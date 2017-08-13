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

package info.martinmarinov.drivers.usb.cxusb;

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.usb.cxusb.Si2168Data.Si2168Chip;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;

class Si2168 implements DvbFrontend {
    // TODO: si2168_select and si2168_deselect for i2c toggling?

    private final static String TAG = Si2168.class.getSimpleName();
    private final static int TIMEOUT_MS = 70;

    static final int SI2168_TS_PARALLEL = 0x06;

    private final Resources resources;
    private final I2cAdapter i2c;
    private final int addr;
    private final int ts_mode;
    private final boolean ts_clock_inv;

    private int version;

    Si2168(Resources resources, I2cAdapter i2c, int addr, int ts_mode, boolean ts_clock_inv) {
        this.resources = resources;
        this.i2c = i2c;
        this.addr = addr;
        this.ts_mode = ts_mode;
        this.ts_clock_inv = ts_clock_inv;
    }

    private void si2168_cmd_execute_wr(byte[] wargs, int wlen) throws DvbException {
        si2168_cmd_execute(wargs, wlen, 0);
    }

    private final static byte[] EMPTY = new byte[0];
    private synchronized @NonNull byte[] si2168_cmd_execute(@Nullable byte[] wargs, int wlen, int rlen) throws DvbException {
        if (wlen > 0 && wlen <= wargs.length) {
            i2c.send(addr, wargs, wlen);
        } else {
            if (wargs != null || wlen != 0) throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
        }

        if (rlen > 0) {
            byte[] rout = new byte[rlen];
            long endTime = System.nanoTime() + TIMEOUT_MS * 1_000_000L;

            while (System.nanoTime() < endTime) {
                i2c.recv(addr, rout, rlen);

                if ((((rout[0] & 0xFF) >> 7) & 0x01) != 0) {
                    Log.d(TAG, "firmware ready");
                    break;
                }
            }

            /* error bit set? */
            if ((((rout[0] & 0xFF) >> 6) & 0x01) != 0) {
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.failed_to_read_from_register));
            }

            if ((((rout[0] & 0xFF) >> 7) & 0x01) == 0) {
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.timed_out_read_from_register));
            }

            return rout;
        }

        return EMPTY;
    }

    @Override
    public DvbCapabilities getCapabilities() {
        return Si2168Data.CAPABILITIES;
    }

    @Override
    public void attatch() throws DvbException {
        /* Initialize */
        si2168_cmd_execute_wr(
                new byte[] {(byte) 0xc0, (byte) 0x12, (byte) 0x00, (byte) 0x0c, (byte) 0x00, (byte) 0x0d, (byte) 0x16, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00},
                13
        );

        /* Power up */
        si2168_cmd_execute(
                new byte[] {(byte) 0xc0, (byte) 0x06, (byte) 0x01, (byte) 0x0f, (byte) 0x00, (byte) 0x20, (byte) 0x20, (byte) 0x01},
                8, 1
        );

        /* Query chip revision */
        byte[] chipInfo = si2168_cmd_execute(new byte[] {0x02}, 1, 13);

        int chipId = ((chipInfo[1] & 0xFF) << 24) | ((chipInfo[2] & 0xFF) << 16) | ((chipInfo[3] & 0xFF) << 8) | (chipInfo[4] & 0xFF);
        Si2168Chip chip = Si2168Chip.fromId(chipId);
        if (chip == null) throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_tuner_on_device));

        version = ((chipInfo[1] & 0xFF) << 24) | (((chipInfo[3] & 0xFF) - '0') << 16) | (((chipInfo[4] & 0xFF) - '0') << 8) | (chipInfo[5] & 0xFF);

        Log.d(TAG, "Found chip "+chip);
    }

    @Override
    public void release() {
        // si2168_sleep
    }

    @Override
    public void init(DvbTuner tuner) throws DvbException {
        // si2168_init
    }

    @Override
    public void setParams(long frequency, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        // si2168_set_frontend
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
        // no-op
    }

    @Override
    public void disablePidFilter() throws DvbException {
        // no-op
    }
}
