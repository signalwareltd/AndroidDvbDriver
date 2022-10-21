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

package info.martinmarinov.usbxfer;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * Sometimes there could be multiple UsbInterfaces with same ID. Android API only allows
 * using the UsbInterface#getAlternateSettings after Android 5.0 so this is a compatibility layer
 */
public class AlternateUsbInterface {
    public static List<AlternateUsbInterface> forUsbInterface(UsbDeviceConnection deviceConnection, UsbInterface usbInterface) {
        byte[] rawDescriptors = deviceConnection.getRawDescriptors();

        List<AlternateUsbInterface> alternateSettings = new ArrayList<>(2);
        int offset = 0;
        while(offset < rawDescriptors.length) {
            int bLength = rawDescriptors[offset] & 0xFF;
            int bDescriptorType = rawDescriptors[offset + 1] & 0xFF;

            if (bDescriptorType == 0x04) {
                // interface descriptor, we are not interested
                int bInterfaceNumber = rawDescriptors[offset + 2] & 0xFF;
                int bAlternateSetting = rawDescriptors[offset + 3] & 0xFF;

                if (bInterfaceNumber == usbInterface.getId()) {
                    alternateSettings.add(new AlternateUsbInterface(usbInterface, bAlternateSetting));
                }
            }

            // go to next structure
            offset += bLength;
        }

        if (alternateSettings.size() < 1) throw new IllegalStateException();
        return alternateSettings;
    }

    private final UsbInterface usbInterface;
    private final int alternateSettings;

    @VisibleForTesting
    AlternateUsbInterface(UsbInterface usbInterface, int alternateSettings) {
        this.usbInterface = usbInterface;
        this.alternateSettings = alternateSettings;
    }

    public UsbInterface getUsbInterface() {
        return usbInterface;
    }

    int getAlternateSettings() {
        return alternateSettings;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlternateUsbInterface that = (AlternateUsbInterface) o;

        if (alternateSettings != that.alternateSettings) return false;
        return usbInterface != null ? usbInterface.equals(that.usbInterface) : that.usbInterface == null;

    }

    @Override
    public int hashCode() {
        int result = usbInterface != null ? usbInterface.hashCode() : 0;
        result = 31 * result + alternateSettings;
        return result;
    }
}
