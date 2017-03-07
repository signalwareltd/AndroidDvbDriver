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
import android.support.annotation.NonNull;

import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.Check;
import info.martinmarinov.drivers.tools.I2cAdapter.I2GateControl;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxDvbDevice.Rtl28xxI2cAdapter;

import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.usb.rtl28xx.R820tTuner.RafaelChip.CHIP_R820T;
import static info.martinmarinov.drivers.usb.rtl28xx.R820tTuner.RafaelChip.CHIP_R828D;

enum Rtl28xxTunerType {
    RTL2832_E4000(
            new IsPresent() {
                @Override
                public boolean isPresent(Rtl28xxDvbDevice device) throws DvbException {
                    byte[] data = new byte[1];
                    device.ctrlMsg(0x02c8, Rtl28xxConst.CMD_I2C_RD, data);
                    return (data[0] & 0xff) == 0x40;
                }
            },
            new SlaveParser() {
                @NonNull
                @Override
                public Rtl28xxSlaveType getSlave(Resources resources, Rtl28xxDvbDevice device) {
                    return Rtl28xxSlaveType.SLAVE_DEMOD_NONE;
                }
            }, new DvbTunerCreator() {
                @NonNull
                @Override
                public DvbTuner create(Rtl28xxI2cAdapter adapter, I2GateControl i2GateControl, Resources resources) throws DvbException {
                    // The tuner uses sames XTAL as the frontend at 28.8 MHz
                    return new E4000Tuner(0x64, adapter, 28_800_000L, i2GateControl, resources);
                }
            }
    ),
    RTL2832_FC0013(
            new IsPresent() {
                @Override
                public boolean isPresent(Rtl28xxDvbDevice device) throws DvbException {
                    byte[] data = new byte[1];
                    device.ctrlMsg(0x00c6, Rtl28xxConst.CMD_I2C_RD, data);
                    return (data[0] & 0xff) == 0xa1;
                }
            },
            new SlaveParser() {
                @NonNull
                @Override
                public Rtl28xxSlaveType getSlave(Resources resources, Rtl28xxDvbDevice device) {
                    return Rtl28xxSlaveType.SLAVE_DEMOD_NONE;
                }
            }, new DvbTunerCreator() {
        @NonNull
        @Override
        public DvbTuner create(Rtl28xxI2cAdapter adapter, I2GateControl i2GateControl, Resources resources) throws DvbException {
            // The tuner uses sames XTAL as the frontend at 28.8 MHz
            return new FC00013Tuner(0xc6>>1, adapter, 28_800_000L, i2GateControl);
        }
    }
    ),
    RTL2832_R820T(
            new IsPresent() {
                @Override
                public boolean isPresent(Rtl28xxDvbDevice device) throws DvbException {
                    byte[] data = new byte[1];
                    device.ctrlMsg(0x0034, Rtl28xxConst.CMD_I2C_RD, data);
                    return (data[0] & 0xff) == 0x69;
                }
            },
            new SlaveParser() {
                @NonNull
                @Override
                public Rtl28xxSlaveType getSlave(Resources resources, Rtl28xxDvbDevice device) {
                    return Rtl28xxSlaveType.SLAVE_DEMOD_NONE;
                }
            }, new DvbTunerCreator() {
                @NonNull
                @Override
                public DvbTuner create(Rtl28xxI2cAdapter adapter, I2GateControl i2GateControl, Resources resources) throws DvbException {
                    // The tuner uses sames XTAL as the frontend at 28.8 MHz
                    return new R820tTuner(0x1a, adapter, CHIP_R820T, 28_800_000L, i2GateControl, resources);
                }
            }
    ),
    RTL2832_R828D(
            new IsPresent() {
                @Override
                public boolean isPresent(Rtl28xxDvbDevice device) throws DvbException {
                    byte[] data = new byte[1];
                    device.ctrlMsg(0x0074, Rtl28xxConst.CMD_I2C_RD, data);
                    return (data[0] & 0xff) == 0x69;
                }
            },
            new SlaveParser() {
                @NonNull
                @Override
                public Rtl28xxSlaveType getSlave(Resources resources, Rtl28xxDvbDevice device) throws DvbException {
                    /* power on MN88472 demod on GPIO0 */
                    device.wrReg(Rtl28xxConst.SYS_GPIO_OUT_VAL, 0x01, 0x01);
                    device.wrReg(Rtl28xxConst.SYS_GPIO_DIR, 0x00, 0x01);
                    device.wrReg(Rtl28xxConst.SYS_GPIO_OUT_EN, 0x01, 0x01);

                    /* check MN88472 answers */
                    byte[] data = new byte[1];

                    device.ctrlMsg(0xff38, Rtl28xxConst.CMD_I2C_RD, data);
                    switch (data[0]) {
                        case 0x02:
                            return Rtl28xxSlaveType.SLAVE_DEMOD_MN88472;
                        case 0x03:
                            return Rtl28xxSlaveType.SLAVE_DEMOD_MN88473;
                        default:
                            throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner));
                    }
                }
            }, new DvbTunerCreator() {
                @NonNull
                @Override
                public DvbTuner create(Rtl28xxI2cAdapter adapter, I2GateControl i2GateControl, Resources resources) throws DvbException {
                    // Actual tuner xtal and frontend crystals are different
                    return new R820tTuner(0x3a, adapter, CHIP_R828D, 16_000_000L, i2GateControl, resources);
                }
            }
    );

    private final IsPresent isPresent;
    private final SlaveParser slaveParser;
    private final DvbTunerCreator creator;

    Rtl28xxTunerType(IsPresent isPresent, SlaveParser slaveParser, DvbTunerCreator creator) {
        this.isPresent = isPresent;
        this.slaveParser = slaveParser;
        this.creator = creator;
    }

    public static @NonNull Rtl28xxTunerType detectTuner(Resources resources, Rtl28xxDvbDevice device) throws DvbException {
        for (Rtl28xxTunerType tuner : values()) {
            try {
                if (tuner.isPresent.isPresent(device)) return tuner;
            } catch (DvbException ignored) {
                // Do nothing, if it is not the correct tuner, the control message
                // will throw an exception and that's ok
            }
        }
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unrecognized_tuner_on_device));
    }

    public @NonNull Rtl28xxSlaveType detectSlave(Resources resources, Rtl28xxDvbDevice device) throws DvbException {
        return slaveParser.getSlave(resources, device);
    }

    public @NonNull DvbTuner createTuner(Rtl28xxI2cAdapter adapter, I2GateControl i2GateControl, Resources resources) throws DvbException {
        return creator.create(adapter, Check.notNull(i2GateControl), resources);
    }

    private interface IsPresent {
        boolean isPresent(Rtl28xxDvbDevice device) throws DvbException;
    }

    private interface DvbTunerCreator {
        @NonNull DvbTuner create(Rtl28xxI2cAdapter adapter, I2GateControl i2GateControl, Resources resources) throws DvbException;
    }

    private interface SlaveParser {
        @NonNull Rtl28xxSlaveType getSlave(Resources resources, Rtl28xxDvbDevice device) throws DvbException;
    }
}
