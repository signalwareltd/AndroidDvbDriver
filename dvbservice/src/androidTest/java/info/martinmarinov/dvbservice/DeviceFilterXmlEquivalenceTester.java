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

package info.martinmarinov.dvbservice;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.util.HashSet;
import java.util.Set;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.usb.DvbUsbDevice;
import info.martinmarinov.drivers.usb.DvbUsbDeviceRegistry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class DeviceFilterXmlEquivalenceTester {

    @Test
    public void allDevicesAreInXml() {
        Set<DeviceFilter> devicesInXml = getDeviceData(getApplicationContext().getResources(), R.xml.device_filter);
        Set<DeviceFilter> supportedDecvices = getDevicesInApp();

        if (!supportedDecvices.equals(devicesInXml)) {
            System.out.println("Devices needed to be added to the xml");

            for (DeviceFilter supportedDevice : supportedDecvices) {
                if (!devicesInXml.contains(supportedDevice)) {
                    System.out.printf("    <usb-device vendor-id=\"0x%x\" product-id=\"0x%x\"/> <!-- %s -->\n",
                            supportedDevice.getVendorId(), supportedDevice.getProductId(), supportedDevice.getName()
                    );
                }
            }

            System.out.println("Devices in xml but not supported");

            for (DeviceFilter deviceInXml : devicesInXml) {
                if (!supportedDecvices.contains(deviceInXml)) {
                    System.out.printf("vendor-id=0x%x, product-id=0x%x\n",
                            deviceInXml.getVendorId(), deviceInXml.getProductId()
                    );
                }
            }

        }

        assertThat(supportedDecvices, equalTo(devicesInXml));
    }

    private static HashSet<DeviceFilter> getDevicesInApp() {
        HashSet<DeviceFilter> supportedDevices = new HashSet<>();
        for (DvbUsbDevice.Creator d : DvbUsbDeviceRegistry.AVAILABLE_DRIVERS) {
            supportedDevices.addAll(d.getSupportedDevices());
        }
        return supportedDevices;
    }

    private static Set<DeviceFilter> getDeviceData(Resources resources, int xmlResourceId) {
        Set<DeviceFilter> ans = new HashSet<>();
        try {
            XmlResourceParser xml = resources.getXml(xmlResourceId);

            xml.next();
            int eventType;
            while ((eventType = xml.getEventType()) != XmlPullParser.END_DOCUMENT) {

                if (eventType == XmlPullParser.START_TAG) {
                    if (xml.getName().equals("usb-device")) {
                        AttributeSet as = Xml.asAttributeSet(xml);
                        Integer vendorId = parseInt(as.getAttributeValue(null, "vendor-id"));
                        Integer productId = parseInt(as.getAttributeValue(null, "product-id"));
                        ans.add(new DeviceFilter(vendorId, productId, null));
                    }
                }
                xml.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ans;
    }

    private static Integer parseInt(String number) {
        if (number.startsWith("0x")) {
            return Integer.valueOf( number.substring(2), 16);
        } else {
            return Integer.valueOf( number, 10);
        }
    }
}