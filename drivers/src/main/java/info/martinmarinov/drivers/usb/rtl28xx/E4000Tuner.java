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

package info.martinmarinov.drivers.usb.rtl28xx;

import android.content.res.Resources;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.I2cAdapter.I2GateControl;
import info.martinmarinov.drivers.tools.ThrowingRunnable;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.usb.rtl28xx.E4000TunerData.E4000Pll;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;
import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;
import static info.martinmarinov.drivers.usb.rtl28xx.E4000TunerData.E4000_FREQ_BANDS;
import static info.martinmarinov.drivers.usb.rtl28xx.E4000TunerData.E4000_IF_LUT;
import static info.martinmarinov.drivers.usb.rtl28xx.E4000TunerData.E4000_LNA_LUT;
import static info.martinmarinov.drivers.usb.rtl28xx.E4000TunerData.E4000_PLL_LUT;

class E4000Tuner implements DvbTuner {
    private final static int MAX_XFER_SIZE = 64;

    private final int i2cAddress;
    private final Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter;
    private final long xtal;
    private final I2GateControl i2GateControl;
    private final Resources resources;

    E4000Tuner(int i2cAddress, Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter, long xtal, I2GateControl i2GateControl, Resources resources) {
        this.i2cAddress = i2cAddress;
        this.i2cAdapter = i2cAdapter;
        this.xtal = xtal;
        this.i2GateControl = i2GateControl;
        this.resources = resources;
    }

    private void wrRegs(int reg, byte[] val) throws DvbException {
        wrRegs(reg, val, val.length);
    }

    private void wrRegs(int reg, byte[] val, int length) throws DvbException {
        if (length + 1 > MAX_XFER_SIZE) throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));

        byte[] buf = new byte[length + 1];
        buf[0] = (byte) reg;
        System.arraycopy(val, 0, buf, 1, length);

        i2cAdapter.transfer(i2cAddress, 0, buf);
    }

    private void readRegs(int reg, byte[] out) throws DvbException {
        readRegs(reg, out, out.length);
    }

    private void readRegs(int reg, byte[] buf, int length) throws DvbException {
        if (length > MAX_XFER_SIZE) throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
        i2cAdapter.transfer(
                i2cAddress, 0, new byte[] { (byte) reg }, 1,
                i2cAddress, I2C_M_RD, buf, length
        );
    }

    private void wrReg(int reg, int val) throws DvbException {
        i2cAdapter.transfer(i2cAddress, 0, new byte[] { (byte) reg, (byte) val });
    }

    private int rdReg(int reg) throws DvbException {
        byte[] val = new byte[1];
        readRegs(reg, val);
        return val[0] & 0xFF;
    }

    @Override
    public void init() throws DvbException {
        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {

                /* dummy I2C to ensure I2C wakes up */
                wrReg(0x02, 0x40);

	            /* reset */
                wrReg(0x00, 0x01);

	            /* disable output clock */
                wrReg(0x06, 0x00);
                wrReg(0x7a, 0x96);

	            /* configure gains */
                wrRegs(0x7e, new byte[]{(byte) 0x01, (byte) 0xFE});
                wrReg(0x82, 0x00);
                wrReg(0x24, 0x05);
                wrRegs(0x87, new byte[]{(byte) 0x20, (byte) 0x01});
                wrRegs(0x9f, new byte[]{(byte) 0x7F, (byte) 0x07});

	            /* DC offset control */
                wrReg(0x2d, 0x1f);
                wrRegs(0x70, new byte[]{(byte) 0x01, (byte) 0x01});

	            /* gain control */
                wrReg(0x1a, 0x17);
                wrReg(0x1f, 0x1a);

            }
        });
    }

    private void sleep() throws DvbException {
        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                wrReg(0x00, 0x00);
            }
        });
    }

    @Override
    public void attatch() throws DvbException {
        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                int chipId = rdReg(0x02);
                if (chipId != 0x40) throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.unexpected_chip_id));

                // put sleep as chip seems to be in normal mode by default
                wrReg(0x00, 0x00);
            }
        });
    }

    @Override
    public void release() {
        try {
            sleep();
        } catch (DvbException ignored) {}
    }

    @Override
    public void setParams(final long frequency, final long bandwidthHz, DeliverySystem ignored) throws DvbException {
        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                /* gain control manual */
                wrReg(0x1a, 0x00);

                int i;

                /* PLL */
                for (i = 0; i < E4000_PLL_LUT.length; i++) {
                    if (frequency <= E4000_PLL_LUT[i].freq) {
                        break;
                    }
                }

                if (i == E4000_PLL_LUT.length) throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.cannot_tune, frequency / 1_000_000L));

                /*
	             * Note: Currently f_VCO overflows when c->frequency is 1 073 741 824 Hz
	             * or more.
	             */
                byte[] buf = new byte[5];

                E4000Pll pllLut = E4000_PLL_LUT[i];
                long f_VCO = frequency * pllLut.mul;
                long sigma_delta = 0x10000L * (f_VCO % xtal) / xtal;
                buf[0] = (byte) (f_VCO / xtal);
                buf[1] = (byte) sigma_delta;
                buf[2] = (byte) (sigma_delta >> 8);
                buf[3] = (byte) 0x00;
                buf[4] = (byte) pllLut.div;
                wrRegs(0x09, buf);

                /* LNA filter (RF filter) */
                for (i = 0; i < E4000_LNA_LUT.length; i++) {
                    if (frequency <= E4000_LNA_LUT[i].freq) {
                        break;
                    }
                }
                if (i == E4000_LNA_LUT.length) throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.cannot_tune, frequency / 1_000_000L));

                wrReg(0x10, E4000_LNA_LUT[i].val);

                /* IF filters */
                for (i = 0; i < E4000_IF_LUT.length; i++) {
                    if (bandwidthHz <= E4000_IF_LUT[i].freq) {
                        break;
                    }
                }

                if (i == E4000_IF_LUT.length) throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.cannot_tune, frequency / 1_000_000L));

                buf[0] = (byte) E4000_IF_LUT[i].reg11val;
                buf[1] = (byte) E4000_IF_LUT[i].reg12val;

                wrRegs(0x11, buf, 2);

                /* frequency band */
                for (i = 0; i < E4000_FREQ_BANDS.length; i++) {
                    if (frequency <= E4000_FREQ_BANDS[i].freq) {
                        break;
                    }
                }

                if (i == E4000_FREQ_BANDS.length) throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.cannot_tune, frequency / 1_000_000L));

                wrReg(0x07, E4000_FREQ_BANDS[i].reg07val);
                wrReg(0x78, E4000_FREQ_BANDS[i].reg78val);

                /* DC offset */
                byte[] i_data = new byte[4];
                byte[] q_data = new byte[4];
                for (i = 0; i < 4; i++) {
                    if (i == 0) {
                        wrRegs(0x15, new byte[] {(byte) 0x00, (byte) 0x7e, (byte) 0x24});
                    } else if (i == 1) {
                        wrRegs(0x15, new byte[] {(byte) 0x00, (byte) 0x7f});
                    } else if (i == 2) {
                        wrRegs(0x15, new byte[] {(byte) 0x01});
                    } else {
                        wrRegs(0x16, new byte[] {(byte) 0x7e});
                    }

                    wrReg(0x29, 0x01);

                    readRegs(0x2a, buf, 3);
                    i_data[i] = (byte) (((buf[2] & 0x3) << 6) | (buf[0] & 0x3f));
                    q_data[i] = (byte) (((((buf[2] & 0xFF) >> 4) & 0x3) << 6) | (buf[1] & 0x3f));
                }

                swap(q_data, 2, 3);
                swap(i_data, 2, 3);

                wrRegs(0x50, q_data, 4);
                wrRegs(0x60, i_data, 4);

                /* gain control auto */
                wrReg(0x1a, 0x17);
            }
        });
    }

    private static void swap(byte[] arr, int id1, int id2) {
        byte tmp = arr[id1];
        arr[id1] = arr[id2];
        arr[id2] = tmp;
    }

    @Override
    public long getIfFrequency() throws DvbException {
        return 0; // Zero-IF
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        throw new UnsupportedOperationException();
    }
}
