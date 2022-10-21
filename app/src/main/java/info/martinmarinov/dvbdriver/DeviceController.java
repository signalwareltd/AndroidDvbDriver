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

package info.martinmarinov.dvbdriver;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.usb.DvbUsbDeviceRegistry;

import static info.martinmarinov.drivers.DvbException.ErrorCode.NO_DVB_DEVICES_FOUND;

class DeviceController extends Thread {
    private final DvbFrontendActivity dvbFrontendActivity;
    private volatile long desiredFreq, desiredBand;
    private volatile long currFreq, currBand;
    private volatile DeliverySystem currDelSystem, desiredDelSystem;

    private DataHandler dataHandler;

    DeviceController(DvbFrontendActivity dvbFrontendActivity, long desiredFreq, long desiredBand, DeliverySystem deliverySystem) {
        this.dvbFrontendActivity = dvbFrontendActivity;
        tuneTo(desiredFreq, desiredBand, deliverySystem);
    }

    void tuneTo(long desiredFreq, long desiredBand, DeliverySystem deliverySystem) {
        // Avoid accessing the DvbDevice from multiple threads
        this.desiredFreq = desiredFreq;
        this.desiredBand = desiredBand;
        this.desiredDelSystem = deliverySystem;
    }

    DataHandler getDataHandler() {
        return dataHandler;
    }

    @Override
    public void run() {
        try {
            List<DvbDevice> availableFrontends = DvbUsbDeviceRegistry.getUsbDvbDevices(dvbFrontendActivity.getApplicationContext());
            if (availableFrontends.isEmpty())
                throw new DvbException(NO_DVB_DEVICES_FOUND, dvbFrontendActivity.getString(R.string.no_devices_found));

            DvbDevice dvbDevice = availableFrontends.get(0);
            dvbDevice.open();
            dvbFrontendActivity.announceOpen(true, dvbDevice.getDebugString());

            dataHandler = new DataHandler(dvbFrontendActivity, dvbDevice.getTransportStream(new DvbDevice.StreamCallback() {
                @Override
                public void onStreamException(IOException e) {
                    dvbFrontendActivity.handleException(e);
                }

                @Override
                public void onStoppedStreaming() {
                    interrupt();
                }
            }));
            dataHandler.start();

            try {
                while (!isInterrupted()) {
                    if (desiredFreq != currFreq || desiredBand != currBand || desiredDelSystem != currDelSystem) {
                        dvbDevice.tune(desiredFreq, desiredBand, desiredDelSystem);
                        dvbDevice.disablePidFilter();

                        currFreq = desiredFreq;
                        currBand = desiredBand;
                        currDelSystem = desiredDelSystem;

                        dataHandler.setFreqAndBandwidth(currFreq, currBand);
                        dataHandler.reset();
                    }

                    int snr = dvbDevice.readSnr();
                    int qualityPercentage = Math.round(100.0f * (0xFFFF - dvbDevice.readBitErrorRate()) / (float) 0xFFFF);
                    int droppedUsbFps = dvbDevice.readDroppedUsbFps();
                    int rfStrength = dvbDevice.readRfStrengthPercentage();
                    Set<DvbStatus> status = dvbDevice.getStatus();
                    boolean hasSignal = status.contains(DvbStatus.FE_HAS_SIGNAL);
                    boolean hasCarrier = status.contains(DvbStatus.FE_HAS_CARRIER);
                    boolean hasSync = status.contains(DvbStatus.FE_HAS_SYNC);
                    boolean hasLock = status.contains(DvbStatus.FE_HAS_LOCK);
                    dvbFrontendActivity.announceMeasurements(snr, qualityPercentage, droppedUsbFps, rfStrength, hasSignal, hasCarrier, hasSync, hasLock);

                    Thread.sleep(1_000);
                }
            } catch (InterruptedException ie) {
                // Interrupted exceptions are ok
            } finally {
                dataHandler.interrupt();
                //noinspection ThrowFromFinallyBlock
                dataHandler.join();

                try {
                    dvbDevice.close();
                } catch (IOException e) {
                    dvbFrontendActivity.handleException(e);
                }
            }
        } catch (Exception e) {
            dvbFrontendActivity.handleException(e);
        } finally {
            dvbFrontendActivity.announceOpen(false, null);
        }
    }
}
