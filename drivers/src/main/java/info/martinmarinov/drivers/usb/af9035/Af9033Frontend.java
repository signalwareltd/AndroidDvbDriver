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
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.tools.RegMap;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;

import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TS_MODE_PARALLEL;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TS_MODE_SERIAL;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TS_MODE_USB;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_38;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_51;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_52;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_60;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_61;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_62;

class Af9033Frontend implements DvbFrontend {
    private final static String TAG = Af9033Frontend.class.getSimpleName();

    private final Resources resources;
    private final Af9033Config config;
    private final RegMap regMap;


    private boolean ts_mode_parallel, ts_mode_serial;

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
            throw new DvbException(DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.usupported_clock_speed));
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
                reg = 0x004bfc;
                break;
            default:
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

    }

    @Override
    public void init(DvbTuner tuner) throws DvbException {

    }

    @Override
    public void setParams(long frequency, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {

    }

    @Override
    public int readSnr() throws DvbException {
        return 0;
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        return 0;
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

    }

    @Override
    public void disablePidFilter() throws DvbException {

    }
}
