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

package info.martinmarinov.drivers.usb.af9035;

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.DvbMath;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.tools.RegMap;
import info.martinmarinov.drivers.tools.SleepUtils;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.DvbException.ErrorCode.UNSUPPORTED_BANDWIDTH;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_ADC_MULTIPLIER_2X;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TS_MODE_PARALLEL;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TS_MODE_SERIAL;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TS_MODE_USB;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_FC0011;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_FC0012;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_FC2580;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_38;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_51;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_52;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_60;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_61;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_62;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_MXL5007T;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_TDA18218;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_TUA9001;

class Af9033Frontend implements DvbFrontend {
    private final static int PID_FILTER_COUNT = 32;
    private final static String TAG = Af9033Frontend.class.getSimpleName();

    private final Resources resources;
    private final Af9033Config config;
    private final RegMap regMap;

    private boolean ts_mode_parallel, ts_mode_serial;
    private boolean is_it9135, is_af9035;
    private long frequency, bandwidth_hz;
    private DvbTuner tuner;

    Af9033Frontend(Resources resources, Af9033Config config, int address, I2cAdapter i2CAdapter) {
        this.resources = resources;
        this.config = config;

        this.regMap = new RegMap(address, 24, i2CAdapter);
    }

    @Override
    public DvbCapabilities getCapabilities() {
        return Af9033Data.CAPABILITIES;
    }

    @Override
    public void attatch() throws DvbException {
        // probe

	    /* Setup the state */
        switch (config.ts_mode) {
            case AF9033_TS_MODE_PARALLEL:
                ts_mode_parallel = true;
                break;
            case AF9033_TS_MODE_SERIAL:
                ts_mode_serial = true;
                break;
            case AF9033_TS_MODE_USB:
		        /* USB mode for AF9035 */
            default:
                break;
        }

        if (config.clock != 12000000) {
            throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.usupported_clock_speed));
        }

	    /* Create regmap */

	    /* Firmware version */
	    int reg;
        switch (config.tuner) {
            case AF9033_TUNER_IT9135_38:
            case AF9033_TUNER_IT9135_51:
            case AF9033_TUNER_IT9135_52:
            case AF9033_TUNER_IT9135_60:
            case AF9033_TUNER_IT9135_61:
            case AF9033_TUNER_IT9135_62:
                is_it9135 = true;
                reg = 0x004bfc;
                break;
            default:
                is_af9035 = true;
                reg = 0x0083e9;
                break;
        }

        byte[] buf = new byte[8];
        regMap.read_regs(reg, buf, 0, 4);
        regMap.read_regs(0x804191, buf, 4, 4);

        Log.d(TAG, String.format("firmware version: LINK %d.%d.%d.%d - OFDM %d.%d.%d.%d",
                buf[0], buf[1], buf[2], buf[3],
                buf[4], buf[5], buf[6], buf[7]));

	    /* Sleep as chip seems to be partly active by default */
        switch (config.tuner) {
            case AF9033_TUNER_IT9135_38:
            case AF9033_TUNER_IT9135_51:
            case AF9033_TUNER_IT9135_52:
            case AF9033_TUNER_IT9135_60:
            case AF9033_TUNER_IT9135_61:
            case AF9033_TUNER_IT9135_62:
		        /* IT9135 did not like to sleep at that early */
                break;
            default:
                regMap.write_reg(0x80004c, 0x01);
                regMap.write_reg(0x800000, 0x00);
        }
    }

    @Override
    public void release() {
        // sleep
        try {
            regMap.write_reg(0x80004c, 0x01);
            regMap.write_reg(0x800000, 0x00);

            // regmap_read_poll_timeout
            for (int i = 100, tmp = 1; i > 0 && tmp != 0; i--) {
                tmp = regMap.read_reg(0x80004c);
                SleepUtils.usleep(10_000);
            }

            regMap.update_bits(0x80fb24, 0x08, 0x08);

	        /* Prevent current leak by setting TS interface to parallel mode */
            if (config.ts_mode == AF9033_TS_MODE_SERIAL) {
		        /* Enable parallel TS */
                regMap.update_bits(0x00d917, 0x01, 0x00);
                regMap.update_bits(0x00d916, 0x01, 0x01);
            }
        } catch (DvbException e) {
            e.printStackTrace();
        }
    }

    /* Write reg val table using reg addr auto increment */
    private void wr_reg_val_tab(int[][] tab) throws DvbException {
        // tab[i][0] is reg
        // tab[i][1] is val

        final int MAX_TAB_LEN =  212;

        if (tab.length > MAX_TAB_LEN) {
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
        }

        byte[] buf = new byte[1 + MAX_TAB_LEN];
        for (int i = 0, j = 0; i < tab.length; i++) {
            buf[j] = (byte) tab[i][1];

            if (i == tab.length - 1 || tab[i][0] != tab[i + 1][0] - 1) {
                regMap.bulk_write(tab[i][0] - j, buf, j + 1);
                j = 0;
            } else {
                j++;
            }
        }
    }

    @Override
    public void init(DvbTuner tuner) throws DvbException {
        this.tuner = tuner;

        /* Main clk control */
        long utmp = DvbMath.divU64(config.clock * 0x80000L, 1_000_000L);

        byte[] buf = new byte[4];
        buf[0] = (byte) (utmp);
        buf[1] = (byte) (utmp >>  8);
        buf[2] = (byte) (utmp >> 16);
        buf[3] = (byte) (utmp >> 24);
        regMap.bulk_write(0x800025, buf);

	    /* ADC clk control */
	    int i;
        for (i = 0; i < Af9033Data.clock_adc_lut.length; i++) {
            if (Af9033Data.clock_adc_lut[i][0] == config.clock)
                break;
        }
        if (i == Af9033Data.clock_adc_lut.length) {
            Log.e(TAG, "Couldn't find ADC config for clock " + config.clock);
		    throw new DvbException(DvbException.ErrorCode.HARDWARE_EXCEPTION, resources.getString(R.string.failed_callibration_step));
        }

        utmp = DvbMath.divU64((long) Af9033Data.clock_adc_lut[i][1] * 0x80000L, 1_000_000L);
        buf[0] = (byte) (utmp);
        buf[1] = (byte) (utmp >>  8);
        buf[2] = (byte) (utmp >> 16);
        regMap.bulk_write(0x80f1cd, buf, 3);

	    /* Config register table */
	    int[][] tab = Af9033Data.reg_val_mask_tab(config.tuner, ts_mode_serial, ts_mode_parallel, config.adc_multiplier);
        for (i = 0; i < tab.length; i++) {
            regMap.update_bits(tab[i][0] /* reg */, tab[i][2] /* mask */, tab[i][1] /* val */);
        }

	    /* Demod clk output */
        if (config.dyn0_clk) {
            regMap.write_reg(0x80fba8, 0x00);
        }

	    /* TS interface */
        if (config.ts_mode == AF9033_TS_MODE_USB) {
            regMap.update_bits(0x80f9a5, 0x01, 0x00);
            regMap.update_bits(0x80f9b5, 0x01, 0x01);
        } else {
            regMap.update_bits(0x80f990, 0x01, 0x00);
            regMap.update_bits(0x80f9b5, 0x01, 0x00);
        }

	    /* Demod core settings */
	    int[][] init;
        switch (config.tuner) {
            case AF9033_TUNER_IT9135_38:
            case AF9033_TUNER_IT9135_51:
            case AF9033_TUNER_IT9135_52:
                init = Af9033Data.ofsm_init_it9135_v1;
                break;
            case AF9033_TUNER_IT9135_60:
            case AF9033_TUNER_IT9135_61:
            case AF9033_TUNER_IT9135_62:
                init = Af9033Data.ofsm_init_it9135_v2;
                break;
            default:
                init = Af9033Data.ofsm_init;
                break;
        }

        wr_reg_val_tab(init);

	    /* Demod tuner specific settings */
        switch (config.tuner) {
            case AF9033_TUNER_TUA9001:
                init = Af9033Data.tuner_init_tua9001;
                break;
            case AF9033_TUNER_FC0011:
                init = Af9033Data.tuner_init_fc0011;
                break;
            case AF9033_TUNER_MXL5007T:
                init = Af9033Data.tuner_init_mxl5007t;
                break;
            case AF9033_TUNER_TDA18218:
                init = Af9033Data.tuner_init_tda18218;
                break;
            case AF9033_TUNER_FC2580:
                init = Af9033Data.tuner_init_fc2580;
                break;
            case AF9033_TUNER_FC0012:
                init = Af9033Data.tuner_init_fc0012;
                break;
            case AF9033_TUNER_IT9135_38:
                init = Af9033Data.tuner_init_it9135_38;
                break;
            case AF9033_TUNER_IT9135_51:
                init = Af9033Data.tuner_init_it9135_51;
                break;
            case AF9033_TUNER_IT9135_52:
                init = Af9033Data.tuner_init_it9135_52;
                break;
            case AF9033_TUNER_IT9135_60:
                init = Af9033Data.tuner_init_it9135_60;
                break;
            case AF9033_TUNER_IT9135_61:
                init = Af9033Data.tuner_init_it9135_61;
                break;
            case AF9033_TUNER_IT9135_62:
                init = Af9033Data.tuner_init_it9135_62;
                break;
            default:
		        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_tuner_on_device));
        }

        wr_reg_val_tab(init);

        if (config.ts_mode == AF9033_TS_MODE_SERIAL) {
            regMap.update_bits(0x00d91c, 0x01, 0x01);
            regMap.update_bits(0x00d917, 0x01, 0x00);
            regMap.update_bits(0x00d916, 0x01, 0x00);
        }

        switch (config.tuner) {
            case AF9033_TUNER_IT9135_60:
            case AF9033_TUNER_IT9135_61:
            case AF9033_TUNER_IT9135_62:
                regMap.write_reg(0x800000, 0x01);
        }

        bandwidth_hz = 0; /* Force to program all parameters */
    }

    @Override
    public void setParams(long frequency, long bandwidth_hz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        int bandwidth_reg_val;

        /* Check bandwidth */
        switch ((int) bandwidth_hz) {
            case 6_000_000:
                bandwidth_reg_val = 0x00;
                break;
            case 7_000_000:
                bandwidth_reg_val = 0x01;
                break;
            case 8_000_000:
                bandwidth_reg_val = 0x02;
                break;
            default:
                throw new DvbException(UNSUPPORTED_BANDWIDTH, resources.getString(R.string.invalid_bw));
        }

	    /* Program tuner */
        tuner.setParams(frequency, bandwidth_hz, deliverySystem);

	    /* Coefficients */
        if (bandwidth_hz != this.bandwidth_hz) {
            if (config.clock != 12_000_000) {
                throw new IllegalStateException("Clock that are not 12 MHz should have been filtered already!");
            }

            switch ((int) bandwidth_hz) {
                case 6_000_000:
                    regMap.bulk_write(0x800001, Af9033Data.coeff_lut_12000000_6000000);
                    break;
                case 7_000_000:
                    regMap.bulk_write(0x800001, Af9033Data.coeff_lut_12000000_7000000);
                    break;
                case 8_000_000:
                    regMap.bulk_write(0x800001, Af9033Data.coeff_lut_12000000_8000000);
                    break;
                default:
                    throw new IllegalStateException("Invalid bandwidth that was already checked");
            }
        }

	    /* IF frequency control */
        if (bandwidth_hz != this.bandwidth_hz) {
            int i;
            for (i = 0; i < Af9033Data.clock_adc_lut.length; i++) {
                if (Af9033Data.clock_adc_lut[i][0] == config.clock)
                    break;
            }
            if (i == Af9033Data.clock_adc_lut.length) {
                throw new DvbException(DvbException.ErrorCode.HARDWARE_EXCEPTION, resources.getString(R.string.failed_callibration_step));
            }
            int adc_freq = Af9033Data.clock_adc_lut[i][1];

            if (config.adc_multiplier == AF9033_ADC_MULTIPLIER_2X) {
                adc_freq = 2 * adc_freq;
            }

		    /* Get used IF frequency */
		    long if_frequency = tuner.getIfFrequency();

            long utmp = DvbMath.divRoundClosest(if_frequency * 0x800000L, adc_freq);

            if (!config.speck_inv && if_frequency > 0) {
                utmp = 0x800000 - utmp;
            }

            byte[] buf = new byte[3];
            buf[0] = (byte) (utmp);
            buf[1] = (byte) (utmp >>  8);
            buf[2] = (byte) (utmp >> 16);
            regMap.bulk_write(0x800029, buf, 3);

            this.bandwidth_hz = bandwidth_hz;
        }

        regMap.update_bits(0x80f904, 0x03, bandwidth_reg_val);
        regMap.write_reg(0x800040, 0x00);
        regMap.write_reg(0x800047, 0x00);
        regMap.update_bits(0x80f999, 0x01, 0x00);

        int tmp;
        if (frequency <= 230_000_000L) {
            tmp = 0x00; /* VHF */
        } else {
            tmp = 0x01; /* UHF */
        }

        regMap.write_reg(0x80004b, tmp);
	    /* Reset FSM */
        regMap.write_reg(0x800000, 0x00);
    }

    @Override
    public int readSnr() throws DvbException {
        return 0;
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        if (is_af9035) {
		    /* Read signal strength of 0-100 scale */
            return regMap.read_reg(0x800048);
        } else {
            int utmp = regMap.read_reg(0x8000f7);

            byte[] buf = new byte[7];
            regMap.read_regs(0x80f900, buf, 0, 7);

            int gain_offset;
            if (frequency <= 300_000_000) {
                gain_offset = 7; /* VHF */
            } else {
                gain_offset = 4; /* UHF */
            }

            int power_real = (utmp - 100 - gain_offset) -
                    Af9033Data.power_reference[((buf[3] & 0xFF) & 3)][((buf[6] & 0xFF) & 7)];

            if (power_real < -15) {
                return 0;
            } else if ((power_real >= -15) && (power_real < 0)) {
                return (2 * (power_real + 15)) / 3;
            } else if ((power_real >= 0) && (power_real < 20)) {
                return 4 * power_real + 10;
            } else if ((power_real >= 20) && (power_real < 35)) {
                return (2 * (power_real - 20)) / 3 + 90;
            } else {
                return 100;
            }
        }
    }

    @Override
    public int readBer() throws DvbException {
        return 0;
    }

    @Override
    public Set<DvbStatus> getStatus() throws DvbException {
        return null;
    }

    @Override
    public void setPids(int... pids) throws DvbException {
        pid_filter_ctrl(true);

        for (int i = 0; i < pids.length; i++) {
            pid_filter(i, pids[i], true);
        }

        for (int i = pids.length; i < PID_FILTER_COUNT; i++) {
            pid_filter(i, pids[i], false);
        }
    }

    @Override
    public void disablePidFilter() throws DvbException {
        pid_filter_ctrl(false);
    }

    private void pid_filter_ctrl(boolean enable) throws DvbException {
        regMap.update_bits(0x80f993, 0x01, enable ? 1 : 0);
    }

    private void pid_filter(int index, int pid, boolean onoff) throws DvbException {
        if (pid > 0x1fff) return;

        regMap.bulk_write(0x80f996, new byte[] {(byte) pid, (byte) (pid >> 8)});
        regMap.write_reg(0x80f994, onoff ? 1 : 0);
        regMap.write_reg(0x80f995, index);
    }
}
