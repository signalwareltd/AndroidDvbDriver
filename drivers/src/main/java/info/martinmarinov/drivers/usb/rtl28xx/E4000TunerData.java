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

class E4000TunerData {
    // PLL
    final static E4000Pll[] E4000_PLL_LUT = new E4000Pll[] {
            new E4000Pll(   72_400_000L, 0x0f, 48 ), /* .......... 3475200000 */
            new E4000Pll(   81_200_000L, 0x0e, 40 ), /* 2896000000 3248000000 */
            new E4000Pll(  108_300_000L, 0x0d, 32 ), /* 2598400000 3465600000 */
            new E4000Pll(  162_500_000L, 0x0c, 24 ), /* 2599200000 3900000000 */
            new E4000Pll(  216_600_000L, 0x0b, 16 ), /* 2600000000 3465600000 */
            new E4000Pll(  325_000_000L, 0x0a, 12 ), /* 2599200000 3900000000 */
            new E4000Pll(  350_000_000L, 0x09,  8 ), /* 2600000000 2800000000 */
            new E4000Pll(  432_000_000L, 0x03,  8 ), /* 2800000000 3456000000 */
            new E4000Pll(  667_000_000L, 0x02,  6 ), /* 2592000000 4002000000 */
            new E4000Pll(1_200_000_000L, 0x01,  4 ), /* 2668000000 4800000000 */
            new E4000Pll(   0xffffffffL, 0x00,  2 )  /* 2400000000 .......... */
    };

    static class E4000Pll {
        final long freq;
        final int div;
        final int mul;

        private E4000Pll(long freq, int div, int mul) {
            this.freq = freq;
            this.div = div;
            this.mul = mul;
        }
    }

    // LNA
    final static E4000Lna[] E4000_LNA_LUT = new E4000Lna[] {
            new E4000Lna(   370_000_000L,  0 ),
            new E4000Lna(   392_500_000L,  1 ),
            new E4000Lna(   415_000_000L,  2 ),
            new E4000Lna(   437_500_000L,  3 ),
            new E4000Lna(   462_500_000L,  4 ),
            new E4000Lna(   490_000_000L,  5 ),
            new E4000Lna(   522_500_000L,  6 ),
            new E4000Lna(   557_500_000L,  7 ),
            new E4000Lna(   595_000_000L,  8 ),
            new E4000Lna(   642_500_000L,  9 ),
            new E4000Lna(   695_000_000L, 10 ),
            new E4000Lna(   740_000_000L, 11 ),
            new E4000Lna(   800_000_000L, 12 ),
            new E4000Lna(   865_000_000L, 13 ),
            new E4000Lna(   930_000_000L, 14 ),
            new E4000Lna( 1_000_000_000L, 15 ),
            new E4000Lna( 1_310_000_000L,  0 ),
            new E4000Lna( 1_340_000_000L,  1 ),
            new E4000Lna( 1_385_000_000L,  2 ),
            new E4000Lna( 1_427_500_000L,  3 ),
            new E4000Lna( 1_452_500_000L,  4 ),
            new E4000Lna( 1_475_000_000L,  5 ),
            new E4000Lna( 1_510_000_000L,  6 ),
            new E4000Lna( 1_545_000_000L,  7 ),
            new E4000Lna( 1_575_000_000L,  8 ),
            new E4000Lna( 1_615_000_000L,  9 ),
            new E4000Lna( 1_650_000_000L, 10 ),
            new E4000Lna( 1_670_000_000L, 11 ),
            new E4000Lna( 1_690_000_000L, 12 ),
            new E4000Lna( 1_710_000_000L, 13 ),
            new E4000Lna( 1_735_000_000L, 14 ),
            new E4000Lna(    0xffffffffL, 15 )
    };

    static class E4000Lna {
        final long freq;
        final int val;


        E4000Lna(long freq, int val) {
            this.freq = freq;
            this.val = val;
        }
    }

    // IF
    final static E4000If[] E4000_IF_LUT = new E4000If[] {
            new E4000If(    4_300_000L, 0xfd, 0x1f ),
            new E4000If(    4_400_000L, 0xfd, 0x1e ),
            new E4000If(    4_480_000L, 0xfc, 0x1d ),
            new E4000If(    4_560_000L, 0xfc, 0x1c ),
            new E4000If(    4_600_000L, 0xfc, 0x1b ),
            new E4000If(    4_800_000L, 0xfc, 0x1a ),
            new E4000If(    4_900_000L, 0xfc, 0x19 ),
            new E4000If(    5_000_000L, 0xfc, 0x18 ),
            new E4000If(    5_100_000L, 0xfc, 0x17 ),
            new E4000If(    5_200_000L, 0xfc, 0x16 ),
            new E4000If(    5_400_000L, 0xfc, 0x15 ),
            new E4000If(    5_500_000L, 0xfc, 0x14 ),
            new E4000If(    5_600_000L, 0xfc, 0x13 ),
            new E4000If(    5_800_000L, 0xfb, 0x12 ),
            new E4000If(    5_900_000L, 0xfb, 0x11 ),
            new E4000If(    6_000_000L, 0xfb, 0x10 ),
            new E4000If(    6_200_000L, 0xfb, 0x0f ),
            new E4000If(    6_400_000L, 0xfa, 0x0e ),
            new E4000If(    6_600_000L, 0xfa, 0x0d ),
            new E4000If(    6_800_000L, 0xf9, 0x0c ),
            new E4000If(    7_200_000L, 0xf9, 0x0b ),
            new E4000If(    7_400_000L, 0xf9, 0x0a ),
            new E4000If(    7_600_000L, 0xf8, 0x09 ),
            new E4000If(    7_800_000L, 0xf8, 0x08 ),
            new E4000If(    8_200_000L, 0xf8, 0x07 ),
            new E4000If(    8_600_000L, 0xf7, 0x06 ),
            new E4000If(    8_800_000L, 0xf7, 0x05 ),
            new E4000If(    9_200_000L, 0xf7, 0x04 ),
            new E4000If(    9_600_000L, 0xf6, 0x03 ),
            new E4000If(   10_000_000L, 0xf6, 0x02 ),
            new E4000If(   10_600_000L, 0xf5, 0x01 ),
            new E4000If(   11_000_000L, 0xf5, 0x00 ),
            new E4000If(   0xffffffffL, 0x00, 0x20 )
    };


    static class E4000If {
        final long freq;
        final int reg11val, reg12val;

        E4000If(long freq, int reg11val, int reg12val) {
            this.freq = freq;
            this.reg11val = reg11val;
            this.reg12val = reg12val;
        }
    }

    // Bands
    final static E4000band[] E4000_FREQ_BANDS = new E4000band[] {
            new E4000band(   140_000_000L, 0x01, 0x03 ),
            new E4000band(   350_000_000L, 0x03, 0x03 ),
            new E4000band( 1_000_000_000L, 0x05, 0x03 ),
            new E4000band(    0xffffffffL, 0x07, 0x00 )
    };

    static class E4000band {
        final long freq;
        final int reg07val, reg78val;

        E4000band(long freq, int reg07val, int reg78val) {
            this.freq = freq;
            this.reg07val = reg07val;
            this.reg78val = reg78val;
        }
    }
}
