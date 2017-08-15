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

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.tools.SleepUtils;
import info.martinmarinov.drivers.tools.ThrowingRunnable;
import info.martinmarinov.drivers.usb.DvbTuner;

import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;

class FC0013Tuner implements DvbTuner {
    private final static boolean DUAL_MASTER = false;

    private final int i2cAddr;
    private final Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter;
    private final long xtal;
    private final I2cAdapter.I2GateControl i2GateControl;

    FC0013Tuner(int i2cAddr, Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter, long xtal, I2cAdapter.I2GateControl i2GateControl) {
        this.i2cAddr = i2cAddr;
        this.i2cAdapter = i2cAdapter;
        this.xtal = xtal;
        this.i2GateControl = i2GateControl;
    }

    private void wr(int reg, int val) throws DvbException {
        i2cAdapter.transfer(i2cAddr, 0, new byte[] { (byte) reg, (byte) val });
    }

    private int rd(int reg) throws DvbException {
        byte[] response = new byte[1];
        i2cAdapter.transfer(
                i2cAddr, 0, new byte[] { (byte) reg },
                i2cAddr, I2C_M_RD, response
        );
        return response[0] & 0xFF;
    }

    @Override
    public void attatch() throws DvbException {
        // no-op
    }

    @Override
    public void release() {
        // no-op
    }

    @Override
    public void init() throws DvbException {
        final int[] reg = new int[] {
                0x00,	/* reg. 0x00: dummy */
                0x09,	/* reg. 0x01 */
                0x16,	/* reg. 0x02 */
                0x00,	/* reg. 0x03 */
                0x00,	/* reg. 0x04 */
                0x17,	/* reg. 0x05 */
                0x02,	/* reg. 0x06 */
                0x0a,	/* reg. 0x07: CHECK */
                0xff,	/* reg. 0x08: AGC Clock divide by 256, AGC gain 1/256,
			                    Loop Bw 1/8 */
                0x6f,	/* reg. 0x09: enable LoopThrough */
                0xb8,	/* reg. 0x0a: Disable LO Test Buffer */
                0x82,	/* reg. 0x0b: CHECK */
                0xfc,	/* reg. 0x0c: depending on AGC Up-Down mode, may need 0xf8 */
                0x01,	/* reg. 0x0d: AGC Not Forcing & LNA Forcing, may need 0x02 */
                0x00,	/* reg. 0x0e */
                0x00,	/* reg. 0x0f */
                0x00,	/* reg. 0x10 */
                0x00,	/* reg. 0x11 */
                0x00,	/* reg. 0x12 */
                0x00,	/* reg. 0x13 */
                0x50,	/* reg. 0x14: DVB-t High Gain, UHF.
			                    Middle Gain: 0x48, Low Gain: 0x40 */
                0x01,	/* reg. 0x15 */
        };

        if (xtal == 27_000_000L || xtal == 28_800_000L) {
            reg[0x07] |= 0x20;
        }

        if (DUAL_MASTER) {
            reg[0x0c] |= 0x02;
        }

        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                for (int i = 1; i < reg.length; i++) {
                    wr(i, reg[i]);
                }
            }
        });
    }

    @Override
    public void setParams(long frequency, final long bandwidthHz, DeliverySystem ignored) throws DvbException {
        final long freq = frequency / 1_000L;

        final int xtalFreqKhz2;
        switch ((int) xtal) {
            case 27_000_000:
                xtalFreqKhz2 = 27000 / 2;
                break;
            case 36_000_000:
                xtalFreqKhz2 = 36000 / 2;
                break;
            case 28_800_000:
            default:
                xtalFreqKhz2 = 28800 / 2;
                break;
        }

        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                setVhfTrack(freq);

                if (freq < 300000) {
		            /* enable VHF filter */
                    int tmp = rd(0x07);
                    wr(0x07, tmp | 0x10);

		            /* disable UHF & disable GPS */
                    tmp = rd(0x14);
                    wr(0x14, tmp & 0x1f);
                } else if (freq <= 862000) {
		            /* disable VHF filter */
                    int tmp = rd(0x07);
                    wr(0x07, tmp & 0xef);

		            /* enable UHF & disable GPS */
                    tmp = rd(0x14);
                    wr(0x14, (tmp & 0x1f) | 0x40);
                } else {
		            /* disable VHF filter */
                    int tmp = rd(0x07);
                    wr(0x07, tmp & 0xef);

		            /* disable UHF & enable GPS */
                    tmp = rd(0x14);
                    wr(0x14, (tmp & 0x1f) | 0x20);
                }

                int[] reg = new int[7];
                int multi;

	        /* select frequency divider and the frequency of VCO */
                if (freq < 37084) {		/* freq * 96 < 3560000 */
                    multi = 96;
                    reg[5] = 0x82;
                    reg[6] = 0x00;
                } else if (freq < 55625) {	/* freq * 64 < 3560000 */
                    multi = 64;
                    reg[5] = 0x02;
                    reg[6] = 0x02;
                } else if (freq < 74167) {	/* freq * 48 < 3560000 */
                    multi = 48;
                    reg[5] = 0x42;
                    reg[6] = 0x00;
                } else if (freq < 111250) {	/* freq * 32 < 3560000 */
                    multi = 32;
                    reg[5] = 0x82;
                    reg[6] = 0x02;
                } else if (freq < 148334) {	/* freq * 24 < 3560000 */
                    multi = 24;
                    reg[5] = 0x22;
                    reg[6] = 0x00;
                } else if (freq < 222500) {	/* freq * 16 < 3560000 */
                    multi = 16;
                    reg[5] = 0x42;
                    reg[6] = 0x02;
                } else if (freq < 296667) {	/* freq * 12 < 3560000 */
                    multi = 12;
                    reg[5] = 0x12;
                    reg[6] = 0x00;
                } else if (freq < 445000) {	/* freq * 8 < 3560000 */
                    multi = 8;
                    reg[5] = 0x22;
                    reg[6] = 0x02;
                } else if (freq < 593334) {	/* freq * 6 < 3560000 */
                    multi = 6;
                    reg[5] = 0x0a;
                    reg[6] = 0x00;
                } else if (freq < 950000) {	/* freq * 4 < 3800000 */
                    multi = 4;
                    reg[5] = 0x12;
                    reg[6] = 0x02;
                } else {
                    multi = 2;
                    reg[5] = 0x0a;
                    reg[6] = 0x02;
                }

                long f_vco = freq * multi;
                boolean vco_select = false;

                if (f_vco >= 3_060_000L) {
                    reg[6] |= 0x08;
                    vco_select = true;
                }

                if (freq >= 45_000L) {
		        /* From divided value (XDIV) determined the FA and FP value */
                    long xdiv = (f_vco / xtalFreqKhz2);
                    if ((f_vco - xdiv * xtalFreqKhz2) >= (xtalFreqKhz2 / 2)) {
                        xdiv++;
                    }

                    int pm = (int) (xdiv / 8);
                    int am = (int) (xdiv - (8 * pm));

                    if (am < 2) {
                        reg[1] = am + 8;
                        reg[2] = pm - 1;
                    } else {
                        reg[1] = am;
                        reg[2] = pm;
                    }
                } else {
		        /* fix for frequency less than 45 MHz */
                    reg[1] = 0x06;
                    reg[2] = 0x11;
                }

	            /* fix clock out */
                reg[6] |= 0x20;

	            /* From VCO frequency determines the XIN ( fractional part of Delta
	            Sigma PLL) and divided value (XDIV) */
                int xin = (int) (f_vco - (f_vco / xtalFreqKhz2) * xtalFreqKhz2);
                xin = (xin << 15) / xtalFreqKhz2;
                if (xin >= 16384) {
                    xin += 32768;
                }

                reg[3] = xin >> 8;
                reg[4] = xin & 0xff;

                reg[6] &= 0x3f; /* bits 6 and 7 describe the bandwidth */
                switch ((int) bandwidthHz) {
                    case 6000000:
                        reg[6] |= 0x80;
                        break;
                    case 7000000:
                        reg[6] |= 0x40;
                        break;
                    case 8000000:
                    default:
                        break;
                }

	            /* modified for Realtek demod */
                reg[5] |= 0x07;

                for (int i = 1; i <= 6; i++) {
                    wr(i, reg[i]);
                }

                int tmp = rd(0x11);

                if (multi == 64) {
                    wr(0x11, tmp | 0x04);
                } else {
                    wr(0x11, tmp & 0xfb);
                }

	            /* VCO Calibration */
                wr(0x0e, 0x80);
                wr(0x0e, 0x00);

	            /* VCO Re-Calibration if needed */
                wr(0x0e, 0x00);

                SleepUtils.mdelay(10);
                tmp = rd(0x0e);

	            /* vco selection */
                tmp &= 0x3f;

                if (vco_select) {
                    if (tmp > 0x3c) {
                        reg[6] &= (~0x08) & 0xFF;
                        wr(0x06, reg[6]);
                        wr(0x0e, 0x80);
                        wr(0x0e, 0x00);
                    }
                } else {
                    if (tmp < 0x02) {
                        reg[6] |= 0x08;
                        wr(0x06, reg[6]);
                        wr(0x0e, 0x80);
                        wr(0x0e, 0x00);
                    }
                }
            }
        });
    }

    private void setVhfTrack(long freq) throws DvbException {
        int tmp = rd(0x1d);
        tmp &= 0xe3;
        if (freq <= 177500) {		/* VHF Track: 7 */
            wr(0x1d, tmp | 0x1c);
        } else if (freq <= 184500) {	/* VHF Track: 6 */
            wr(0x1d, tmp | 0x18);
        } else if (freq <= 191500) {	/* VHF Track: 5 */
            wr(0x1d, tmp | 0x14);
        } else if (freq <= 198500) {	/* VHF Track: 4 */
            wr(0x1d, tmp | 0x10);
        } else if (freq <= 205500) {	/* VHF Track: 3 */
            wr(0x1d, tmp | 0x0c);
        } else if (freq <= 219500) {	/* VHF Track: 2 */
            wr(0x1d, tmp | 0x08);
        } else if (freq < 300000) {	/* VHF Track: 1 */
            wr(0x1d, tmp | 0x04);
        } else {			/* UHF and GPS */
            wr(0x1d, tmp | 0x1c);
        }
    }

    @Override
    public long getIfFrequency() throws DvbException {
        /* always ? */
        return 0;
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        throw new UnsupportedOperationException();
    }
}
