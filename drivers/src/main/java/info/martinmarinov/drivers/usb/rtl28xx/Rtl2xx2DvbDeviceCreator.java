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

import android.content.Context;
import android.hardware.usb.UsbDevice;

import java.util.Set;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.usb.DvbUsbDevice;
import info.martinmarinov.drivers.usb.DvbUsbIds;
import info.martinmarinov.drivers.tools.DeviceFilterMatcher;

import static info.martinmarinov.drivers.tools.SetUtils.setOf;

public class Rtl2xx2DvbDeviceCreator implements DvbUsbDevice.Creator {
    private final static Set<DeviceFilter> RTL2832_DEVICES = setOf(
        new DeviceFilter(DvbUsbIds.USB_VID_REALTEK, 0x2832, "Realtek RTL2832U reference design"),
        new DeviceFilter(DvbUsbIds.USB_VID_REALTEK, 0x2838, "Realtek RTL2832U reference design"),
        new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, DvbUsbIds.USB_PID_TERRATEC_CINERGY_T_STICK_BLACK_REV1, "TerraTec Cinergy T Stick Black"),
        new DeviceFilter(DvbUsbIds.USB_VID_GTEK, DvbUsbIds.USB_PID_DELOCK_USB2_DVBT, "G-Tek Electronics Group Lifeview LV5TDLX DVB-T"),
        new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, DvbUsbIds.USB_PID_NOXON_DAB_STICK, "TerraTec NOXON DAB Stick"),
        new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, DvbUsbIds.USB_PID_NOXON_DAB_STICK_REV2, "TerraTec NOXON DAB Stick (rev 2)"),
        new DeviceFilter(DvbUsbIds.USB_VID_GTEK, DvbUsbIds.USB_PID_TREKSTOR_TERRES_2_0, "Trekstor DVB-T Stick Terres 2.0"),
        new DeviceFilter(DvbUsbIds.USB_VID_DEXATEK, 0x1101, "Dexatek DK DVB-T Dongle"),
        new DeviceFilter(DvbUsbIds.USB_VID_LEADTEK, 0x6680, "DigitalNow Quad DVB-T Receiver"),
        new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, 0x00d3, "TerraTec Cinergy T Stick RC (Rev. 3)"),
        new DeviceFilter(DvbUsbIds.USB_VID_DEXATEK, 0x1102, "Dexatek DK mini DVB-T Dongle"),
        new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, 0x00d7, "TerraTec Cinergy T Stick+"),
        new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, 0xd3a8, "ASUS My Cinema-U3100Mini Plus V2"),
        new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, 0xd393, "GIGABYTE U7300"),
        new DeviceFilter(DvbUsbIds.USB_VID_DEXATEK, 0x1104, "Digivox Micro Hd"),
        new DeviceFilter(DvbUsbIds.USB_VID_COMPRO, 0x0620, "Compro VideoMate U620F"),
        new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, 0xd394, "MaxMedia HU394-T")
    );

    private final static DeviceFilterMatcher SUPPORTED_DEVICES = new DeviceFilterMatcher(RTL2832_DEVICES);

    @Override
    public DvbUsbDevice create(UsbDevice usbDevice, Context context) throws DvbException {
        DeviceFilter filter = SUPPORTED_DEVICES.getFilter(usbDevice);
        if (filter == null) return null; // not supported device found

        return new Rtl2832DvbDevice(usbDevice, context, filter);
    }
}
