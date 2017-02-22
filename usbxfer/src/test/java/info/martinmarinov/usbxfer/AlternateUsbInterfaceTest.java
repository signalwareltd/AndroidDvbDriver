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

package info.martinmarinov.usbxfer;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;

import org.junit.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AlternateUsbInterfaceTest {
    @Test
    public void getAlternateSetting() throws Exception {
        UsbDeviceConnection usbDeviceConnection = mockConnectionWithRawDescriptors(new byte[] {
                18, 1, 0, 2, 0, 0, 0, 64, -38, 11, 56, 40, 0, 1, 1, 2, 3, 1, // Device Descriptor
                9, 2, 34, 0, 2, 1, 4, -128, -6, // Configuration Descriptor
                9, 4, 0, 0, 1, -1, -1, -1, 5, // Interface Descriptor for interface 0 with alternate setting 0
                9, 4, 0, 1, 1, -1, -1, -1, 5, // Interface Descriptor for interface 0 with alternate setting 1
                7, 5, -127, 2, 0, 2, 0, // Endpoint Descriptor
                9, 4, 1, 0, 0, -1, -1, -1, 5 // Interface Descriptor for interface 1 with alternate setting 0
        });

        // Interface 0 has alternate settings {0, 1}
        UsbInterface i0 = mockInterface(0);
        assertThat(AlternateUsbInterface.forUsbInterface(usbDeviceConnection, i0),
                equalTo(asList(new AlternateUsbInterface(i0, 0), new AlternateUsbInterface(i0, 1))));

        // Interface 1 has alternate settings {0}
        UsbInterface i1 = mockInterface(1);
        assertThat(AlternateUsbInterface.forUsbInterface(usbDeviceConnection, i1),
                equalTo(singletonList(new AlternateUsbInterface(i1, 0))));
    }

    private static UsbInterface mockInterface(int id) {
        UsbInterface usbInterface = mock(UsbInterface.class);
        when(usbInterface.getId()).thenReturn(id);
        return usbInterface;
    }

    private static UsbDeviceConnection mockConnectionWithRawDescriptors(byte[] rawDescriptors) {
        UsbDeviceConnection usbDeviceConnection = mock(UsbDeviceConnection.class);
        when(usbDeviceConnection.getRawDescriptors()).thenReturn(rawDescriptors);
        return usbDeviceConnection;
    }
}