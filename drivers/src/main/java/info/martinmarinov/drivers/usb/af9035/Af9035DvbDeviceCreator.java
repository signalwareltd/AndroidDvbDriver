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

package info.martinmarinov.drivers.usb.af9035;

import android.content.Context;
import android.hardware.usb.UsbDevice;

import java.util.Set;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.usb.DvbUsbDevice;
import info.martinmarinov.drivers.usb.DvbUsbIds;

import static info.martinmarinov.drivers.tools.SetUtils.setOf;

public class Af9035DvbDeviceCreator implements DvbUsbDevice.Creator {
    private final static Set<DeviceFilter> AF9035_DEVICES = setOf(
            /* AF9035 devices */
            new DeviceFilter(DvbUsbIds.USB_VID_AFATECH, DvbUsbIds.USB_PID_AFATECH_AF9035_9035, "Afatech AF9035 reference design"),
            new DeviceFilter(DvbUsbIds.USB_VID_AFATECH, DvbUsbIds.USB_PID_AFATECH_AF9035_1000, "Afatech AF9035 reference design"),
            new DeviceFilter(DvbUsbIds.USB_VID_AFATECH, DvbUsbIds.USB_PID_AFATECH_AF9035_1001, "Afatech AF9035 reference design"),
            new DeviceFilter(DvbUsbIds.USB_VID_AFATECH, DvbUsbIds.USB_PID_AFATECH_AF9035_1002, "Afatech AF9035 reference design"),
            new DeviceFilter(DvbUsbIds.USB_VID_AFATECH, DvbUsbIds.USB_PID_AFATECH_AF9035_1003, "Afatech AF9035 reference design"),
            new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, DvbUsbIds.USB_PID_TERRATEC_CINERGY_T_STICK, "TerraTec Cinergy T Stick"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_A835, "AVerMedia AVerTV Volar HD/PRO (A835)"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_B835, "AVerMedia AVerTV Volar HD/PRO (A835)"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_1867, "AVerMedia HD Volar (A867)"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_A867, "AVerMedia HD Volar (A867)"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_TWINSTAR, "AVerMedia Twinstar (A825)"),
            new DeviceFilter(DvbUsbIds.USB_VID_ASUS, DvbUsbIds.USB_PID_ASUS_U3100MINI_PLUS, "Asus U3100Mini Plus"),
            new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, 0x00aa, "TerraTec Cinergy T Stick (rev. 2)"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, 0x0337, "AVerMedia HD Volar (A867)"),
            new DeviceFilter(DvbUsbIds.USB_VID_GTEK, DvbUsbIds.USB_PID_EVOLVEO_XTRATV_STICK, "EVOLVEO XtraTV stick"),

	        /* IT9135 devices */
            new DeviceFilter(DvbUsbIds.USB_VID_ITETECH, DvbUsbIds.USB_PID_ITETECH_IT9135, "ITE 9135 Generic"),
            new DeviceFilter(DvbUsbIds.USB_VID_ITETECH, DvbUsbIds.USB_PID_ITETECH_IT9135_9005, "ITE 9135(9005) Generic"),
            new DeviceFilter(DvbUsbIds.USB_VID_ITETECH, DvbUsbIds.USB_PID_ITETECH_IT9135_9006, "ITE 9135(9006) Generic"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_A835B_1835, "Avermedia A835B(1835)"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_A835B_2835, "Avermedia A835B(2835)"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_A835B_3835, "Avermedia A835B(3835)"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_A835B_4835, "Avermedia A835B(4835)"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_TD110, "Avermedia AverTV Volar HD 2 (TD110)"),
            new DeviceFilter(DvbUsbIds.USB_VID_AVERMEDIA, DvbUsbIds.USB_PID_AVERMEDIA_H335, "Avermedia H335"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, DvbUsbIds.USB_PID_KWORLD_UB499_2T_T09, "Kworld UB499-2T T09"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, DvbUsbIds.USB_PID_SVEON_STV22_IT9137, "Sveon STV22 Dual DVB-T HDTV"),
            new DeviceFilter(DvbUsbIds.USB_VID_KWORLD_2, DvbUsbIds.USB_PID_CTVDIGDUAL_V2, "Digital Dual TV Receiver CTVDIGDUAL_V2"),

            /* XXX: that same ID [0ccd:0099] is used by af9015 driver too */
            // new DeviceFilter(DvbUsbIds.USB_VID_TERRATEC, 0x0099, "TerraTec Cinergy T Stick Dual RC (rev. 2)"), // This is not supported since there are two devices with same ids with only manufacturer being different meaning we can't use this due to auto start issues
            new DeviceFilter(DvbUsbIds.USB_VID_LEADTEK, 0x6a05, "Leadtek WinFast DTV Dongle Dual"),
            new DeviceFilter(DvbUsbIds.USB_VID_HAUPPAUGE, 0xf900, "Hauppauge WinTV-MiniStick 2"),
            new DeviceFilter(DvbUsbIds.USB_VID_PCTV, DvbUsbIds.USB_PID_PCTV_78E, "PCTV AndroiDTV (78e)"),
            new DeviceFilter(DvbUsbIds.USB_VID_PCTV, DvbUsbIds.USB_PID_PCTV_79E, "PCTV microStick (79e)")

            /* IT930x devices not supported by this driver */
    );

    @Override
    public Set<DeviceFilter> getSupportedDevices() {
        return AF9035_DEVICES;
    }

    @Override
    public DvbDevice create(UsbDevice usbDevice, Context context, DeviceFilter filter) throws DvbException {
        return new Af9035DvbDevice(usbDevice, context, filter);
    }

}
