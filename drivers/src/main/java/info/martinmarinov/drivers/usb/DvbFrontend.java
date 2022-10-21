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

package info.martinmarinov.drivers.usb;

import androidx.annotation.NonNull;

import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;

public interface DvbFrontend {
    // TODO these capabilities contain frequency min and max which is actually determined by tuner
    DvbCapabilities getCapabilities();

    void attatch() throws DvbException;
    void release(); // release should always succeed or fail catastrophically

    // don't forget to call tuner.init() from here!
    void init(DvbTuner tuner) throws DvbException;

    void setParams(long frequency, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException;
    int readSnr() throws DvbException;
    int readRfStrengthPercentage() throws DvbException;
    int readBer() throws DvbException;
    Set<DvbStatus> getStatus() throws DvbException;
    void setPids(int ... pids) throws DvbException;
    void disablePidFilter() throws DvbException;
}
