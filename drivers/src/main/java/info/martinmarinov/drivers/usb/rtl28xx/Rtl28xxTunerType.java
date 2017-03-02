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

import android.content.res.Resources;

import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.usb.DvbFrontend.I2GateControl;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxDvbDevice.Rtl28xxI2cAdapter;
import info.martinmarinov.drivers.tools.Check;

import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.usb.rtl28xx.R820tTuner.RafaelChip.CHIP_R820T;

enum Rtl28xxTunerType {
    RTL2832_E4000(0x02c8, 0x10, 28_800_000L,
            new ExpectedPayload(1) {
                @Override
                public boolean matches(byte[] data) {
                    return (data[0] & 0xff) == 0x40;
                }
            },
            new DvbTunerCreator() {
                @Override
                public DvbTuner create(Rtl28xxI2cAdapter adapter, I2GateControl i2GateControl, Resources resources) throws DvbException {
                    return new E4000Tuner(0x64, adapter, RTL2832_E4000.xtal, i2GateControl, resources);
                }
            }
    ),
    RTL2832_R820T(0x0034, 0x10, 28_800_000L,
            new ExpectedPayload(1) {
                @Override
                public boolean matches(byte[] data) {
                    return (data[0] & 0xff) == 0x69;
                }
            },
            new DvbTunerCreator() {
                @Override
                public DvbTuner create(Rtl28xxI2cAdapter adapter, I2GateControl i2GateControl, Resources resources) throws DvbException {
                    return new R820tTuner(0x1a, adapter, CHIP_R820T, RTL2832_R820T.xtal, i2GateControl, resources);
                }
            }
    );

    private final int probeVal;
    private final ExpectedPayload expectedPayload;
    public final int i2c_addr;
    public final long xtal;
    private final DvbTunerCreator creator;

    Rtl28xxTunerType(int probeVal, int i2c_addr, long xtal, ExpectedPayload expectedPayload, DvbTunerCreator creator) {
        this.probeVal = probeVal;
        this.expectedPayload = expectedPayload;
        this.xtal = xtal;
        this.i2c_addr = i2c_addr;
        this.creator = creator;
    }

    private boolean isOnDevice(Rtl28xxDvbDevice device) throws DvbException {
        byte[] data = new byte[expectedPayload.length];
        device.ctrlMsg(probeVal, Rtl28xxConst.CMD_I2C_RD, data);
        return expectedPayload.matches(data);
    }

    public static Rtl28xxTunerType detectTuner(Resources resources, Rtl28xxDvbDevice device) throws DvbException {
        for (Rtl28xxTunerType tuner : values()) {
            try {
                if (tuner.isOnDevice(device)) return tuner;
            } catch (DvbException ignored) {
                // Do nothing, if it is not the correct tuner, the control message
                // will throw an exception and that's ok
            }
        }
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unrecognized_tuner_on_device));
    }

    public DvbTuner createTuner(Rtl28xxI2cAdapter adapter, I2GateControl i2GateControl, Resources resources) throws DvbException {
        return creator.create(adapter, Check.notNull(i2GateControl), resources);
    }

    private abstract static class ExpectedPayload {
        private final int length;

        private ExpectedPayload(int length) {
            this.length = length;
        }

        public abstract boolean matches(byte[] data);
    }

    private interface DvbTunerCreator {
        DvbTuner create(Rtl28xxI2cAdapter adapter, I2GateControl i2GateControl, Resources resources) throws DvbException;
    }
}
