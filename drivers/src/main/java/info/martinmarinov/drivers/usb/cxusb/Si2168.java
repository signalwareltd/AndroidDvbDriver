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

import android.support.annotation.NonNull;

import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;

class Si2168 implements DvbFrontend {
    static final int SI2168_TS_PARALLEL = 0x06;

    private final I2cAdapter i2c;
    private final int addr;
    private final int ts_mode;
    private final boolean ts_clock_inv;

    Si2168(I2cAdapter i2c, int addr, int ts_mode, boolean ts_clock_inv) {
        this.i2c = i2c;
        this.addr = addr;
        this.ts_mode = ts_mode;
        this.ts_clock_inv = ts_clock_inv;
    }

    @Override
    public DvbCapabilities getCapabilities() {
        return Si2168Data.CAPABILITIES;
    }

    @Override
    public void attatch() throws DvbException {
        // si2168_probe
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
