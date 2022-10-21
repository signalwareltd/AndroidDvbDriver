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

class IsoRequest {
    private final long urbPtr;
    private final int fd;

    IsoRequest(UsbDeviceConnection usbDeviceConnection, UsbEndpoint usbEndpoint, int id, int maxPackets, int packetSize) {
        this.fd = usbDeviceConnection.getFileDescriptor();
        urbPtr = jni_allocate_urb(usbEndpoint.getAddress(), id, maxPackets, packetSize);
        jni_reset_urb(urbPtr);
    }

    void reset() {
        jni_reset_urb(urbPtr);
    }

    void submit() throws IOException {
        IoctlUtils.res(jni_submit(urbPtr, fd));
    }

    void cancel() throws IOException {
        IoctlUtils.res(jni_cancel(urbPtr, fd));
    }

    int read(byte[] data) {
        return jni_read(urbPtr, data);
    }

    static int getReadyRequestId(UsbDeviceConnection usbDeviceConnection, boolean wait) {
        return jni_get_ready_packet_id(usbDeviceConnection.getFileDescriptor(), wait);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        jni_free_urb(urbPtr);
    }

    private static native long jni_allocate_urb(int endpointAddr, int id, int maxPackets, int packetSize);
    private static native void jni_reset_urb(long ptr);
    private static native void jni_free_urb(long ptr);
    private static native int jni_submit(long ptr, int fd);
    private static native int jni_cancel(long ptr, int fd);
    private static native int jni_read(long ptr, byte[] data);
    private static native int jni_get_ready_packet_id(int fd, boolean wait);
}
