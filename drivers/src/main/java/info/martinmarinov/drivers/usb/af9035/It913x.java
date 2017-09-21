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

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.RegMap;
import info.martinmarinov.drivers.tools.SleepUtils;
import info.martinmarinov.drivers.usb.DvbTuner;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;

class It913x implements DvbTuner {
    final static int IT9133AX_TUNER = 0;
    final static int IT9133BX_TUNER = 1;

    final static int IT913X_ROLE_SINGLE = 0;
    final static int IT913X_ROLE_DUAL_MASTER = 1;
    // final static int IT913X_ROLE_DUAL_SLAVE = 2;

    private final static int TIMEOUT = 50;
    private final static int[] nv = new int[] {48, 32, 24, 16, 12, 8, 6, 4, 2};

    private final Resources resources;
    private final RegMap regMap;
    private final int chip_ver;
    private final int role;

    private boolean active = false;
    private int clk_mode, xtal, fdiv, fn_min;

    It913x(Resources resources, RegMap regMap, int chip_ver, int role) {
        this.resources = resources;
        this.regMap = regMap;
        this.chip_ver = chip_ver;
        this.role = role;
    }

    @Override
    public void attatch() throws DvbException {
        // no-op
    }

    @Override
    public synchronized void release() {
        // sleep
        active = false;

        try {
            regMap.bulk_write(0x80ec40, new byte[]{0x00});

            /*
             * Writing '0x00' to master tuner register '0x80ec08' causes slave tuner
             * communication lost. Due to that, we cannot put master full sleep.
             */
            if (role == IT913X_ROLE_DUAL_MASTER) {
                regMap.bulk_write(0x80ec02, new byte[]{0x3f,0x1f,0x3f,0x3e});
            } else {
                regMap.bulk_write(0x80ec02, new byte[]{0x3f,0x1f,0x3f,0x3e,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
            }

            regMap.bulk_write(0x80ec12, new byte[]{0x00,0x00,0x00,0x00});
            regMap.bulk_write(0x80ec17, new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
            regMap.bulk_write(0x80ec22, new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
            regMap.bulk_write(0x80ec20, new byte[]{0x00});
            regMap.bulk_write(0x80ec3f, new byte[]{0x01});

        } catch (DvbException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void init() throws DvbException {
        regMap.write_reg(0x80ec4c, 0x68);

        SleepUtils.usleep(100_000L);

        int utmp = regMap.read_reg(0x80ec86);
        int iqik_m_cal;
        switch (utmp) {
            case 0:
		        /* 12.000 MHz */
                clk_mode = utmp;
                xtal = 2000;
                fdiv = 3;
                iqik_m_cal = 16;
                break;
            case 1:
		        /* 20.480 MHz */
                clk_mode = utmp;
                xtal = 640;
                fdiv = 1;
                iqik_m_cal = 6;
                break;
            default:
                throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.usupported_clock_speed));
        }

        utmp = regMap.read_reg(0x80ed03);

        int nv_val;
        if (utmp < nv.length) {
            nv_val = nv[utmp];
        } else {
            nv_val = 2;
        }

        byte[] buf = new byte[2];
        for (int i = 0; i < 10; i++) {
            SleepUtils.mdelay(TIMEOUT / 10);
            regMap.read_regs(0x80ed23, buf, 0, 2);

            utmp = ((buf[1] & 0xFF) << 8) | (buf[0] & 0xFF);
            if (utmp != 0) break;
        }

        fn_min = xtal * utmp;
        fn_min /= (fdiv * nv_val);
        fn_min *= 1000;

        /*
         * Chip version BX never sets that flag so we just wait 50ms in that
         * case. It is possible poll BX similarly than AX and then timeout in
         * order to get 50ms delay, but that causes about 120 extra I2C
         * messages. As for now, we just wait and reduce IO.
         */
        if (chip_ver == 1) {
            for (int i = 0; i < 10; i++) {
                SleepUtils.mdelay(TIMEOUT / 10);
                utmp = regMap.read_reg(0x80ec82);
                if (utmp != 0) break;
            }
        } else {
            SleepUtils.mdelay(TIMEOUT);
        }

        regMap.write_reg(0x80ed81, iqik_m_cal);
        regMap.write_reg(0x80ec57, 0x00);
        regMap.write_reg(0x80ec58, 0x00);
        regMap.write_reg(0x80ec40, 0x01);

        active = true;
    }

    @Override
    public void setParams(long frequency, long bandwidth_hz, DeliverySystem deliverySystem) throws DvbException {
        if (!active) {
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
        }
        if (deliverySystem != DeliverySystem.DVBT) {
            throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }

        int n, n_div;
        if (frequency <=         74_000_000L) {
            n_div = 48;
            n = 0;
        } else if (frequency <= 111_000_000L) {
            n_div = 32;
            n = 1;
        } else if (frequency <= 148_000_000L) {
            n_div = 24;
            n = 2;
        } else if (frequency <= 222_000_000L) {
            n_div = 16;
            n = 3;
        } else if (frequency <= 296_000_000L) {
            n_div = 12;
            n = 4;
        } else if (frequency <= 445_000_000L) {
            n_div = 8;
            n = 5;
        } else if (frequency <= fn_min) {
            n_div = 6;
            n = 6;
        } else if (frequency <= 950_000_000L) {
            n_div = 4;
            n = 7;
        } else {
            n_div = 2;
            n = 0;
        }

        long utmp = regMap.read_reg(0x80ed81);

        long iqik_m_cal = utmp * n_div;

        if (utmp < 0x20) {
            if (clk_mode == 0) {
                iqik_m_cal = (iqik_m_cal * 9) >> 5;
            } else {
                iqik_m_cal >>= 1;
            }
        } else {
            iqik_m_cal = 0x40 - iqik_m_cal;
            if (clk_mode == 0) {
                iqik_m_cal = ~((iqik_m_cal * 9) >> 5);
            } else {
                iqik_m_cal = ~(iqik_m_cal >> 1);
            }
        }

        long t_cal_freq = (frequency / 1_000L) * n_div * fdiv;
        long pre_lo_freq = t_cal_freq / xtal;
        utmp = pre_lo_freq * xtal;

        if ((t_cal_freq - utmp) >= (xtal >> 1)) {
            pre_lo_freq++;
        }

        pre_lo_freq += ((long) n) << 13;
	    /* Frequency OMEGA_IQIK_M_CAL_MID*/
        t_cal_freq = pre_lo_freq + iqik_m_cal;

        int l_band, lna_band;
        if (frequency <=         440_000_000L) {
            l_band = 0;
            lna_band = 0;
        } else if (frequency <=  484_000_000L) {
            l_band = 1;
            lna_band = 1;
        } else if (frequency <=  533_000_000L) {
            l_band = 1;
            lna_band = 2;
        } else if (frequency <=  587_000_000L) {
            l_band = 1;
            lna_band = 3;
        } else if (frequency <=  645_000_000L) {
            l_band = 1;
            lna_band = 4;
        } else if (frequency <=  710_000_000L) {
            l_band = 1;
            lna_band = 5;
        } else if (frequency <=  782_000_000L) {
            l_band = 1;
            lna_band = 6;
        } else if (frequency <=  860_000_000L) {
            l_band = 1;
            lna_band = 7;
        } else if (frequency <= 1_492_000_000L) {
            l_band = 1;
            lna_band = 0;
        } else if (frequency <= 1_685_000_000L) {
            l_band = 1;
            lna_band = 1;
        } else {
            throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.cannot_tune, frequency / 1_000_000));
        }

	    /* XXX: latest windows driver does not set that at all */
        regMap.write_reg(0x80ee06, lna_band);

        int u8tmp;
        if (bandwidth_hz <=        5_000_000L) {
            u8tmp = 0;
        } else if (bandwidth_hz <= 6_000_000L) {
            u8tmp = 2;
        } else if (bandwidth_hz <= 7_000_000L) {
            u8tmp = 4;
        } else {
            u8tmp = 6;       /* 8_000_000 */
        }

        regMap.write_reg(0x80ec56, u8tmp);

	    /* XXX: latest windows driver sets different value (a8 != 68) */
        regMap.write_reg(0x80ec4c, 0xa0 | (l_band << 3));
        regMap.write_reg(0x80ec4d, (int) (t_cal_freq & 0xff));
        regMap.write_reg(0x80ec4e, (int) ((t_cal_freq >> 8) & 0xff));
        regMap.write_reg(0x80011e, (int) (pre_lo_freq & 0xff));
        regMap.write_reg(0x80011f, (int) ((pre_lo_freq >> 8) & 0xf));
    }

    @Override
    public long getIfFrequency() throws DvbException {
        return 0;
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        return 0;
    }
}
