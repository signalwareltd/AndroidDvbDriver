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

package info.martinmarinov.usbxfer;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Android USB API drops packets when high speeds are needed.
 *
 * This is why we need this framework to allow us to use the kernel to handle the transfer for us
 *
 * Inspired by http://www.source-code.biz/snippets/java/UsbIso
 *
 * This is not thread safe! Call only from one thread.
 */

public class UsbHiSpeedBulk {
    public final static boolean IS_PLATFORM_SUPPORTED;

    static {
        boolean isPlatformSupported = false;
        try {
            System.loadLibrary("UsbXfer");
            isPlatformSupported = true;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        IS_PLATFORM_SUPPORTED = isPlatformSupported;
    }

    private final int fileDescriptor;
    private final UsbDeviceConnection usbDeviceConnection;
    private final List<IsoRequest> requests;
    private final int nrequests, packetsPerRequests, packetSize;
    private final UsbEndpoint usbEndpoint;
    private final Buffer buffer;

    public UsbHiSpeedBulk(UsbDeviceConnection usbDeviceConnection, UsbEndpoint usbEndpoint, int nrequests, int packetsPerRequests) {
        this.usbDeviceConnection = usbDeviceConnection;
        this.fileDescriptor = usbDeviceConnection.getFileDescriptor();
        this.nrequests = nrequests;
        this.requests = new ArrayList<>(nrequests);
        this.packetSize = usbEndpoint.getMaxPacketSize();
        this.usbEndpoint = usbEndpoint;
        this.packetsPerRequests = packetsPerRequests;
        this.buffer = new Buffer(packetsPerRequests * packetSize);
    }

    // API

    public void setInterface(AlternateUsbInterface usbInterface) throws IOException {
        IoctlUtils.res(jni_setInterface(fileDescriptor, usbInterface.getUsbInterface().getId(), usbInterface.getAlternateSettings()));
    }

    public void unsetInterface(AlternateUsbInterface usbInterface) throws IOException {
        // According to original UsbHiSpeedBulk alternatesetting of 0 stops the streaming
        IoctlUtils.res(jni_setInterface(fileDescriptor, usbInterface.getUsbInterface().getId(), 0));
    }

    public void start() throws IOException {
        for (int i = 0; i < nrequests; i++) {
            IsoRequest req = new IsoRequest(usbDeviceConnection, usbEndpoint, i, packetsPerRequests, packetSize);
            try {
                req.submit();
                requests.add(req);
            } catch (IOException e) {
                // No more memory for allocating packets
                e.printStackTrace();
                break;
            }
        }

        if (requests.isEmpty()) throw new IOException("Cannot initialize any USB requests");
    }

    /**
     * Buffer is not immutable! Next time you call #read, its value will be invalid and overwriten.
     * The buffer is reused for efficiency.
     * @param wait whther to block until data is available
     * @return a buffer or null if nothing is available
     * @throws IOException
     */
    public Buffer read(boolean wait) throws IOException {
        IsoRequest req = getReadyRequest(wait);
        if (req == null) return null;

        buffer.length = req.read(buffer.data);
        req.reset();
        req.submit();

        return buffer;
    }

    public void stop() throws IOException {
        for (IsoRequest r : requests) {
            r.cancel();
        }
        requests.clear();
    }

    public class Buffer {
        private final byte[] data;
        private int length;

        private Buffer(int bufferSize) {
            this.data = new byte[bufferSize];
        }

        public byte[] getData() {
            return data;
        }

        public int getLength() {
            return length;
        }
    }

    // helpers

    private IsoRequest getReadyRequest(boolean wait) throws IOException {
        int readyRequestId = IsoRequest.getReadyRequestId(usbDeviceConnection, wait);
        if (readyRequestId < 0) return null;
        return requests.get(readyRequestId);
    }

    // native

    private static native int jni_setInterface(int fd, int interfaceId, int alternateSettings);
}
