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
import android.hardware.usb.UsbDevice;
import android.util.Log;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.tools.I2cAdapter.I2GateControl;
import info.martinmarinov.drivers.tools.SleepUtils;
import info.martinmarinov.drivers.tools.ThrowingRunnable;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;

import static info.martinmarinov.drivers.tools.Check.notNull;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.SYS_DEMOD_CTL;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.SYS_DEMOD_CTL1;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.SYS_GPIO_OUT_VAL;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.USB_EPA_CTL;

class Rtl2832DvbDevice extends Rtl28xxDvbDevice {
    private final static String TAG = Rtl2832DvbDevice.class.getSimpleName();
    private Rtl28xxTunerType tuner;
    private Rtl28xxSlaveType slave;

    Rtl2832DvbDevice(UsbDevice usbDevice, Context context, DeviceFilter deviceFilter) throws DvbException {
        super(usbDevice, context, deviceFilter);
    }

    @Override
    protected void powerControl(boolean turnOn) throws DvbException {
        Log.d(TAG, "Turning "+(turnOn ? "on" : "off"));

        if (turnOn) {
            /* set output values */
            int val = rdReg(SYS_GPIO_OUT_VAL);
            val |= 0x08;
            val &= 0xef;
            wrReg(SYS_GPIO_OUT_VAL, val);

            /* demod_ctl_1 */
            val = rdReg(SYS_DEMOD_CTL1);
            val &= 0xef;
            wrReg(SYS_DEMOD_CTL1, val);

            /* demod control */
		    /* PLL enable */
            val = rdReg(SYS_DEMOD_CTL);
            val |= 0x80; // bit 7 to 1
            wrReg(SYS_DEMOD_CTL, val);

            val = rdReg(SYS_DEMOD_CTL);
            val |= 0x20;
            wrReg(SYS_DEMOD_CTL, val);

            SleepUtils.mdelay(5L);

            /* enable ADC_Q and ADC_I */
            rdReg(SYS_DEMOD_CTL);
            val |= 0x48;
            wrReg(SYS_DEMOD_CTL, val);

            /* streaming EP: clear stall & reset */
            wrReg(USB_EPA_CTL, new byte[] {0, 0});
        } else {
		    /* demod_ctl_1 */
            int val = rdReg(SYS_DEMOD_CTL1);
            val |= 0x0c;
            wrReg(SYS_DEMOD_CTL1, val);

		    /* set output values */
            val = rdReg(SYS_GPIO_OUT_VAL);
            val |= 0x10;
            wrReg(SYS_GPIO_OUT_VAL, val);

		    /* demod control */
            val = rdReg(SYS_DEMOD_CTL);
            val &= 0x37;
            wrReg(SYS_DEMOD_CTL, val);

		    /* streaming EP: set stall & reset */
            wrReg(USB_EPA_CTL, new byte[] {0x10, 0x02});
        }
    }

    @Override
    protected void readConfig() throws DvbException {
        /* enable GPIO3 and GPIO6 as output */
        wrReg(Rtl28xxConst.SYS_GPIO_DIR, 0x00, 0x40);
        wrReg(Rtl28xxConst.SYS_GPIO_OUT_EN, 0x48, 0x48);

        /*
	    * Probe used tuner. We need to know used tuner before demod attach
	    * since there is some demod params needed to set according to tuner.
	    */

	    /* open demod I2C gate */
	    i2GateController.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                tuner = Rtl28xxTunerType.detectTuner(resources, Rtl2832DvbDevice.this);
                slave = tuner.detectSlave(resources, Rtl2832DvbDevice.this);
            }
        });
        Log.d(TAG, "Detected tuner " + tuner + " with slave demod "+slave);
    }

    @Override
    protected DvbFrontend frontendAttatch() throws DvbException {
        notNull(tuner, "Initialize tuner first!");
        return slave.createFrontend(this, tuner, i2CAdapter, resources);
    }

    @Override
    protected DvbTuner tunerAttatch() throws DvbException {
        notNull(tuner, "Initialize tuner first!");
        notNull(frontend, "Initialize frontend first!");
        return tuner.createTuner(i2CAdapter, i2GateController, resources, tunerCallbackBuilder.forTuner(tuner));
    }

    private final I2GateControl i2GateController = new I2GateControl() {
        private boolean i2cGateState = false;

        @Override
        protected void i2cGateCtrl(boolean enable) throws DvbException {
            if (i2cGateState == enable) return;

            if (enable) {
                ctrlMsg(0x0120, 0x0011, new byte[] {(byte) 0x18});
            } else {
                ctrlMsg(0x0120, 0x0011, new byte[] {(byte) 0x10});
            }

            i2cGateState = enable;
        }
    };
}
