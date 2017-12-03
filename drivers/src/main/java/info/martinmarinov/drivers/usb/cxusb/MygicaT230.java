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

import android.content.Context;
import android.hardware.usb.UsbDevice;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.tools.SleepUtils;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.usb.DvbUsbIds;
import info.martinmarinov.drivers.usb.silabs.Si2157;
import info.martinmarinov.drivers.usb.silabs.Si2168;

import static info.martinmarinov.drivers.usb.silabs.Si2157.Type.SI2157_CHIPTYPE_SI2157;

class MygicaT230 extends CxUsbDvbDevice {
    private final static String MYGICA_NAME = "Mygica T230 DVB-T/T2/C";
    final static DeviceFilter MYGICA_T230 = new DeviceFilter(DvbUsbIds.USB_VID_CONEXANT, DvbUsbIds.USB_PID_MYGICA_T230, MYGICA_NAME);

    private Si2168 frontend;

    MygicaT230(UsbDevice usbDevice, Context context) throws DvbException {
        super(usbDevice, context, MYGICA_T230);
    }

    @Override
    public String getDebugString() {
        return MYGICA_NAME;
    }

    @Override
    synchronized protected void powerControl(boolean onoff) throws DvbException {
        cxusb_d680_dmb_power_ctrl(onoff);
        cxusb_streaming_ctrl(onoff);
    }

    private void cxusb_d680_dmb_power_ctrl(boolean onoff) throws DvbException {
        cxusb_power_ctrl(onoff);
        if (!onoff) return;

        SleepUtils.mdelay(128);
        cxusb_ctrl_msg(CMD_DIGITAL, new byte[0], 0, new byte[1], 1);
        SleepUtils.mdelay(100);
    }

    @Override
    protected void readConfig() throws DvbException {
        // no-op
    }

    @Override
    protected DvbFrontend frontendAttatch() throws DvbException {
        return frontend = new Si2168(this, resources, i2CAdapter, 0x64, SI2168_TS_PARALLEL, true, SI2168_TS_CLK_AUTO_FIXED, false);
    }

    @Override
    protected DvbTuner tunerAttatch() throws DvbException {
        return new Si2157(resources, i2CAdapter, frontend.gateControl(), 0x60, true, SI2157_CHIPTYPE_SI2157);
    }

    @Override
    protected void init() throws DvbException {
        // no-op
    }
}
