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

package info.martinmarinov.drivers.file;

import android.content.res.Resources;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbDemux;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.SetUtils;
import info.martinmarinov.drivers.tools.io.ByteSource;
import info.martinmarinov.drivers.tools.io.ThrottledTsSource;
import info.martinmarinov.drivers.DeliverySystem;

import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_OPEN_USB;

/**
 * This is a DvbDevice that can be used for debugging purposes.
 * It takes a file and streams it as if it is a stream coming from a real USB device.
 */
public class DvbFileDevice extends DvbDevice {
    private final static DvbCapabilities CAPABILITIES = new DvbCapabilities(174000000L, 862000000L, 166667L, SetUtils.setOf(DeliverySystem.values()));

    private final Resources resources;
    private final File file;
    private final long freq;
    private final long bandwidth;

    private boolean isTuned = false;

    public DvbFileDevice(Resources resources, File file, long freq, long bandwidth) {
        super(DvbDemux.DvbDmxSwfilter());
        this.resources = resources;
        this.file = file;
        this.freq = freq;
        this.bandwidth = bandwidth;
    }

    @Override
    public void open() throws DvbException {
        if (!file.canRead()) throw new DvbException(CANNOT_OPEN_USB, new IOException());
    }

    @Override
    public DeviceFilter getDeviceFilter() {
        return new FileDeviceFilter(resources.getString(R.string.rec_name, freq / 1_000_000.0, bandwidth / 1_000_000.0), file);
    }

    @Override
    protected void tuneTo(long freqHz, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        this.isTuned = freqHz == this.freq && bandwidthHz == this.bandwidth;
    }

    @Override
    public DvbCapabilities readCapabilities() throws DvbException {
        return CAPABILITIES;
    }

    @Override
    public int readSnr() throws DvbException {
        return isTuned ? 400 : 0;
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        return isTuned ? 100 : 0;
    }

    @Override
    public int readBitErrorRate() throws DvbException {
        return isTuned ? 0 : 0xFFFF;
    }

    @Override
    public Set<DvbStatus> getStatus() throws DvbException {
        if (isTuned) return SetUtils.setOf(DvbStatus.FE_HAS_SIGNAL, DvbStatus.FE_HAS_CARRIER, DvbStatus.FE_HAS_VITERBI, DvbStatus.FE_HAS_SYNC, DvbStatus.FE_HAS_LOCK);
        return SetUtils.setOf();
    }

    @Override
    protected ByteSource createTsSource() {
        return new ThrottledTsSource(file);
    }
}
