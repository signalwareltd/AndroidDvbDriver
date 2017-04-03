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

package info.martinmarinov.drivers.usb.rtl28xx;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.usb.DvbUsbDevice;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.DvbDemux;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.usbxfer.AlternateUsbInterface;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_DEMOD_WR;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_I2C_DA_RD;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_I2C_DA_WR;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_I2C_RD;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_I2C_WR;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_IR_RD;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_IR_WR;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_SYS_RD;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_SYS_WR;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_USB_RD;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_USB_WR;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.CMD_WR_FLAG;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.SYS_GPIO_OUT_VAL;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.USB_EPA_MAXPKT;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.USB_SYSCTL_0;

abstract class Rtl28xxDvbDevice extends DvbUsbDevice {
    private final Object usbLock = new Object();

    private final UsbInterface iface;
    private final UsbEndpoint endpoint;

    final Rtl28xxI2cAdapter i2CAdapter = new Rtl28xxI2cAdapter();
    final TunerCallbackBuilder tunerCallbackBuilder = new TunerCallbackBuilder();

    Rtl28xxDvbDevice(UsbDevice usbDevice, Context context, DeviceFilter deviceFilter) throws DvbException {
        super(usbDevice, context, deviceFilter, DvbDemux.DvbDmxSwfilter());
        iface = usbDevice.getInterface(0);
        endpoint = iface.getEndpoint(0);

        if (endpoint.getAddress() != 0x81) throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_usb_endpoint));
    }

    int ctrlMsg(int value, int index, byte[] data) throws DvbException {
        int requestType;

        if ((index & CMD_WR_FLAG) != 0) {
            // write
            requestType = UsbConstants.USB_TYPE_VENDOR;
        } else {
            // read
            requestType = UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_IN;
        }

        synchronized (usbLock) {
            int result = usbDeviceConnection.controlTransfer(requestType, 0, value, index, data, data.length, 5_000);

            if (result < 0) {
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_send_control_message, result));
            }

            return result;
        }
    }

    void wrReg(int reg, byte[] val) throws DvbException {
        int index;

        if (reg < 0x3000) {
            index = CMD_USB_WR;
        } else if (reg < 0x4000) {
            index = CMD_SYS_WR;
        } else {
            index = CMD_IR_WR;
        }

        int written = ctrlMsg(reg, index, val);
        if (written != val.length) throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.failed_to_write_to_register));
    }

    void wrReg(int reg, int onebyte) throws DvbException {
        byte[] data = new byte[] { (byte) onebyte };
        wrReg(reg, data);
    }

    void wrReg(int reg, int val, int mask) throws DvbException {
        if (mask != 0xff) {
            int tmp = rdReg(reg);

            val &= mask;
            tmp &= ~mask;
            val |= tmp;
        }

        wrReg(reg, val);
    }

    private void rdReg(int reg, byte[] val) throws DvbException {
        int index;

        if (reg < 0x3000) {
            index = CMD_USB_RD;
        } else if (reg < 0x4000) {
            index = CMD_SYS_RD;
        } else {
            index = CMD_IR_RD;
        }

        int read = ctrlMsg(reg, index, val);
        if (read != val.length) throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.failed_to_read_from_register));
    }

    int rdReg(int reg) throws DvbException {
        byte[] result = new byte[1];
        rdReg(reg, result);
        return result[0] & 0xFF;
    }

    @Override
    protected void init() throws DvbException {
        /* init USB endpoints */
        int val = rdReg(USB_SYSCTL_0);

        /* enable DMA and Full Packet Mode*/
        val |= 0x09;
        wrReg(USB_SYSCTL_0, val);

        /* set EPA maximum packet size to 0x0200 */
        wrReg(USB_EPA_MAXPKT, new byte[] { 0x00, 0x02, 0x00, 0x00 });

        /* change EPA FIFO length */
        wrReg(USB_EPA_MAXPKT, new byte[] { 0x14, 0x00, 0x00, 0x00 });
    }

    class Rtl28xxI2cAdapter extends I2cAdapter {
        private final Object lock = new Object();

        int page = -1;

        @Override
        protected int masterXfer(I2cMessage[] msg) throws DvbException {
            /*
	         * It is not known which are real I2C bus xfer limits, but testing
	         * with RTL2831U + MT2060 gives max RD 24 and max WR 22 bytes.
	         */

	        /*
	         * I2C adapter logic looks rather complicated due to fact it handles
	         * three different access methods. Those methods are;
	         * 1) integrated demod access
	         * 2) old I2C access
	         * 3) new I2C access
	         *
	         * Used method is selected in order 1, 2, 3. Method 3 can handle all
	         * requests but there is two reasons why not use it always;
	         * 1) It is most expensive, usually two USB messages are needed
	         * 2) At least RTL2831U does not support it
	         *
	         * Method 3 is needed in case of I2C write+read (typical register read)
	         * where write is more than one byte.
	         */

            synchronized (lock) {
                if (msg.length == 2 && (msg[0].flags & I2cMessage.I2C_M_RD) == 0 &&
                        (msg[1].flags & I2cMessage.I2C_M_RD) != 0) {

                    if (msg[0].len > 24 || msg[1].len > 24) {
			            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
                    } else if (msg[0].addr == 0x10) {
			            /* method 1 - integrated demod */
                        ctrlMsg(((msg[0].buf[0] & 0xFF) << 8) | (msg[0].addr << 1),
                                page,
                                msg[1].buf);
                    } else if (msg[0].len < 2) {
                        /* method 2 - old I2C */
                        ctrlMsg(((msg[0].buf[0] & 0xFF) << 8) | (msg[0].addr << 1),
                                CMD_I2C_RD,
                                msg[1].buf);
                    } else {
                        /* method 3 - new I2C */
                        ctrlMsg(msg[0].addr << 1, CMD_I2C_DA_WR, msg[0].buf);
                        ctrlMsg(msg[0].addr << 1, CMD_I2C_DA_RD, msg[1].buf);
                    }

                } else if (msg.length == 1 && (msg[0].flags & I2cMessage.I2C_M_RD) == 0) {
                    if (msg[0].len > 22) {
                        throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
                    } else if (msg[0].addr == 0x10) {
			            /* method 1 - integrated demod */
                        if (msg[0].buf[0] == 0x00) {
				            /* save demod page for later demod access */
                            page = msg[0].buf[1] & 0xFF;
                        } else {
                            byte[] newdata = new byte[msg[0].len-1];
                            System.arraycopy(msg[0].buf, 1, newdata, 0, newdata.length);

                            ctrlMsg(((msg[0].buf[0] & 0xFF) << 8) | (msg[0].addr << 1),
                                    CMD_DEMOD_WR | page,
                                    newdata);
                        }
                    } else if (msg[0].len < 23) {
                        /* method 2 - old I2C */
                        byte[] newdata = new byte[msg[0].len-1];
                        System.arraycopy(msg[0].buf, 1, newdata, 0, newdata.length);

                        ctrlMsg(((msg[0].buf[0] & 0xFF) << 8) | (msg[0].addr << 1),
                                CMD_I2C_WR,
                                newdata);
                    } else {
                        /* method 3 - new I2C */
                        ctrlMsg(msg[0].addr << 1, CMD_I2C_DA_WR, msg[0].buf);
                    }
                } else {
                    throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
                }
            }

            return msg.length;
        }
    }

    @Override
    protected UsbEndpoint getUsbEndpoint() {
        return endpoint;
    }

    @Override
    protected AlternateUsbInterface getUsbInterface() {
        return AlternateUsbInterface.forUsbInterface(usbDeviceConnection, iface).get(0);
    }

    @SuppressWarnings("WeakerAccess") // This is a false warning
    class TunerCallbackBuilder {
        TunerCallback forTuner(final Rtl28xxTunerType tuner) {
            return new TunerCallback() {
                @Override
                public void onFeVhfEnable(boolean enable) throws DvbException {
                    if (tuner == Rtl28xxTunerType.RTL2832_FC0012) {
                    /* set output values */
                        int val = rdReg(SYS_GPIO_OUT_VAL);

                        if (enable) {
                            val &= 0xbf; /* set GPIO6 low */
                        } else {
                            val |= 0x40; /* set GPIO6 high */
                        }

                        wrReg(SYS_GPIO_OUT_VAL, val);
                    } else {
                        throw new DvbException(BAD_API_USAGE, "Unexpected tuner asks callback");
                    }
                }
            };
        }
    }

    interface TunerCallback {
        void onFeVhfEnable(boolean enable) throws DvbException;
    }
}
