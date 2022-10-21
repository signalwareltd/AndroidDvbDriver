/*
 * This is an Android user space port of DVB-T Linux kernel modules.
 *
 * Copyright (C) 2022 by Signalware Ltd <driver at aerialtv.eu>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package info.martinmarinov.dvbservice;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;

import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.DeliverySystem;

/**
 * The client sends a command consisting of a variable number of Longs in the following format:
 *
 * byte 0 will be the Request.ordinal of the request
 * byte 1 will be N the number of longs in the payload
 * byte 2 to 8*N+1 will consist the actual values of the payload
 *
 * After the request has been processed it returns a Response, which is in a similar format.
 * Please refer to the Response documentation for more info
 *
 * Warning: For backwards compatibility order of the enum will be always preserved
 */
enum Request {
    REQ_PROTOCOL_VERSION(
            new Executor() {
                @Override
                public Response execute(DvbDevice dvbDevice, long... payload) throws DvbException {
                    // Clients can use it to determine whether new features
                    // are available.
                    // WARNING: Backward compatibility should always be ensured
                    return Response.success(
                            0L, // parameter 1, version, when adding capabilities, change that number.
                            ALL_REQUESTS.length // parameter 2, can be useful for determining supported commands
                    );
                }
            }
    ),
    REQ_EXIT (new Executor() {
        @Override
        public Response execute(DvbDevice dvbDevice, long ... payload) {
            Log.d(TAG, "Client requested to close the connection");
            return Response.SUCCESS;
        }
    }),
    REQ_TUNE (new Executor() {
        @Override
        public Response execute(DvbDevice dvbDevice, long ... payload) throws DvbException {
            long frequency = payload[0];            // frequency in herz
            long bandwidth = payload[1];            // bandwidth in herz.
                                                    // Typical value for DVB-T is 8_000_000
            DeliverySystem deliverySystem = DeliverySystem.values()[(int) payload[2]];
                                                    // Check enum for actual values

            Log.d(TAG, "Client requested tune to "+frequency+" Hz with bandwidth "+bandwidth+" Hz with delivery system "+deliverySystem);
            dvbDevice.tune(frequency, bandwidth, deliverySystem);
            return Response.SUCCESS;
        }
    }),
    REQ_GET_STATUS (new Executor() {
        @Override
        public Response execute(DvbDevice dvbDevice, long ... ignored) throws DvbException {
            int snr = dvbDevice.readSnr();
            int bitErrorRate = dvbDevice.readBitErrorRate();
            int droppedUsbFps = dvbDevice.readDroppedUsbFps();
            int rfStrengthPercentage = dvbDevice.readRfStrengthPercentage();
            Set<DvbStatus> status = dvbDevice.getStatus();
            boolean hasSignal = status.contains(DvbStatus.FE_HAS_SIGNAL);
            boolean hasCarrier = status.contains(DvbStatus.FE_HAS_CARRIER);
            boolean hasSync = status.contains(DvbStatus.FE_HAS_SYNC);
            boolean hasLock = status.contains(DvbStatus.FE_HAS_LOCK);

            return Response.success(
                    (long) snr, // parameter 1
                    (long) bitErrorRate, // parameter 2
                    (long) droppedUsbFps, // parameter 3
                    (long) rfStrengthPercentage, // parameter 4
                    hasSignal ? 1L : 0L, // parameter 5
                    hasCarrier ? 1L : 0L, // parameter 6
                    hasSync ? 1L : 0L, // parameter 7
                    hasLock ? 1L : 0L // parameter 8
            );
        }
    }),
    REQ_SET_PIDS(new Executor() {
        @Override
        public Response execute(DvbDevice dvbDevice, long ... payload) throws DvbException {
            int[] pids = new int[payload.length];
            for (int i = 0; i < payload.length; i++) pids[i] = (int) payload[i];
            dvbDevice.setPidFilter(pids);
            return Response.SUCCESS;
        }
    }),
    REQ_GET_CAPABILITIES(new Executor() {
        @Override
        public Response execute(DvbDevice dvbDevice, long... payload) throws DvbException {
            DvbCapabilities frontendProperties = dvbDevice.readCapabilities();

            // Only up to 62 deliverySystems are supported under current encoding method
            if (frontendProperties.getSupportedDeliverySystems().size() > 62) throw new IllegalStateException();
            long supportedDeliverySystems = 0;
            for (DeliverySystem deliverySystem : frontendProperties.getSupportedDeliverySystems()) {
                supportedDeliverySystems |= 1 << deliverySystem.ordinal();
            }

            return Response.success(
                    supportedDeliverySystems, // parameter 1
                    frontendProperties.getFrequencyMin(), // parameter 2
                    frontendProperties.getFrequencyMax(), // parameter 3
                    frontendProperties.getFrequencyStepSize(), // parameter 4
                    (long) dvbDevice.getDeviceFilter().getVendorId(), // parameter 5
                    (long) dvbDevice.getDeviceFilter().getProductId() // parameter 6
            );
        }
    });

    private final static String TAG = Request.class.getSimpleName();
    private final static Request[] ALL_REQUESTS = values();

    private final Executor executor;

    Request(Executor executor) {
        this.executor = executor;
    }

    private Response execute(DvbDevice dvbDevice, long ... payload) {
        try {
            return executor.execute(dvbDevice, payload);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.ERROR;
        }
    }

    private static long[] parsePayload(DataInputStream inputStream) throws IOException {
        int size = inputStream.read();
        if (size == -1) throw new IOException("Failed to parse size of command due to possibly EOF");

        long[] payload = new long[size];
        for (int i = 0; i < size; i++) payload[i] = inputStream.readLong();
        return payload;
    }

    public static Request parseAndExecute(DataInputStream inputStream, DataOutputStream dataOutputStream, DvbDevice dvbDevice) throws IOException {
        Request req = null;

        int ordinal = inputStream.read();
        if (ordinal == -1) throw new IOException("Failed to parse command due to possibly EOF");
        long[] payload = parsePayload(inputStream);

        if (ordinal < ALL_REQUESTS.length) {
            req = ALL_REQUESTS[ordinal];

            // This command is recognized, execute and get response
            Response r = req.execute(dvbDevice, payload);

            // Send response back over the wire
            r.serialize(req, dataOutputStream);
        }

        return req;
    }

    private interface Executor {
        Response execute(DvbDevice dvbDevice, long ... payload) throws DvbException;
    }
}
