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

package info.martinmarinov.drivers;

import android.hardware.usb.UsbDevice;

import org.junit.Test;
import org.mockito.Mockito;

import info.martinmarinov.drivers.tools.DeviceFilterMatcher;

import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_PID_AVERMEDIA_A835;
import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_PID_HAUPPAUGE_MYTV_T;
import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_PID_REALTEK_RTL2831U;
import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_PID_REALTEK_RTL2832U;
import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_VID_AVERMEDIA;
import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_VID_HAUPPAUGE;
import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_VID_REALTEK;
import static info.martinmarinov.drivers.tools.SetUtils.setOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public class DeviceFilterMatcherTest {
    private final static DeviceFilterMatcher TEST_MATCHER = new DeviceFilterMatcher(setOf(
            new DeviceFilter(USB_VID_HAUPPAUGE, USB_PID_HAUPPAUGE_MYTV_T, "HAUPPAUGE"),
            new DeviceFilter(USB_VID_REALTEK, USB_PID_REALTEK_RTL2831U, "RTL2831U"),
            new DeviceFilter(0xFFFF, 0xFFFF, "Edge case")
    ));

    @Test
    public void whenMatches() throws Exception {
        assertThat(TEST_MATCHER.getFilter(mockUsbDevice(USB_VID_HAUPPAUGE, USB_PID_HAUPPAUGE_MYTV_T)).getName(), equalTo("HAUPPAUGE"));
        assertThat(TEST_MATCHER.getFilter(mockUsbDevice(USB_VID_REALTEK, USB_PID_REALTEK_RTL2831U)).getName(), equalTo("RTL2831U"));
        assertThat(TEST_MATCHER.getFilter(mockUsbDevice(0xFFFF, 0xFFFF)).getName(), equalTo("Edge case"));
    }

    @Test
    public void whenDoesntMatch() throws Exception {
        assertThat(TEST_MATCHER.getFilter(mockUsbDevice(USB_VID_AVERMEDIA, USB_PID_AVERMEDIA_A835)), nullValue());
        assertThat(TEST_MATCHER.getFilter(mockUsbDevice(USB_VID_REALTEK, USB_PID_REALTEK_RTL2832U)), nullValue());
        assertThat(TEST_MATCHER.getFilter(mockUsbDevice(0xFFFF, USB_PID_HAUPPAUGE_MYTV_T)), nullValue());
        assertThat(TEST_MATCHER.getFilter(mockUsbDevice(USB_VID_HAUPPAUGE, 0xFFFF)), nullValue());
    }

    private static UsbDevice mockUsbDevice(int vendorId, int productId) {
        UsbDevice mockUsbDevice = Mockito.mock(UsbDevice.class);
        when(mockUsbDevice.getVendorId()).thenReturn(vendorId);
        when(mockUsbDevice.getProductId()).thenReturn(productId);
        return mockUsbDevice;
    }
}