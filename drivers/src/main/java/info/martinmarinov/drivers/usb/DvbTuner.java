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

package info.martinmarinov.drivers.usb;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbException;

public interface DvbTuner {
    void attatch() throws DvbException;
    void release(); // release should always succeed or fail catastrophically
    void init() throws DvbException;
    void setParams(long frequency, long bandwidthHz, DeliverySystem deliverySystem) throws DvbException;
    long getIfFrequency() throws DvbException;
    int readRfStrengthPercentage() throws DvbException;
}
