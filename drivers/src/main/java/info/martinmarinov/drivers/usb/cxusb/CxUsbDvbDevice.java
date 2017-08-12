/*
 * This is an Android user space port of DVB-T Linux kernel modules.
 *
 * Copyright (C) 2017 Martin Marinov <martintzvetomirov at gmail com>
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

package info.martinmarinov.drivers.usb.cxusb;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDemux;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.usb.DvbUsbDevice;
import info.martinmarinov.usbxfer.AlternateUsbInterface;

import static android.hardware.usb.UsbConstants.USB_DIR_IN;
import static android.hardware.usb.UsbConstants.USB_DIR_OUT;
import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;
import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;
import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_VID_MEDION;

abstract class CxUsbDvbDevice extends DvbUsbDevice {
    private final static String TAG = CxUsbDvbDevice.class.getSimpleName();

    private final static byte CMD_I2C_WRITE = 0x08;
    private final static byte CMD_I2C_READ = 0x09;

    private final static byte CMD_GPIO_WRITE = 0x0e;
    private final static byte GPIO_TUNER = 0x02;

    private final static byte CMD_POWER_OFF = (byte) 0xdc;
    private final static byte CMD_POWER_ON = (byte) 0xde;

    private final static byte CMD_STREAMING_ON = 0x36;
    private final static byte CMD_STREAMING_OFF = 0x37;

    final static byte CMD_DIGITAL = 0x51;

    /* Max transfer size done by I2C transfer functions */
    private final static int MAX_XFER_SIZE = 80;

    private final Object usbLock = new Object();

    private boolean gpio_tuner_write_state = false;

    private final UsbInterface iface;
    private final UsbEndpoint endpoint;
    private final UsbEndpoint controlEndpointIn;
    private final UsbEndpoint controlEndpointOut;

    final I2cAdapter i2CAdapter = new CxUsbDvbDeviceI2cAdapter();

    CxUsbDvbDevice(UsbDevice usbDevice, Context context, DeviceFilter filter) throws DvbException {
        super(usbDevice, context, filter, DvbDemux.DvbDmxSwfilter());

        iface = usbDevice.getInterface(0);

        controlEndpointIn = iface.getEndpoint(0);
        controlEndpointOut = iface.getEndpoint(1);
        endpoint = iface.getEndpoint(2);

        if (controlEndpointIn.getAddress() != 0x81 || controlEndpointIn.getDirection() != USB_DIR_IN) throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_usb_endpoint));
        if (controlEndpointOut.getAddress() != 0x01 || controlEndpointOut.getDirection() != USB_DIR_OUT) throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_usb_endpoint));
        if (endpoint.getAddress() != 0x82 || endpoint.getDirection() != USB_DIR_IN) throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_usb_endpoint));
    }

    @Override
    protected AlternateUsbInterface getUsbInterface() {
        return AlternateUsbInterface.forUsbInterface(usbDeviceConnection, iface).get(0);
    }

    @Override
    protected UsbEndpoint getUsbEndpoint() {
        return endpoint;
    }

    void cxusb_streaming_ctrl(boolean onoff) throws DvbException {
        byte[] buf = new byte[]{ 0x03, 0x00 };
        if (onoff) {
            cxusb_ctrl_msg(CMD_STREAMING_ON, buf, 2);
        } else {
            cxusb_ctrl_msg(CMD_STREAMING_OFF, new byte[0], 0);
        }
    }

    void cxusb_power_ctrl(boolean onoff) throws DvbException {
        if (onoff) {
            cxusb_ctrl_msg(CMD_POWER_ON, new byte[1], 1);
        } else {
            cxusb_ctrl_msg(CMD_POWER_OFF, new byte[1], 1);
        }
    }

    @SuppressWarnings("WeakerAccess")
    void cxusb_ctrl_msg(byte cmd, @NonNull byte[] wbuf, int wlen) throws DvbException {
        cxusb_ctrl_msg(cmd, wbuf, wlen, null, Integer.MIN_VALUE);
    }

    void cxusb_ctrl_msg(byte cmd, @NonNull byte[] wbuf, int wlen, @Nullable byte[] rbuf, int rlen) throws DvbException {
        if (1 + wlen > MAX_XFER_SIZE) {
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
        }

        if (rlen > MAX_XFER_SIZE) {
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
        }

        byte[] data = new byte[wlen + 1];
        data[0] = cmd;
        System.arraycopy(wbuf, 0, data, 1, wlen);

        dvb_usb_generic_rw(data, 1 + wlen, data, rlen);

        if (rbuf != null) {
            System.arraycopy(data, 0, rbuf, 0, rlen);
        }
    }

    private void dvb_usb_generic_rw(@NonNull byte[] wbuf, int wlen, @Nullable byte[] rbuf, int rlen) throws DvbException {
        synchronized (usbLock) {
            int actlen = usbDeviceConnection.bulkTransfer(controlEndpointOut, wbuf, wlen, 5_000);

            if (actlen < wlen) {
                if (actlen >= 0) actlen = -1;
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_send_control_message, actlen));
            }

            // put delay here if needed

            if (rbuf != null) {
                actlen = usbDeviceConnection.bulkTransfer(controlEndpointIn, rbuf, rlen, 5_000);
                if (actlen < rlen) {
                    if (actlen >= 0) actlen = -1;
                    throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_send_control_message, actlen));
                }
            }
        }
    }

    private void cxusb_gpio_tuner(boolean onoff) throws DvbException {
        if (gpio_tuner_write_state == onoff) {
            return;
        }
        byte[] o = new byte[2];
        o[0] = GPIO_TUNER;
        o[1] = onoff ? (byte) 1 : 0;

        byte[] i = new byte[1];
        cxusb_ctrl_msg(CMD_GPIO_WRITE, o, 2, i, 1);

        if (i[0] != 0x01) {
            Log.w(TAG, "gpio_write failed.");
        }
        gpio_tuner_write_state = onoff;
    }

    private class CxUsbDvbDeviceI2cAdapter extends I2cAdapter {

        @Override
        protected int masterXfer(I2cMessage[] msg) throws DvbException {
            for (int i = 0; i < msg.length; i++) {

                if (getDeviceFilter().getVendorId() == USB_VID_MEDION) {
                    switch (msg[i].addr) {
                        case 0x63:
                            cxusb_gpio_tuner(false);
                            break;
                        default:
                            cxusb_gpio_tuner(true);
                            break;
                    }
                }

                if ((msg[i].flags & I2C_M_RD) != 0) {
			        /* read only */
                    byte[] obuf = new byte[3];
                    byte[] ibuf = new byte[MAX_XFER_SIZE];

                    if (1 + msg[i].len > ibuf.length) {
                        throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
                    }
                    obuf[0] = 0;
                    obuf[1] = (byte) msg[i].len;
                    obuf[2] = (byte) msg[i].addr;
                    cxusb_ctrl_msg(CMD_I2C_READ, obuf, 3, ibuf, 1+msg[i].len);

                    System.arraycopy(ibuf, 1, msg[i].buf, 0,  msg[i].len);
                } else if (i+1 < msg.length && ((msg[i+1].flags & I2C_M_RD) != 0) && msg[i].addr == msg[i+1].addr) {
                    /* write to then read from same address */
                    byte[] obuf = new byte[MAX_XFER_SIZE];
                    byte[] ibuf = new byte[MAX_XFER_SIZE];

                    if (3 + msg[i].len > obuf.length) {
                        throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
                    }
                    if (1 + msg[i + 1].len > ibuf.length) {
                        throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
                    }
                    obuf[0] = (byte) msg[i].len;
                    obuf[1] = (byte) msg[i+1].len;
                    obuf[2] = (byte) msg[i].addr;

                    System.arraycopy(msg[i].buf, 0, obuf, 3, msg[i].len);

                    cxusb_ctrl_msg(CMD_I2C_READ, obuf, 3+msg[i].len, ibuf, 1+msg[i+1].len);

                    if (ibuf[0] != 0x08) {
                        Log.w(TAG, "i2c read may have failed");
                    }

                    System.arraycopy(ibuf, 1, msg[i+1].buf, 0, msg[i+1].len);

                    i++;
                } else {
			        /* write only */
                    byte[] obuf = new byte[MAX_XFER_SIZE];
                    byte[] ibuf = new byte[1];

                    if (2 + msg[i].len > obuf.length) {
                        throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
                    }
                    obuf[0] = (byte) msg[i].addr;
                    obuf[1] = (byte) msg[i].len;
                    System.arraycopy(msg[i].buf, 0, obuf, 2, msg[i].len);

                    cxusb_ctrl_msg(CMD_I2C_WRITE, obuf, 2+msg[i].len, ibuf, 1);
                    if (ibuf[0] != 0x08){
                        Log.w(TAG, "i2c read may have failed");
                    }
                }
            }

            return msg.length;
        }
    }
}
