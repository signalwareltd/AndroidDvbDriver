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
    protected synchronized void powerControl(boolean turnOn) throws DvbException {
        Log.d(TAG, "Turning "+(turnOn ? "on" : "off"));

        if (turnOn) {
		    /* GPIO3=1, GPIO4=0 */
            wrReg(SYS_GPIO_OUT_VAL, 0x08, 0x18);
		    /* suspend? */
            wrReg(SYS_DEMOD_CTL1, 0x00, 0x10);
		    /* enable PLL */
            wrReg(SYS_DEMOD_CTL, 0x80, 0x80);
		    /* disable reset */
            wrReg(SYS_DEMOD_CTL, 0x20, 0x20);
		    /* streaming EP: clear stall & reset */
		    wrReg(USB_EPA_CTL, new byte[] {(byte) 0x00, (byte) 0x00});
            /* enable ADC */
            wrReg(SYS_DEMOD_CTL, 0x48, 0x48);
        } else {
		    /* GPIO4=1 */
            wrReg(SYS_GPIO_OUT_VAL, 0x10, 0x10);
		    /* disable PLL */
            wrReg(SYS_DEMOD_CTL, 0x00, 0x80);
		    /* streaming EP: set stall & reset */
            wrReg(USB_EPA_CTL, new byte[] {(byte) 0x10, (byte) 0x02});
            /* disable ADC */
            wrReg(SYS_DEMOD_CTL, 0x00, 0x48);
        }
    }

    @Override
    protected synchronized void readConfig() throws DvbException {
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
    protected synchronized DvbFrontend frontendAttatch() throws DvbException {
        notNull(tuner, "Initialize tuner first!");
        return slave.createFrontend(this, tuner, i2CAdapter, resources);
    }

    @Override
    protected synchronized DvbTuner tunerAttatch() throws DvbException {
        notNull(tuner, "Initialize tuner first!");
        notNull(frontend, "Initialize frontend first!");
        return tuner.createTuner(i2CAdapter, i2GateController, resources, tunerCallbackBuilder.forTuner(tuner));
    }

    @Override
    public String getDebugString() {
        StringBuilder sb = new StringBuilder("RTL2832 ");

        if (tuner != null) sb.append(tuner.name()).append(' ');
        if (slave != null) sb.append(slave.name()).append(' ');

        return sb.toString();
    }

    private final I2GateControl i2GateController = new I2GateControl() {
        private boolean i2cGateState = false;

        @Override
        protected synchronized void i2cGateCtrl(boolean enable) throws DvbException {
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
