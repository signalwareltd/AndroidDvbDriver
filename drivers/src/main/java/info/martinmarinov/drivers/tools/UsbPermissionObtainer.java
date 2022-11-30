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

package info.martinmarinov.drivers.tools;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import java.util.concurrent.Future;

public class UsbPermissionObtainer {
    private static final String TAG = UsbPermissionObtainer.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    public static Future<UsbDeviceConnection> obtainFdFor(Context context, UsbDevice usbDevice) {
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = PendingIntent.FLAG_MUTABLE;
        }
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (!manager.hasPermission(usbDevice)) {
            AsyncFuture<UsbDeviceConnection> task = new AsyncFuture<>();
            registerNewBroadcastReceiver(context, usbDevice, task);
            manager.requestPermission(usbDevice, PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), flags));
            return task;
        } else {
            return new CompletedFuture<>(manager.openDevice(usbDevice));
        }
    }

    private static void registerNewBroadcastReceiver(final Context context, final UsbDevice usbDevice, final AsyncFuture<UsbDeviceConnection> task) {
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if (device.equals(usbDevice)) {
                            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                if (!manager.hasPermission(device)) {
                                    Log.d(TAG, "Permissions were granted but can't access the device");
                                    task.setDone(null);
                                } else {
                                    Log.d(TAG, "Permissions granted and device is accessible");
                                    task.setDone(manager.openDevice(device));
                                }
                            } else {
                                Log.d(TAG, "Extra permission was not granted");
                                task.setDone(null);
                            }
                            context.unregisterReceiver(this);
                        } else {
                            Log.d(TAG, "Got a permission for an unexpected device");
                            task.setDone(null);
                        }
                    }
                } else {
                    Log.d(TAG, "Unexpected action");
                    task.setDone(null);
                }
            }
        }, new IntentFilter(ACTION_USB_PERMISSION));
    }

    private UsbPermissionObtainer() {}
}
