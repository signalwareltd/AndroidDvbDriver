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
import androidx.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.BitReverse;
import info.martinmarinov.drivers.tools.I2cAdapter.I2GateControl;
import info.martinmarinov.drivers.tools.SleepUtils;
import info.martinmarinov.drivers.tools.ThrowingRunnable;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxDvbDevice.Rtl28xxI2cAdapter;

import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;
import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;

class R820tTuner implements DvbTuner {
    private final static int MAX_I2C_MSG_LEN = 2;
    private final static String TAG = R820tTuner.class.getSimpleName();

    @SuppressWarnings("unused")
    enum RafaelChip {
        CHIP_R820T, CHIP_R620D, CHIP_R828D, CHIP_R828, CHIP_R828S, CHIP_R820C
    }

    @SuppressWarnings("unused")
    enum XtalCapValue {
        XTAL_LOW_CAP_30P,
        XTAL_LOW_CAP_20P,
        XTAL_LOW_CAP_10P,
        XTAL_LOW_CAP_0P,
        XTAL_HIGH_CAP_0P
    }

    private final int i2cAddress;
    private final Rtl28xxI2cAdapter i2cAdapter;
    private final RafaelChip rafaelChip;
    private final long xtal;
    private final I2GateControl i2GateControl;
    private final Resources resources;

    private final static int VCO_POWER_REF = 0x02;
    private final static int VER_NUM = 49;

    private final static int NUM_REGS = 27;
    private final static int REG_SHADOW_START = 5;
    private final byte[] regs = new byte[NUM_REGS];

    private final static int NUM_IMR = 5;
    private final static int IMR_TRIAL = 9;
    private final SectType[] imrData = SectType.newArray(NUM_IMR);

    private XtalCapValue xtalCapValue;
    private boolean hasLock = false;
    private boolean imrDone = false;
    private boolean initDone = false;
    private long intFreq = 0;

    private long mBw;
    private int filCalCode;

    R820tTuner(int i2cAddress, Rtl28xxI2cAdapter i2cAdapter, RafaelChip rafaelChip, long xtal, I2GateControl i2GateControl, Resources resources) {
        this.i2cAddress = i2cAddress;
        this.i2cAdapter = i2cAdapter;
        this.rafaelChip = rafaelChip;
        this.xtal = xtal;
        this.i2GateControl = i2GateControl;
        this.resources = resources;
    }

    // IO

    private void shadowStore(int reg, byte[] val) {
        int len = val.length;
        int r = reg - REG_SHADOW_START;
        if (r < 0) {
            len += r;
            r = 0;
        }
        if (len <= 0) {
            return;
        }
        if (len > NUM_REGS) {
            len = NUM_REGS;
        }
        System.arraycopy(val, 0, regs, r, len);
    }

    private void write(int reg, byte[] val) throws DvbException {
        shadowStore(reg, val);

        int len = val.length;
        int pos = 0;
        byte[] buf = new byte[len+1];
        do {
            int size = len > MAX_I2C_MSG_LEN - 1 ? MAX_I2C_MSG_LEN - 1 : len;

            buf[0] = (byte) reg;
            System.arraycopy(val, pos, buf, 1, size);

            i2cAdapter.transfer(i2cAddress, 0, buf, size + 1);

            reg += size;
            len -= size;
            pos += size;
        } while (len > 0);
    }

    private void writeReg(int reg, int val) throws DvbException {
        write(reg, new byte[] {(byte) val});
    }

    private void writeRegMask(int reg, int val, int bitMask) throws DvbException {
        int rc = readCacheReg(reg);

        val = (rc & ~bitMask) | (val & bitMask);
        write(reg, new byte[] {(byte) val});
    }

    private void read(int reg, byte[] val, int len) throws DvbException {
        i2cAdapter.transfer(
                i2cAddress, 0, new byte[] {(byte) reg}, 1,
                i2cAddress, I2C_M_RD, val, len
        );
        for (int i = 0; i < len; i++) val[i] = BitReverse.bitRev8(val[i]);
    }

    private void read(int reg, byte[] val) throws DvbException {
        read(reg, val, val.length);
    }

    private int readCacheReg(int reg) {
        reg -= REG_SHADOW_START;
        if (reg < 0 || reg >= NUM_REGS) throw new IllegalArgumentException();
        return regs[reg] & 0xFF;
    }

    private int multiRead() throws DvbException {
        int sum = 0;
        int min = 255;
        int max = 0;
        byte[] data = new byte[2];

        SleepUtils.usleep(5_000);
        for (int i = 0; i < 6; i++) {
            read(0, data);

            int dataVal = data[1] & 0xFF;
            sum += dataVal;

            if (dataVal < min) min = dataVal;
            if (dataVal > max) max = dataVal;
        }


        int rc = sum - max - min;
        if (rc < 0) throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.failed_callibration_step));
        return rc;
    }

    // Logic
    private void initRegs() throws DvbException {
        write(REG_SHADOW_START, R820tTunerData.INIT_REGS);
    }

    private void imrPrepare() throws DvbException {
        /* Initialize the shadow registers */
        System.arraycopy(R820tTunerData.INIT_REGS, 0, regs, 0, R820tTunerData.INIT_REGS.length);
        /* lna off (air-in off) */
        writeRegMask(0x05, 0x20, 0x20);
        /* mixer gain mode = manual */
        writeRegMask(0x07, 0, 0x10);
        /* filter corner = lowest */
        writeRegMask(0x0a, 0x0f, 0x0f);
        /* filter bw=+2cap, hp=5M */
        writeRegMask(0x0b, 0x60, 0x6f);
        /* adc=on, vga code mode, gain = 26.5dB   */
        writeRegMask(0x0c, 0x0b, 0x9f);
        /* ring clk = on */
        writeRegMask(0x0f, 0, 0x08);
        /* ring power = on */
        writeRegMask(0x18, 0x10, 0x10);
        /* from ring = ring pll in */
        writeRegMask(0x1c, 0x02, 0x02);
        /* sw_pdect = det3 */
        writeRegMask(0x1e, 0x80, 0x80);
        /* Set filt_3dB */
        writeRegMask(0x06, 0x20, 0x20);
    }

    private void vgaAdjust() throws DvbException {
        /* increase vga power to let image significant */
        for (int vgaCount = 12; vgaCount < 16; vgaCount++) {
            writeRegMask(0x0c, vgaCount, 0x0f);
            SleepUtils.usleep(10_000L);

            int rc = multiRead();
            if (rc > 40 * 4) break;
        }
    }

    private boolean imrCross(SectType[] iqPoint) throws DvbException {
        SectType[] cross = SectType.newArray(5);

        int reg08 = readCacheReg(0x08) & 0xc0;
        int reg09 = readCacheReg(0x09) & 0xc0;

        SectType tmp = new SectType();
        tmp.value = 255;

        for (int i = 0; i < 5; i++) {
            switch (i) {
                case 0:
                    cross[i].gainX  = reg08;
                    cross[i].phaseY = reg09;
                    break;
                case 1:
                    cross[i].gainX  = reg08;		/* 0 */
                    cross[i].phaseY = reg09 + 1;		/* Q-1 */
                    break;
                case 2:
                    cross[i].gainX  = reg08;		/* 0 */
                    cross[i].phaseY = (reg09 | 0x20) + 1;	/* I-1 */
                    break;
                case 3:
                    cross[i].gainX  = reg08 + 1;		/* Q-1 */
                    cross[i].phaseY = reg09;
                    break;
                default:
                    cross[i].gainX  = (reg08 | 0x20) + 1;	/* I-1 */
                    cross[i].phaseY = reg09;
            }

            writeReg(0x08, cross[i].gainX);
            writeReg(0x09, cross[i].phaseY);

            cross[i].value = multiRead();
            if (cross[i].value < tmp.value) tmp.copyFrom(cross[i]);
        }

        if ((tmp.phaseY & 0x1f) == 1) {	/* y-direction */
            iqPoint[0].copyFrom(cross[0]);
            iqPoint[1].copyFrom(cross[1]);
            iqPoint[2].copyFrom(cross[2]);
            return false;
        } else {				/* (0,0) or x-direction */
            iqPoint[0].copyFrom(cross[0]);
            iqPoint[1].copyFrom(cross[3]);
            iqPoint[2].copyFrom(cross[4]);
            return true;
        }
    }

    private static void compreCor(SectType[] iq) {
        for (int i = 3; i > 0; i--) {
            int otherId = i - 1;
            if (iq[0].value > iq[otherId].value) {
                SectType temp = new SectType();
                temp.copyFrom(iq[0]);
                iq[0].copyFrom(iq[otherId]);
                iq[otherId].copyFrom(temp);
            }
        }
    }

    private void compreSep(SectType[] iq, int reg) throws DvbException {
        /*
	     * Purpose: if (Gain<9 or Phase<9), Gain+1 or Phase+1 and compare
	     * with min value:
	     *  new < min => update to min and continue
	     *  new > min => Exit
	     */
        SectType tmp = new SectType();

        /* min value already saved in iq[0] */
        tmp.phaseY = iq[0].phaseY;
        tmp.gainX = iq[0].gainX;

        while (((tmp.gainX & 0x1f) < IMR_TRIAL) &&
                ((tmp.phaseY & 0x1f) < IMR_TRIAL)) {
            if (reg == 0x08)
                tmp.gainX++;
            else
                tmp.phaseY++;

            writeReg(0x08, tmp.gainX);
            writeReg(0x09, tmp.phaseY);

            tmp.value = multiRead();

            if (tmp.value <= iq[0].value) {
                iq[0].gainX  = tmp.gainX;
                iq[0].phaseY = tmp.phaseY;
                iq[0].value   = tmp.value;
            } else {
                return;
            }
        }
    }

    private void iqTree(SectType[] iq, int fixVal, int varVal, int fixReg) throws DvbException {
        /*
	     * record IMC results by input gain/phase location then adjust
	     * gain or phase positive 1 step and negtive 1 step,
	     * both record results
	     */
        int varReg = fixReg == 0x08 ? 0x09 : 0x08;

        for (int i = 0; i < 3; i++) {
            writeReg(fixReg, fixVal);
            writeReg(varReg, varVal);

            iq[i].value = multiRead();

            if (fixReg == 0x08) {
                iq[i].gainX  = fixVal;
                iq[i].phaseY = varVal;
            } else {
                iq[i].phaseY = fixVal;
                iq[i].gainX  = varVal;
            }

            if (i == 0) {  /* try right-side point */
                varVal++;
            } else if (i == 1) { /* try left-side point */
			    /* if absolute location is 1, change I/Q direction */
                if ((varVal & 0x1f) < 0x02) {
                    int tmp = 2 - (varVal & 0x1f);

				    /* b[5]:I/Q selection. 0:Q-path, 1:I-path */
                    if ((varVal & 0x20) != 0) {
                        varVal &= 0xc0;
                        varVal |= tmp;
                    } else {
                        varVal |= 0x20 | tmp;
                    }
                } else {
                    varVal -= 2;
                }
            }
        }
    }

    private void section(SectType iqPoint) throws DvbException {
        SectType[] compareIq = SectType.newArray(3);
        SectType[] compareBet = SectType.newArray(3);

        /* Try X-1 column and save min result to compare_bet[0] */
        if ((iqPoint.gainX & 0x1f) == 0) {
            compareIq[0].gainX = ((iqPoint.gainX) & 0xdf) + 1;  /* Q-path, Gain=1 */
        } else {
            compareIq[0].gainX = iqPoint.gainX - 1;  /* left point */
        }
        compareIq[0].phaseY = iqPoint.phaseY;

        /* y-direction */
        iqTree(compareIq, compareIq[0].gainX, compareIq[0].phaseY, 0x08);
        compreCor(compareIq);
        compareBet[0].copyFrom(compareIq[0]);

        /* Try X column and save min result to compare_bet[1] */
        compareIq[0].gainX  = iqPoint.gainX;
        compareIq[0].phaseY = iqPoint.phaseY;

        iqTree(compareIq, compareIq[0].gainX, compareIq[0].phaseY, 0x08);
        compreCor(compareIq);

        compareBet[1].copyFrom(compareIq[0]);

	    /* Try X+1 column and save min result to compare_bet[2] */
        if ((iqPoint.gainX & 0x1f) == 0x00) {
            compareIq[0].gainX = ((iqPoint.gainX) | 0x20) + 1;  /* I-path, Gain=1 */
        } else {
            compareIq[0].gainX = iqPoint.gainX + 1;
        }
        compareIq[0].phaseY = iqPoint.phaseY;
        iqTree(compareIq, compareIq[0].gainX, compareIq[0].phaseY, 0x08);
        compreCor(compareIq);
        compareBet[2].copyFrom(compareIq[0]);
        compreCor(compareBet);

        iqPoint.copyFrom(compareBet[0]);
    }

    private SectType iq() throws DvbException {
        vgaAdjust();

        SectType[] compareIq = SectType.newArray(3);
        boolean xDirection = imrCross(compareIq);

        int dirReg, otherReg;
        if (xDirection) {
            dirReg = 0x08;
            otherReg = 0x09;
        } else {
            dirReg = 0x09;
            otherReg = 0x08;
        }

        /* compare and find min of 3 points. determine i/q direction */
        compreCor(compareIq);
        /* increase step to find min value of this direction */
        compreSep(compareIq, dirReg);
        /* the other direction */
        iqTree(compareIq, compareIq[0].gainX, compareIq[0].phaseY, dirReg);
        /* compare and find min of 3 points. determine i/q direction */
        compreCor(compareIq);
        /* increase step to find min value on this direction */
        compreSep(compareIq, otherReg);
        /* check 3 points again */
        iqTree(compareIq, compareIq[0].gainX, compareIq[0].phaseY, otherReg);
        compreCor(compareIq);
        section(compareIq[0]);
        /* reset gain/phase control setting */
        writeRegMask(0x08, 0, 0x3f);
        writeRegMask(0x09, 0, 0x3f);

        return compareIq[0];
    }

    private void fImr(SectType iqPoint) throws DvbException {
        vgaAdjust();

        /*
	    * search surrounding points from previous point
	    * try (x-1), (x), (x+1) columns, and find min IMR result point
	    */
        section(iqPoint);
    }

    private void setMux(long freq) throws DvbException {
        /* Get the proper frequency range */
        freq = freq / 1_000_000L;

        R820tTunerData.FreqRange range = R820tTunerData.FREQ_RANGES[R820tTunerData.FREQ_RANGES.length - 1];
        for (int i = 0; i < R820tTunerData.FREQ_RANGES.length - 1; i++) {
            if (freq < R820tTunerData.FREQ_RANGES[i + 1].freq) {
                range = R820tTunerData.FREQ_RANGES[i];
                break;
            }
        }

        /* Open Drain */
        writeRegMask(0x17, range.openD, 0x08);
        /* RF_MUX,Polymux */
        writeRegMask(0x1a, range.rfMuxPloy, 0xc3);
        /* TF BAND */
        writeReg(0x1b, range.tfC);

        /* XTAL CAP & Drive */
        int val;
        switch (xtalCapValue) {
            case XTAL_LOW_CAP_30P:
            case XTAL_LOW_CAP_20P:
                val = range.xtalCap20p | 0x08;
                break;
            case XTAL_LOW_CAP_10P:
                val = range.xtalCap10p | 0x08;
                break;
            case XTAL_HIGH_CAP_0P:
                val = range.xtalCap0p;
                break;
            default:
            case XTAL_LOW_CAP_0P:
                val = range.xtalCap0p | 0x08;
                break;
        }
        writeRegMask(0x10, val, 0x0b);

        int reg08, reg09;
        if (imrDone) {
            reg08 = imrData[range.imrMem].gainX;
            reg09 = imrData[range.imrMem].phaseY;
        } else {
            reg08 = 0;
            reg09 = 0;
        }
        writeRegMask(0x08, reg08, 0x3f);
        writeRegMask(0x09, reg09, 0x3f);
    }

    private void setPll(long freq) throws DvbException {
        /* Frequency in kHz */
        freq = freq / 1_000L;
        long pllRef = xtal / 1_000L;

        // refdiv2 which is disabled in original driver
        writeRegMask(0x10, 0x00, 0x10);
        /* set pll autotune = 128kHz */
        writeRegMask(0x1a, 0x00, 0x0c);
        /* set VCO current = 100 */
        writeRegMask(0x12, 0x80, 0xe0);

        /* Calculate divider */
        int mixDiv = 2;
        int divNum = 0;
        long vcoMin  = 1_770_000L;
        long vcoMax  = vcoMin * 2;

        while (mixDiv <= 64) {
            if (((freq * mixDiv) >= vcoMin) &&
                    ((freq * mixDiv) < vcoMax)) {
                int divBuf = mixDiv;
                while (divBuf > 2) {
                    divBuf = divBuf >> 1;
                    divNum++;
                }
                break;
            }
            mixDiv = mixDiv << 1;
        }

        byte[] data = new byte[5];
        read(0x00, data);

        int vcoFineTune = (data[4] & 0x30) >> 4;

        if (rafaelChip != RafaelChip.CHIP_R828D) {
            if (vcoFineTune > VCO_POWER_REF) {
                divNum--;
            } else if (vcoFineTune < VCO_POWER_REF) {
                divNum++;
            }
        }
        writeRegMask(0x10, divNum << 5, 0xe0);

        long vcoFreq = freq * mixDiv;
        long nint = vcoFreq / (2 * pllRef);
        long vcoFra = vcoFreq - 2 * pllRef * nint;

	    /* boundary spur prevention */
        if (vcoFra < pllRef / 64) {
            vcoFra = 0;
        } else if (vcoFra > pllRef * 127 / 64) {
            vcoFra = 0;
            nint++;
        } else if ((vcoFra > pllRef * 127 / 128) && (vcoFra < pllRef)) {
            vcoFra = pllRef * 127 / 128;
        } else if ((vcoFra > pllRef) && (vcoFra < pllRef * 129 / 128)) {
            vcoFra = pllRef * 129 / 128;
        }

        long ni = (nint - 13) / 4;
        long si = nint - 4 * ni - 13;

        writeReg(0x14, (int) (ni + (si << 6)));

        /* pw_sdm */
        int val = vcoFra == 0 ? 0x08 : 0x00;
        writeRegMask(0x12, val, 0x08);

        /* sdm calculator */
        int nSdm = 2;
        int sdm = 0;
        while (vcoFra > 1) {
            if (vcoFra > (2 * pllRef / nSdm)) {
                sdm = sdm + 32768 / (nSdm / 2);
                vcoFra = vcoFra - 2 * pllRef / nSdm;
                if (nSdm >= 0x8000)
                    break;
            }
            nSdm = nSdm << 1;
        }
        writeReg(0x16, sdm >> 8);
        writeReg(0x15, sdm & 0xFF);

        for (int i = 0; i < 2; i++) {
            SleepUtils.usleep(1_0000L);

            /* Check if PLL has locked */
            read(0x00, data, 3);
            if ((data[2] & 0x40) != 0)
                break;

            if (i == 0) {
			    /* Didn't lock. Increase VCO current */
                writeRegMask(0x12, 0x60, 0xe0);
            }
        }

        if ((data[2] & 0x40) == 0) {
            hasLock = false;
        } else {
            hasLock = true;

            Log.d(TAG, String.format("tuner has lock at frequency %d kHz\n", freq));
            writeRegMask(0x1a, 0x08, 0x08);
        }
    }

    private void imr(int imrMem, boolean imFlag) throws DvbException {
        long ringRef = xtal > 24_000_000L ? xtal / 2_000L : xtal / 1_000L;
        int nRing = 15;
        for (int n = 0; n < 16; n++) {
            if ((16L + n) * 8L * ringRef >= 3_100_000L) {
                nRing = n;
                break;
            }
        }

        int reg18 = readCacheReg(0x18);
        int reg19 = readCacheReg(0x19);
        int reg1f = readCacheReg(0x1f);

        reg18 &= 0xf0;      /* set ring[3:0] */
        reg18 |= nRing;

        long ringVco = (16L + nRing) * 8L * ringRef;

        reg18 &= 0xdf;   /* clear ring_se23 */
        reg19 &= 0xfc;   /* clear ring_seldiv */
        reg1f &= 0xfc;   /* clear ring_att */

        long ringFreq;
        switch (imrMem) {
            case 0:
                ringFreq = ringVco / 48;
                reg18 |= 0x20;  /* ring_se23 = 1 */
                reg19 |= 0x03;  /* ring_seldiv = 3 */
                reg1f |= 0x02;  /* ring_att 10 */
                break;
            case 1:
                ringFreq = ringVco / 16;
                reg18 |= 0x00;  /* ring_se23 = 0 */
                reg19 |= 0x02;  /* ring_seldiv = 2 */
                reg1f |= 0x00;  /* pw_ring 00 */
                break;
            case 2:
                ringFreq = ringVco / 8;
                reg18 |= 0x00;  /* ring_se23 = 0 */
                reg19 |= 0x01;  /* ring_seldiv = 1 */
                reg1f |= 0x03;  /* pw_ring 11 */
                break;
            case 3:
                ringFreq = ringVco / 6;
                reg18 |= 0x20;  /* ring_se23 = 1 */
                reg19 |= 0x00;  /* ring_seldiv = 0 */
                reg1f |= 0x03;  /* pw_ring 11 */
                break;
            case 4:
                ringFreq = ringVco / 4;
                reg18 |= 0x00;  /* ring_se23 = 0 */
                reg19 |= 0x00;  /* ring_seldiv = 0 */
                reg1f |= 0x01;  /* pw_ring 01 */
                break;
            default:
                ringFreq = ringVco / 4;
                reg18 |= 0x00;  /* ring_se23 = 0 */
                reg19 |= 0x00;  /* ring_seldiv = 0 */
                reg1f |= 0x01;  /* pw_ring 01 */
                break;
        }

        /* write pw_ring, n_ring, ringdiv2 registers */
	    /* n_ring, ring_se23 */
        writeReg(0x18, reg18);
	    /* ring_sediv */
        writeReg(0x19, reg19);
	    /* pw_ring */
        writeReg(0x1f, reg1f);

	    /* mux input freq ~ rf_in freq */
        setMux((ringFreq - 5_300L) * 1_000L);
        setPll((ringFreq - 5_300L) * 1_000L);
        if (!hasLock) throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.cannot_calibrate_tuner));

        SectType imrPoint;
        if (imFlag) {
            imrPoint = iq();
        } else {
            imrPoint = new SectType();
            imrPoint.copyFrom(imrData[3]);

            fImr(imrPoint);
        }

        imrData[imrMem >= NUM_IMR ? NUM_IMR - 1 : imrMem].copyFrom(imrPoint);
    }

    private @NonNull XtalCapValue xtalCheck() throws DvbException {
        initRegs();

        /* cap 30pF & Drive Low */
        writeRegMask(0x10, 0x0b, 0x0b);

        /* set pll autotune = 128kHz */
        writeRegMask(0x1a, 0x00, 0x0c);

         /* set manual initial reg = 111111;  */
        writeRegMask(0x13, 0x7f, 0x7f);

         /* set auto */
        writeRegMask(0x13, 0x00, 0x40);

        /* Try several xtal capacitor alternatives */
        byte[] data = new byte[3];
        for (Pair<Integer, XtalCapValue> xtalCap : R820tTunerData.XTAL_CAPS) {
            writeRegMask(0x10, xtalCap.first, 0x1b);

            SleepUtils.usleep(6_000L);

            read(0x00, data);
            if ((data[2] & 0x40) == 0) {
                continue;
            }

            int val = data[2] & 0x3f;

            if (xtal == 16_000_000L && (val > 29 || val < 23)) {
                return xtalCap.second;
            }

            if (val != 0x3f) {
                return xtalCap.second;
            }
        }

        throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.cannot_calibrate_tuner));
    }

    private void imrCalibrate() throws DvbException {
        if (initDone) return;

        if (rafaelChip == RafaelChip.CHIP_R820T ||
                rafaelChip == RafaelChip.CHIP_R828S ||
                rafaelChip == RafaelChip.CHIP_R820C) {
            xtalCapValue = XtalCapValue.XTAL_HIGH_CAP_0P;
        } else {
            for (int i = 0; i < 3; i++) {
                XtalCapValue detectedCap = xtalCheck();
                if (i == 0 || detectedCap.ordinal() > xtalCapValue.ordinal()) {
                    xtalCapValue = detectedCap;
                }
            }
        }
        initRegs();

        imrPrepare();
        imr(3, true);
        imr(1, false);
        imr(0, false);
        imr(2, false);
        imr(4, false);

        imrDone = true;
        initDone = true;
    }

    private void standby() throws DvbException {
        /* If device was not initialized yet, don't need to standby */
        if (!initDone) return;

        writeReg(0x06, 0xb1);
        writeReg(0x05, 0x03);
        writeReg(0x07, 0x3a);
        writeReg(0x08, 0x40);
        writeReg(0x09, 0xc0);
        writeReg(0x0a, 0x36);
        writeReg(0x0c, 0x35);
        writeReg(0x0f, 0x68);
        writeReg(0x11, 0x03);
        writeReg(0x17, 0xf4);
        writeReg(0x19, 0x0c);
    }

    private void setTvStandard(long bw) throws DvbException {
        long ifKhz, filtCalLo;
        int filtGain, imgR, filtQ, hpCor, extEnable, loopThrough;
        int ltAtt, fltExtWidest, polyfilCur;

        if (bw <= 6) {
            ifKhz = 3570;
            filtCalLo = 56000;	/* 52000->56000 */
            filtGain = 0x10;	/* +3db, 6mhz on */
            imgR = 0x00;		/* image negative */
            filtQ = 0x10;		/* r10[4]:low q(1'b1) */
            hpCor = 0x6b;		/* 1.7m disable, +2cap, 1.0mhz */
            extEnable = 0x60;	/* r30[6]=1 ext enable; r30[5]:1 ext at lna max-1 */
            loopThrough = 0x00;	/* r5[7], lt on */
            ltAtt = 0x00;		/* r31[7], lt att enable */
            fltExtWidest = 0x00;	/* r15[7]: flt_ext_wide off */
            polyfilCur = 0x60;	/* r25[6:5]:min */
        } else if (bw == 7) {
			    /* 7 MHz, second table */
            ifKhz = 4570;
            filtCalLo = 63000;
            filtGain = 0x10;	/* +3db, 6mhz on */
            imgR = 0x00;		/* image negative */
            filtQ = 0x10;		/* r10[4]:low q(1'b1) */
            hpCor = 0x2a;		/* 1.7m disable, +1cap, 1.25mhz */
            extEnable = 0x60;	/* r30[6]=1 ext enable; r30[5]:1 ext at lna max-1 */
            loopThrough = 0x00;	/* r5[7], lt on */
            ltAtt = 0x00;		/* r31[7], lt att enable */
            fltExtWidest = 0x00;	/* r15[7]: flt_ext_wide off */
            polyfilCur = 0x60;	/* r25[6:5]:min */
        } else {
            ifKhz = 4570;
            filtCalLo = 68500;
            filtGain = 0x10;	/* +3db, 6mhz on */
            imgR = 0x00;		/* image negative */
            filtQ = 0x10;		/* r10[4]:low q(1'b1) */
            hpCor = 0x0b;		/* 1.7m disable, +0cap, 1.0mhz */
            extEnable = 0x60;	/* r30[6]=1 ext enable; r30[5]:1 ext at lna max-1 */
            loopThrough = 0x00;	/* r5[7], lt on */
            ltAtt = 0x00;		/* r31[7], lt att enable */
            fltExtWidest = 0x00;	/* r15[7]: flt_ext_wide off */
            polyfilCur = 0x60;	/* r25[6:5]:min */
        }

        /* Initialize the shadow registers */
        System.arraycopy(R820tTunerData.INIT_REGS, 0, regs, 0, R820tTunerData.INIT_REGS.length);

        /* Init Flag & Xtal_check Result */
        writeRegMask(0x0c, imrDone ? 1 | (xtalCapValue.ordinal() << 1) : 0, 0x0f);

        /* version */
        writeRegMask(0x13, VER_NUM, 0x3f);

        /* for LT Gain test */
        writeRegMask(0x1d, 0x00, 0x38);
        SleepUtils.usleep(1_000);
        intFreq = ifKhz * 1_000;

        /* Check if standard changed. If so, filter calibration is needed */
        boolean needCalibration = bw != mBw;

        if (needCalibration) {
            byte[] data = new byte[5];
            for (int i = 0; i < 2; i++) {
			    /* Set filt_cap */
                writeRegMask(0x0b, hpCor, 0x60);
			    /* set cali clk =on */
                writeRegMask(0x0f, 0x04, 0x04);
			    /* X'tal cap 0pF for PLL */
                writeRegMask(0x10, 0x00, 0x03);
                setPll(filtCalLo * 1_000L);
                if (!hasLock) throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.cannot_tune, filtCalLo / 1_000L));

			    /* Start Trigger */
                writeRegMask(0x0b, 0x10, 0x10);

                SleepUtils.usleep(1_000L);

			    /* Stop Trigger */
                writeRegMask(0x0b, 0x00, 0x10);
			    /* set cali clk =off */
                writeRegMask(0x0f, 0x00, 0x04);
			    /* Check if calibration worked */
                read(0x00, data);

                filCalCode = data[4] & 0x0f;
                if (filCalCode != 0 && filCalCode != 0x0f) {
                    break;
                }
            }
		    /* narrowest */
            if (filCalCode == 0x0f) filCalCode = 0;
        }

        writeRegMask(0x0a, filtQ | filCalCode, 0x1f);

	    /* Set BW, Filter_gain, & HP corner */
        writeRegMask(0x0b, hpCor, 0xef);
	    /* Set Img_R */
        writeRegMask(0x07, imgR, 0x80);
	    /* Set filt_3dB, V6MHz */
        writeRegMask(0x06, filtGain, 0x30);
	    /* channel filter extension */
        writeRegMask(0x1e, extEnable, 0x60);
	    /* Loop through */
        writeRegMask(0x05, loopThrough, 0x80);
	    /* Loop through attenuation */
        writeRegMask(0x1f, ltAtt, 0x80);
	    /* filter extension widest */
        writeRegMask(0x0f, fltExtWidest, 0x80);
	    /* RF poly filter current */
        writeRegMask(0x19, polyfilCur, 0x60);

	    /* Store current standard. If it changes, re-calibrate the tuner */
        mBw = bw;
    }

    private void sysFreqSel(long freq, DeliverySystem deliverySystem) throws DvbException {
        int mixerTop, lnaTop, cpCur, divBufCur;
        int lnaVthL, mixerVthL, airCable1In, cable2In, lnaDischarge, filterCur;

        switch (deliverySystem) {
            case DVBT:
                if ((freq == 506000000L) || (freq == 666000000L) ||
                        (freq == 818000000L)) {
                    mixerTop = 0x14;	/* mixer top:14 , top-1, low-discharge */
                    lnaTop = 0xe5;		/* detect bw 3, lna top:4, predet top:2 */
                    cpCur = 0x28;		/* 101, 0.2 */
                    divBufCur = 0x20;	/* 10, 200u */
                } else {
                    mixerTop = 0x24;	/* mixer top:13 , top-1, low-discharge */
                    lnaTop = 0xe5;		/* detect bw 3, lna top:4, predet top:2 */
                    cpCur = 0x38;		/* 111, auto */
                    divBufCur = 0x30;	/* 11, 150u */
                }
                lnaVthL = 0x53;		/* lna vth 0.84	,  vtl 0.64 */
                mixerVthL = 0x75;		/* mixer vth 1.04, vtl 0.84 */
                airCable1In = 0x00;
                cable2In = 0x00;
                lnaDischarge = 14;
                filterCur = 0x40;		/* 10, low */
                break;
            case DVBT2:
                mixerTop = 0x24;	/* mixer top:13 , top-1, low-discharge */
                lnaTop = 0xe5;		/* detect bw 3, lna top:4, predet top:2 */
                lnaVthL = 0x53;	/* lna vth 0.84	,  vtl 0.64 */
                mixerVthL = 0x75;	/* mixer vth 1.04, vtl 0.84 */
                airCable1In = 0x00;
                cable2In = 0x00;
                lnaDischarge = 14;
                cpCur = 0x38;		/* 111, auto */
                divBufCur = 0x30;	/* 11, 150u */
                filterCur = 0x40;	/* 10, low */
                break;
            case DVBC:
                mixerTop = 0x24;       /* mixer top:13 , top-1, low-discharge */
                lnaTop = 0xe5;
                lnaVthL = 0x62;
                mixerVthL = 0x75;
                airCable1In = 0x60;
                cable2In = 0x00;
                lnaDischarge = 14;
                cpCur = 0x38;          /* 111, auto */
                divBufCur = 0x30;     /* 11, 150u */
                filterCur = 0x40;      /* 10, low */
                break;
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }

        // Skipping diplexer since it doesn't seem to be happening for R820t
        // Skipping predetect since it doesn't seem to be happening for R820t

        writeRegMask(0x1d, lnaTop, 0xc7);
        writeRegMask(0x1c, mixerTop, 0xf8);
        writeReg(0x0d, lnaVthL);
        writeReg(0x0e, mixerVthL);

	    /* Air-IN only for Astrometa */
        writeRegMask(0x05, airCable1In, 0x60);
        writeRegMask(0x06, cable2In, 0x08);

        writeRegMask(0x11, cpCur, 0x38);
        writeRegMask(0x17, divBufCur, 0x30);
        writeRegMask(0x0a, filterCur, 0x60);

	    /*
	     * Original driver initializes regs 0x05 and 0x06 with the
	     * same value again on this point. Probably, it is just an
	     * error there
	     */

	    /*
	     * Set LNA
	     */

        /* LNA TOP: lowest */
        writeRegMask(0x1d, 0, 0x38);
        /* 0: normal mode */
        writeRegMask(0x1c, 0, 0x04);
        /* 0: PRE_DECT off */
        writeRegMask(0x06, 0, 0x40);
        /* agc clk 250hz */
        writeRegMask(0x1a, 0x30, 0x30);

        SleepUtils.mdelay(250);

        /* write LNA TOP = 3 */
        writeRegMask(0x1d, 0x18, 0x38);

        /*
		 * write discharge mode
		 * what's there at the original driver
		 */
        writeRegMask(0x1c, mixerTop, 0x04);
        /* LNA discharge current */
        writeRegMask(0x1e, lnaDischarge, 0x1f);
        /* agc clk 60hz */
        writeRegMask(0x1a, 0x20, 0x30);
    }

    private void genericSetFreq(long freq /* in Hz */, long bw, DeliverySystem deliverySystem) throws DvbException {
        setTvStandard(bw);

        long loFreq = freq + intFreq;

        setMux(loFreq);
        setPll(loFreq);
        if (!hasLock) throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.cannot_tune, freq / 1_000_000));
        sysFreqSel(freq, deliverySystem);
    }

    // API

    @Override
    public void init() throws DvbException {
        if (initDone) {
            Log.d(TAG, "Already initialized, no need to re-initialize");
            return;
        }
        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                imrCalibrate();
                initRegs();
            }
        });
    }

    @Override
    public void setParams(final long frequency, final long bandwidthHz, final DeliverySystem deliverySystem) throws DvbException {
        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                long bw = (bandwidthHz + 500_000L) / 1_000_000L;
                if (bw == 0) bw = 8;

                genericSetFreq(frequency, bw, deliverySystem);
            }
        });
    }

    @Override
    public void attatch() throws DvbException {
        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                byte[] data = new byte[5];
                read(0x00, data);
                standby();
            }
        });

        Log.d(TAG, "Rafael Micro r820t successfully identified");
    }

    @Override
    public void release() {
        try {
            i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
                @Override
                public void run() throws DvbException {
                    standby();
                }
            });
        } catch (DvbException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getIfFrequency() throws DvbException {
        return intFreq;
    }


    @Override
    public int readRfStrengthPercentage() throws DvbException {
        throw new UnsupportedOperationException();
    }

    // Helper classes

    private static class SectType {
        private int phaseY;
        private int gainX;
        private int value;

        private void copyFrom(SectType src) {
            phaseY = src.phaseY;
            gainX = src.gainX;
            value = src.value;
        }

        private static SectType[] newArray(int elements) {
            SectType[] array = new SectType[elements];
            for (int i = 0; i < array.length; i++) array[i] = new SectType();
            return array;
        }
    }
}
