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

public class UsbBulkSource implements ByteSource {
    private final static int INITIAL_DELAY_BEFORE_BACKOFF = 1_000;
    private final static int MAX_BACKOFF = 10;

    private final UsbDeviceConnection usbDeviceConnection;
    private final UsbEndpoint usbEndpoint;
    private final AlternateUsbInterface usbInterface;
    private final int numRequests;
    private final int numPacketsPerReq;

    private UsbHiSpeedBulk usbHiSpeedBulk;
    private int backoff = -INITIAL_DELAY_BEFORE_BACKOFF;

    public UsbBulkSource(UsbDeviceConnection usbDeviceConnection, UsbEndpoint usbEndpoint, AlternateUsbInterface usbInterface, int numRequests, int numPacketsPerReq) {
        this.usbDeviceConnection = usbDeviceConnection;
        this.usbEndpoint = usbEndpoint;
        this.usbInterface = usbInterface;
        this.numRequests = numRequests;
        this.numPacketsPerReq = numPacketsPerReq;
    }

    @Override
    public void open() throws IOException {
        usbHiSpeedBulk = new UsbHiSpeedBulk(usbDeviceConnection, usbEndpoint, numRequests, numPacketsPerReq);

        usbHiSpeedBulk.setInterface(usbInterface);
        usbDeviceConnection.claimInterface(usbInterface.getUsbInterface(), true);
        usbHiSpeedBulk.start();
    }

    @Override
    public void readNext(ByteSink sink) throws IOException, InterruptedException {
        UsbHiSpeedBulk.Buffer read = usbHiSpeedBulk.read(false);
        if (read == null) {
            backoff++;
            if (backoff > 0) {
                Thread.sleep(backoff);
            } else {
                if (backoff % 3 == 0) Thread.sleep(1);
            }
            if (backoff > MAX_BACKOFF) {
                backoff = MAX_BACKOFF;
            }
        } else {
            backoff = -INITIAL_DELAY_BEFORE_BACKOFF;
            sink.consume(read.getData(), read.getLength());
        }
    }

    @Override
    public void close() throws IOException {
        usbHiSpeedBulk.stop();
        usbHiSpeedBulk.unsetInterface(usbInterface);
        usbDeviceConnection.releaseInterface(usbInterface.getUsbInterface());
    }
}
