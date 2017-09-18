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

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.tools.DeviceFilterMatcher;
import info.martinmarinov.drivers.usb.af9035.Af9035DvbDeviceCreator;
import info.martinmarinov.drivers.usb.cxusb.CxUsbDvbDeviceCreator;
import info.martinmarinov.drivers.usb.rtl28xx.Rtl2xx2DvbDeviceCreator;

public class DvbUsbDeviceRegistry {

    public static DvbUsbDevice.Creator[] AVAILABLE_DRIVERS = new DvbUsbDevice.Creator[] {
            new Rtl2xx2DvbDeviceCreator(),
            new CxUsbDvbDeviceCreator(),
            new Af9035DvbDeviceCreator()
    };

    /**
     * Checks if the {@link UsbDevice} could be handled by the available drivers and returns a
     * {@link DvbDevice} if the device is supported.
     * @param usbDevice a {@link UsbDevice} device that is attached to the system
     * @param context the application context, used for accessing usb system service and obtaining permissions
     * @return a valid {@link DvbDevice} to control the {@link UsbDevice} as a DVB frontend or
     * a null if none of the available drivers can handle the provided {@link UsbDevice}
     */
    private static DvbDevice getDvbUsbDeviceFor(UsbDevice usbDevice, Context context) throws DvbException {
        for (DvbUsbDevice.Creator c : AVAILABLE_DRIVERS) {
            DeviceFilterMatcher deviceFilterMatcher = new DeviceFilterMatcher(c.getSupportedDevices());
            DeviceFilter filter = deviceFilterMatcher.getFilter(usbDevice);

            if (filter != null) {
                DvbDevice dvbDevice = c.create(usbDevice, context, filter);
                if (dvbDevice != null) return dvbDevice;
            }
        }
        return null;
    }

    /**
     * Gets a {@link DvbDevice} if a supported DVB USB dongle is connected to the system.
     * If multiple dongles are connected, a {@link Collection} would be returned
     * @param context a context for obtaining {@link Context#USB_SERVICE}
     * @return a {@link Collection} of available {@link DvbDevice} devices. Can be empty.
     */
    public static List<DvbDevice> getUsbDvbDevices(Context context) throws DvbException {
        List<DvbDevice> availableDvbDevices = new ArrayList<>();

        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        Collection<UsbDevice> availableDevices = manager.getDeviceList().values();
        DvbException lastException = null;
        for (UsbDevice usbDevice : availableDevices) {
            try {
                DvbDevice frontend = getDvbUsbDeviceFor(usbDevice, context);
                if (frontend != null) availableDvbDevices.add(frontend);
            } catch (DvbException e) {
                // Failed to initialize this device, try next and capture exception
                e.printStackTrace();
                lastException = e;
            }
        }
        if (availableDvbDevices.isEmpty()) {
            if (lastException != null) throw  lastException;
        }
        return availableDvbDevices;
    }
}
