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

import static info.martinmarinov.drivers.tools.SetUtils.setOf;

public class Rtl2xx2DvbDeviceCreator implements DvbUsbDevice.Creator {
    private final static Set<DeviceFilter> RTL2832_DEVICES = setOf(
            new DeviceFilter(DvbUsbIds.USB_VID_REALTEK, 0x2832, "Realtek RTL2832U reference design"),
            new DeviceFilter(DvbUsbIds.USB_VID_REALTEK, 0x2838, "Realtek RTL2832U reference design"),
            new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, DvbUsbIds.USB_PID_TERRATEC_CINERGY_T_STICK_BLACK_REV1, "TerraTec Cinergy T Stick Black"),
            new DeviceFilter(DvbUsbIds.USB_VID_GTEK, DvbUsbIds.USB_PID_DELOCK_USB2_DVBT, "G-Tek Electronics Group Lifeview LV5TDLX DVB-T"),
            new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, DvbUsbIds.USB_PID_NOXON_DAB_STICK, "TerraTec NOXON DAB Stick"),
            new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, DvbUsbIds.USB_PID_NOXON_DAB_STICK_REV2, "TerraTec NOXON DAB Stick (rev 2)"),
            new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, DvbUsbIds.USB_PID_NOXON_DAB_STICK_REV3, "TerraTec NOXON DAB Stick (rev 3)"),
            new DeviceFilter(DvbUsbIds.USB_VID_GTEK, DvbUsbIds.USB_PID_TREKSTOR_TERRES_2_0, "Trekstor DVB-T Stick Terres 2.0"),
            new DeviceFilter(DvbUsbIds.USB_VID_DEXATEK, 0x1101, "Dexatek DK DVB-T Dongle"),
            new DeviceFilter(DvbUsbIds.USB_VID_LEADTEK, 0x6680, "DigitalNow Quad DVB-T Receiver"),
            new DeviceFilter(DvbUsbIds.USB_VID_LEADTEK, DvbUsbIds.USB_PID_WINFAST_DTV_DONGLE_MINID, "Leadtek Winfast DTV Dongle Mini D"),
            new DeviceFilter(DvbUsbIds.USB_VID_LEADTEK, DvbUsbIds.USB_PID_WINFAST_DTV2000DS_PLUS, "Leadtek WinFast DTV2000DS Plus"),
            new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, 0x00d3, "TerraTec Cinergy T Stick RC (Rev. 3)"),
            new DeviceFilter(DvbUsbIds.USB_VID_DEXATEK, 0x1102, "Dexatek DK mini DVB-T Dongle"),
            new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, 0x00d7, "TerraTec Cinergy T Stick+"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, 0xd3a8, "ASUS My Cinema-U3100Mini Plus V2"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, 0xd393, "GIGABYTE U7300"),
            new DeviceFilter(DvbUsbIds.USB_VID_DEXATEK, 0x1104, "MSI DIGIVOX Micro HD"),
            new DeviceFilter(DvbUsbIds.USB_VID_COMPRO, 0x0620, "Compro VideoMate U620F"),
            new DeviceFilter(DvbUsbIds.USB_VID_COMPRO, 0x0650, "Compro VideoMate U650F"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, 0xd394, "MaxMedia HU394-T"),
            new DeviceFilter(DvbUsbIds.USB_VID_LEADTEK, 0x6a03, "Leadtek WinFast DTV Dongle mini"),
            new DeviceFilter(DvbUsbIds.USB_VID_GTEK, DvbUsbIds.USB_PID_CPYTO_REDI_PC50A, "Crypto ReDi PC 50 A"),
            new DeviceFilter(DvbUsbIds.USB_VID_KYE, 0x707f, "Genius TVGo DVB-T03"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, 0xd395, "Peak DVB-T USB"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, DvbUsbIds.USB_PID_SVEON_STV20_RTL2832U, "Sveon STV20"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, DvbUsbIds.USB_PID_SVEON_STV21, "Sveon STV21"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, DvbUsbIds.USB_PID_SVEON_STV27, "Sveon STV27"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, DvbUsbIds.USB_PID_TURBOX_DTT_2000, "TURBO-X Pure TV Tuner DTT-2000"),

            /* RTL2832P devices: */
            new DeviceFilter(DvbUsbIds.USB_VID_HANFTEK, 0x0131, "Astrometa DVB-T2"),
            new DeviceFilter(0x5654, 0xca42, "GoTView MasterHD 3")
    );

    @Override
    public Set<DeviceFilter> getSupportedDevices() {
        return RTL2832_DEVICES;
    }

    @Override
    public DvbUsbDevice create(UsbDevice usbDevice, Context context, DeviceFilter filter) throws DvbException {
        return new Rtl2832DvbDevice(usbDevice, context, filter);
    }
}
