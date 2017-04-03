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

package info.martinmarinov.drivers.usb.rtl28xx;

import android.support.annotation.NonNull;

import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;

import static info.martinmarinov.drivers.usb.rtl28xx.Rtl2832FrontendData.DvbtRegBitName.DVBT_PIP_ON;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl2832FrontendData.DvbtRegBitName.DVBT_SOFT_RST;
import static info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxConst.SYS_DEMOD_CTL;

class Rtl2832pFrontend implements DvbFrontend {
    private final Rtl2832Frontend rtl2832Frontend;
    private final Rtl28xxDvbDevice rtl28xxDvbDevice;
    private final DvbFrontend slave;
    private final DvbCapabilities rtl2832Capabilities;

    private boolean slaveEnabled;

    Rtl2832pFrontend(Rtl2832Frontend rtl2832Frontend, Rtl28xxDvbDevice rtl28xxDvbDevice, DvbFrontend slave) throws DvbException {
        this.rtl2832Frontend = rtl2832Frontend;
        this.rtl28xxDvbDevice = rtl28xxDvbDevice;
        this.slave = slave;
        this.rtl2832Capabilities = rtl2832Frontend.getCapabilities();
    }

    @Override
    public DvbCapabilities getCapabilities() {
        return slave.getCapabilities();
    }

    @Override
    public void attatch() throws DvbException {
        rtl2832Frontend.attatch();
        slave.attatch();
    }

    @Override
    public void release() {
        slave.release();
        rtl2832Frontend.release();
    }

    @Override
    public void init(DvbTuner tuner) throws DvbException {
        rtl2832Frontend.init(tuner);
        slave.init(tuner);
        enableSlave(true);
    }

    @Override
    public void setParams(long frequency, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        if (rtl2832Capabilities.getSupportedDeliverySystems().contains(deliverySystem)) {
            enableSlave(false);
        } else {
            enableSlave(true);
        }
        activeFrontend().setParams(frequency, bandwidthHz, deliverySystem);
    }

    @Override
    public int readSnr() throws DvbException {
        return activeFrontend().readSnr();
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        return activeFrontend().readRfStrengthPercentage();
    }

    @Override
    public int readBer() throws DvbException {
        return activeFrontend().readBer();
    }

    @Override
    public Set<DvbStatus> getStatus() throws DvbException {
        return activeFrontend().getStatus();
    }

    private DvbFrontend activeFrontend() {
        return slaveEnabled ? slave : rtl2832Frontend;
    }

    private void enableSlave(boolean enable) throws DvbException {
        if (enable) {
            rtl28xxDvbDevice.wrReg(SYS_DEMOD_CTL, 0x00, 0x48); // disable ADC
            rtl2832Frontend.wrDemodReg(DVBT_SOFT_RST, 0x0);
            rtl2832Frontend.wr(0x0c, 1, new byte[]{(byte) 0x5f, (byte) 0xff});
            rtl2832Frontend.wrDemodReg(DVBT_PIP_ON, 0x1);
            rtl2832Frontend.wr(0xbc, 0, new byte[]{(byte) 0x18});
            rtl2832Frontend.wr(0x92, 1, new byte[]{(byte) 0x7f, (byte) 0xf7, (byte) 0xff});
        } else {
            rtl2832Frontend.wr(0x92, 1, new byte[]{(byte) 0x00, (byte) 0x0f, (byte) 0xff});
            rtl2832Frontend.wr(0xbc, 0, new byte[]{(byte) 0x08});
            rtl2832Frontend.wrDemodReg(DVBT_PIP_ON, 0x0);
            rtl2832Frontend.wr(0x0c, 1, new byte[]{(byte) 0x00, (byte) 0x00});
            rtl2832Frontend.wrDemodReg(DVBT_SOFT_RST, 0x1);
            rtl28xxDvbDevice.wrReg(SYS_DEMOD_CTL, 0x48, 0x48); // enable ADC
        }
        this.slaveEnabled = enable;
    }

    @Override
    public void disablePidFilter() throws DvbException {
        rtl2832Frontend.disablePidFilter(slaveEnabled);
    }

    @Override
    public void setPids(int... pids) throws DvbException {
        rtl2832Frontend.setPids(slaveEnabled, pids);
    }
}
