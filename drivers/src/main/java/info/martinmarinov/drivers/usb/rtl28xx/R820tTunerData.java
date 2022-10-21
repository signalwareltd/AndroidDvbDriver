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

import android.util.Pair;

import java.util.Arrays;
import java.util.List;

import info.martinmarinov.drivers.usb.rtl28xx.R820tTuner.XtalCapValue;

import static info.martinmarinov.drivers.usb.rtl28xx.R820tTuner.XtalCapValue.XTAL_HIGH_CAP_0P;
import static info.martinmarinov.drivers.usb.rtl28xx.R820tTuner.XtalCapValue.XTAL_LOW_CAP_0P;
import static info.martinmarinov.drivers.usb.rtl28xx.R820tTuner.XtalCapValue.XTAL_LOW_CAP_10P;
import static info.martinmarinov.drivers.usb.rtl28xx.R820tTuner.XtalCapValue.XTAL_LOW_CAP_20P;
import static info.martinmarinov.drivers.usb.rtl28xx.R820tTuner.XtalCapValue.XTAL_LOW_CAP_30P;

class R820tTunerData {
    static final List<Pair<Integer, XtalCapValue>> XTAL_CAPS = Arrays.asList(
            new Pair<>(0x0b, XTAL_LOW_CAP_30P),
            new Pair<>(0x02, XTAL_LOW_CAP_20P),
            new Pair<>(0x01, XTAL_LOW_CAP_10P),
            new Pair<>(0x00, XTAL_LOW_CAP_0P),
            new Pair<>(0x10, XTAL_HIGH_CAP_0P)
    );

    static final byte[] INIT_REGS = new byte[] {
            (byte) 0x83, (byte) 0x32, (byte) 0x75,			/* 05 to 07 */
            (byte) 0xc0, (byte) 0x40, (byte) 0xd6, (byte) 0x6c,			/* 08 to 0b */
            (byte) 0xf5, (byte) 0x63, (byte) 0x75, (byte) 0x68,			/* 0c to 0f */
            (byte) 0x6c, (byte) 0x83, (byte) 0x80, (byte) 0x00,			/* 10 to 13 */
            (byte) 0x0f, (byte) 0x00, (byte) 0xc0, (byte) 0x30,			/* 14 to 17 */
            (byte) 0x48, (byte) 0xcc, (byte) 0x60, (byte) 0x00,			/* 18 to 1b */
            (byte) 0x54, (byte) 0xae, (byte) 0x4a, (byte) 0xc0			/* 1c to 1f */
    };

    static final FreqRange[] FREQ_RANGES = new FreqRange[]{
            new FreqRange(
                    0,
                    0x08,		/* low */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0xdf,		/* R27[7:0]  band2,band0 */
                    0x02,	/* R16[1:0]  20pF (10)   */
                    0x01,
                    0x00,
                    0),
            new FreqRange(
                    50,		/* Start freq, in MHz */
                    0x08,		/* low */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0xbe,		/* R27[7:0]  band4,band1  */
                    0x02,	/* R16[1:0]  20pF (10)   */
                    0x01,
                    0x00,
                    0),
            new FreqRange(
                    55,		/* Start freq, in MHz */
                    0x08,		/* low */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x8b,		/* R27[7:0]  band7,band4 */
                    0x02,	/* R16[1:0]  20pF (10)   */
                    0x01,
                    0x00,
                    0),
            new FreqRange(
                    60,		/* Start freq, in MHz */
                    0x08,		/* low */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x7b,		/* R27[7:0]  band8,band4 */
                    0x02,	/* R16[1:0]  20pF (10)   */
                    0x01,
                    0x00,
                    0),
            new FreqRange(
                    65,		/* Start freq, in MHz */
                    0x08,		/* low */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x69,		/* R27[7:0]  band9,band6 */
                    0x02,	/* R16[1:0]  20pF (10)   */
                    0x01,
                    0x00,
                    0),
            new FreqRange(
                    70,		/* Start freq, in MHz */
                    0x08,		/* low */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x58,		/* R27[7:0]  band10,band7 */
                    0x02,	/* R16[1:0]  20pF (10)   */
                    0x01,
                    0x00,
                    0),
            new FreqRange(
                    75,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x44,		/* R27[7:0]  band11,band11 */
                    0x02,	/* R16[1:0]  20pF (10)   */
                    0x01,
                    0x00,
                    0),
            new FreqRange(
                    80,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x44,		/* R27[7:0]  band11,band11 */
                    0x02,	/* R16[1:0]  20pF (10)   */
                    0x01,
                    0x00,
                    0),
            new FreqRange(
                    90,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x34,		/* R27[7:0]  band12,band11 */
                    0x01,	/* R16[1:0]  10pF (01)   */
                    0x01,
                    0x00,
                    0),
            new FreqRange(
                    100,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x34,		/* R27[7:0]  band12,band11 */
                    0x01,	/* R16[1:0]  10pF (01)    */
                    0x01,
                    0x00,
                    0),
            new FreqRange(
                    110,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x24,		/* R27[7:0]  band13,band11 */
                    0x01,	/* R16[1:0]  10pF (01)   */
                    0x01,
                    0x00,
                    1),
            new FreqRange(
                    120,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x24,		/* R27[7:0]  band13,band11 */
                    0x01,	/* R16[1:0]  10pF (01)   */
                    0x01,
                    0x00,
                    1),
            new FreqRange(
                    140,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x14,		/* R27[7:0]  band14,band11 */
                    0x01,	/* R16[1:0]  10pF (01)   */
                    0x01,
                    0x00,
                    1),
            new FreqRange(
                    180,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x13,		/* R27[7:0]  band14,band12 */
                    0x00,	/* R16[1:0]  0pF (00)   */
                    0x00,
                    0x00,
                    1),
            new FreqRange(
                    220,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x13,		/* R27[7:0]  band14,band12 */
                    0x00,	/* R16[1:0]  0pF (00)   */
                    0x00,
                    0x00,
                    2),
            new FreqRange(
                    250,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x11,		/* R27[7:0]  highest,highest */
                    0x00,	/* R16[1:0]  0pF (00)   */
                    0x00,
                    0x00,
                    2),
            new FreqRange(
                    280,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x02,	/* R26[7:6]=0 (LPF)  R26[1:0]=2 (low) */
                    0x00,		/* R27[7:0]  highest,highest */
                    0x00,	/* R16[1:0]  0pF (00)   */
                    0x00,
                    0x00,
                    2),
            new FreqRange(
                    310,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x41,	/* R26[7:6]=1 (bypass)  R26[1:0]=1 (middle) */
                    0x00,		/* R27[7:0]  highest,highest */
                    0x00,	/* R16[1:0]  0pF (00)   */
                    0x00,
                    0x00,
                    2),
            new FreqRange(
                    450,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x41,	/* R26[7:6]=1 (bypass)  R26[1:0]=1 (middle) */
                    0x00,		/* R27[7:0]  highest,highest */
                    0x00,	/* R16[1:0]  0pF (00)   */
                    0x00,
                    0x00,
                    3),
            new FreqRange(
                    588,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x40,	/* R26[7:6]=1 (bypass)  R26[1:0]=0 (highest) */
                    0x00,		/* R27[7:0]  highest,highest */
                    0x00,	/* R16[1:0]  0pF (00)   */
                    0x00,
                    0x00,
                    3),
            new FreqRange(
                    650,		/* Start freq, in MHz */
                    0x00,		/* high */
                    0x40,	/* R26[7:6]=1 (bypass)  R26[1:0]=0 (highest) */
                    0x00,		/* R27[7:0]  highest,highest */
                    0x00,	/* R16[1:0]  0pF (00)   */
                    0x00,
                    0x00,
                    4)
    };

    static class FreqRange {
        final long freq;
        final int openD;
        final int rfMuxPloy;
        final int tfC;
        final int xtalCap20p;
        final int xtalCap10p;
        final int xtalCap0p;
        final int imrMem;

        FreqRange(long freq, int openD, int rfMuxPloy, int tfC, int xtalCap20p, int xtalCap10p, int xtalCap0p, int imrMem) {
            this.freq = freq;
            this.openD = openD;
            this.rfMuxPloy = rfMuxPloy;
            this.tfC = tfC;
            this.xtalCap20p = xtalCap20p;
            this.xtalCap10p = xtalCap10p;
            this.xtalCap0p = xtalCap0p;
            this.imrMem = imrMem;
        }
    }
}
