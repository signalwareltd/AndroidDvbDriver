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

package info.martinmarinov.drivers.usb.af9035;

class Af9033Config {
    /*
     * clock Hz
     * 12000000, 22000000, 24000000, 34000000, 32000000, 28000000, 26000000,
     * 30000000, 36000000, 20480000, 16384000
     */
    final int clock;

    final boolean dyn0_clk;
    final boolean speck_inv;

    /*
     * ADC multiplier
     */
    static final int AF9033_ADC_MULTIPLIER_1X = 0;
    static final int AF9033_ADC_MULTIPLIER_2X = 1;
    final int adc_multiplier;

    /*
     * tuner
     */
    static final int AF9033_TUNER_TUA9001    = 0x27; /* Infineon TUA 9001 */
    static final int AF9033_TUNER_FC0011     = 0x28; /* Fitipower FC0011 */
    static final int AF9033_TUNER_FC0012     = 0x2e; /* Fitipower FC0012 */
    static final int AF9033_TUNER_MXL5007T   = 0xa0; /* MaxLinear MxL5007T */
    static final int AF9033_TUNER_TDA18218   = 0xa1; /* NXP TDA 18218HN */
    static final int AF9033_TUNER_FC2580     = 0x32; /* FCI FC2580 */
    /* 50-5f Omega */
    static final int AF9033_TUNER_IT9135_38  = 0x38; /* Omega */
    static final int AF9033_TUNER_IT9135_51  = 0x51; /* Omega LNA config 1 */
    static final int AF9033_TUNER_IT9135_52  = 0x52; /* Omega LNA config 2 */
    /* 60-6f Omega v2 */
    static final int AF9033_TUNER_IT9135_60  = 0x60; /* Omega v2 */
    static final int AF9033_TUNER_IT9135_61  = 0x61; /* Omega v2 LNA config 1 */
    static final int AF9033_TUNER_IT9135_62  = 0x62; /* Omega v2 LNA config 2 */
    final int tuner;

    /*
     * TS settings
     */
    static final int  AF9033_TS_MODE_USB       = 0;
    static final int  AF9033_TS_MODE_PARALLEL  = 1;
    static final int  AF9033_TS_MODE_SERIAL    = 2;
    final int ts_mode;

    Af9033Config(boolean dyn0_clk, int adc_multiplier, int tuner, int ts_mode, int clock, boolean speck_inv) {
        this.dyn0_clk = dyn0_clk;
        this.adc_multiplier = adc_multiplier;
        this.tuner = tuner;
        this.ts_mode = ts_mode;
        this.clock = clock;
        this.speck_inv = speck_inv;
    }
}
