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

import java.util.Set;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.usb.DvbUsbDevice;

import static info.martinmarinov.drivers.tools.SetUtils.setOf;
import static info.martinmarinov.drivers.usb.cxusb.MygicaT230.MYGICA_T230;
import static info.martinmarinov.drivers.usb.cxusb.MygicaT230C.MYGICA_T230C;

public class CxUsbDvbDeviceCreator implements DvbUsbDevice.Creator {
    private final static Set<DeviceFilter> CXUSB_DEVICES = setOf(MYGICA_T230, MYGICA_T230C);

    @Override
    public Set<DeviceFilter> getSupportedDevices() {
        return CXUSB_DEVICES;
    }

    @Override
    public DvbUsbDevice create(UsbDevice usbDevice, Context context, DeviceFilter filter) throws DvbException {
        if (MYGICA_T230C.matches(usbDevice)) {

            return new MygicaT230C(usbDevice, context);

        } else {

            return new MygicaT230(usbDevice, context);

        }
    }
}
