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

package info.martinmarinov.drivers.tools;

import android.annotation.SuppressLint;
import android.hardware.usb.UsbDevice;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import info.martinmarinov.drivers.DeviceFilter;

public class DeviceFilterMatcher {

    @SuppressLint("UseSparseArrays") // Use SparseArrays and unit testing is impossible
    private final Map<Integer, DeviceFilter> filterMap = new HashMap<>();

    public DeviceFilterMatcher(Set<DeviceFilter> filters) {
        for (DeviceFilter deviceFilter : filters) {
            filterMap.put(hash(deviceFilter), deviceFilter);
        }
    }

    public DeviceFilter getFilter(UsbDevice usbDevice) {
        return filterMap.get(hash(usbDevice));
    }

    private static int hash(DeviceFilter deviceFilter) {
        return hash(deviceFilter.getVendorId(), deviceFilter.getProductId());
    }

    private static int hash(UsbDevice usbDevice) {
        return hash(usbDevice.getVendorId(), usbDevice.getProductId());
    }

    private static int hash(int vendorId, int productId) {
        return (vendorId << 16) | (productId & 0xFFFF);
    }
}
