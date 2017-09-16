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

package info.martinmarinov.dvbservice;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;

public class UsbDelegate extends Activity {
    private static final String TAG = UsbDelegate.class.getSimpleName();
    private static final String ACTION_DVB_DEVICE_ATTACHED = "info.martinmarinov.dvbdriver.DVB_DEVICE_ATTACHED";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            Log.d(TAG, "USB DVB-T attached: " + usbDevice.getDeviceName());
            Intent newIntent = new Intent(ACTION_DVB_DEVICE_ATTACHED);
            newIntent.putExtra(UsbManager.EXTRA_DEVICE, usbDevice);
            try {
                startActivity(newIntent);
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "No activity found for DVB-T handling");
            }
        }

        finish();
    }
}