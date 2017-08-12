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

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.usb.DvbTuner;

public class Si2157 implements DvbTuner {

    private final I2cAdapter i2c;
    private final int addr;
    private final boolean if_port;

    Si2157(I2cAdapter i2c, int addr, boolean if_port) {
        this.i2c = i2c;
        this.addr = addr;
        this.if_port = if_port;
    }

    @Override
    public void attatch() throws DvbException {

    }

    @Override
    public void release() {

    }

    @Override
    public void init() throws DvbException {

    }

    @Override
    public void setParams(long frequency, long bandwidthHz, DeliverySystem deliverySystem) throws DvbException {

    }

    @Override
    public long getIfFrequency() throws DvbException {
        return 0;
    }
}
