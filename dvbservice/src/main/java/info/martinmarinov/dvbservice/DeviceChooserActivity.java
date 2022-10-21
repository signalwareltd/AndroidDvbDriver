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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.usb.DvbUsbDeviceRegistry;
import info.martinmarinov.dvbservice.dialogs.ListPickerFragmentDialog;
import info.martinmarinov.dvbservice.tools.StackTraceSerializer;
import info.martinmarinov.dvbservice.tools.TsDumpFileUtils;

import static info.martinmarinov.drivers.DvbException.ErrorCode.NO_DVB_DEVICES_FOUND;

public class DeviceChooserActivity extends FragmentActivity implements ListPickerFragmentDialog.OnSelected<DeviceFilter> {
    private final static int RESULT_ERROR = RESULT_FIRST_USER;

    private final static String CONTRACT_ERROR_CODE = "ErrorCode";
    private final static String CONTRACT_CONTROL_PORT = "ControlPort";
    private final static String CONTRACT_TRANSFER_PORT = "TransferPort";
    private final static String CONTRACT_DEVICE_NAME = "DeviceName";
    private final static String CONTRACT_RAW_TRACE = "RawTrace";
    private final static String CONTRACT_USB_PRODUCT_IDS = "ProductIds";
    private final static String CONTRACT_USB_VENDOR_IDS = "VendorIds";

    private final Intent response = new Intent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.progress);

        // Set to receive info from the DvbService
        IntentFilter statusIntentFilter = new IntentFilter(DvbService.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(new DvbServiceResponseReceiver(), statusIntentFilter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            List<DvbDevice> dvbDevices = DvbUsbDeviceRegistry.getUsbDvbDevices(this);
            List<DvbDevice> dvbFileDevices = TsDumpFileUtils.getDevicesForAllRecordings(this); // these are only for debugging purposes
            dvbDevices.addAll(dvbFileDevices);

            if (dvbDevices.isEmpty()) throw new DvbException(NO_DVB_DEVICES_FOUND, getString(info.martinmarinov.drivers.R.string.no_devices_found));

            List<DeviceFilter> deviceFilters = new ArrayList<>(dvbDevices.size());
            for (DvbDevice dvbDevice : dvbDevices) {
                deviceFilters.add(dvbDevice.getDeviceFilter());
            }

            if (deviceFilters.size() > 1 || !dvbFileDevices.isEmpty()) {
                ListPickerFragmentDialog.showOneInstanceOnly(getSupportFragmentManager(), deviceFilters);
            } else {
                DvbService.requestOpen(this, deviceFilters.get(0));
            }
        } catch (DvbException e) {
            handleException(e);
        }
    }

    @Override
    public void onListPickerDialogItemSelected(@NonNull DeviceFilter deviceFilter) {
        DvbService.requestOpen(this, deviceFilter);
    }

    @Override
    public void onListPickerDialogCanceled() {
        finishWith(RESULT_CANCELED);
    }

    // Open selected device

    private class DvbServiceResponseReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            DvbService.StatusMessage statusMessage = DvbService.parseMessage(intent);
            if (statusMessage.exception != null) {
                Exception exception = statusMessage.exception;
                if (exception instanceof DvbException) {
                    handleException((DvbException) exception);
                } else if (exception instanceof IOException) {
                    handleException(new DvbException(DvbException.ErrorCode.IO_EXCEPTION, exception));
                } else if (exception instanceof RuntimeException) {
                    throw (RuntimeException) exception;
                } else {
                    throw new RuntimeException(exception);
                }
            } else {
                handleSuccess(statusMessage.deviceFilter, statusMessage.serverAddresses);
            }
        }
    }

    // API for returning response to caller

    private void handleSuccess(DeviceFilter deviceFilter, DvbServerPorts addresses) {
        int[] productIds = new int[] {deviceFilter.getProductId()};
        int[] vendorIds = new int[] {deviceFilter.getVendorId()};

        response.putExtra(CONTRACT_DEVICE_NAME, deviceFilter.getName());
        response.putExtra(CONTRACT_USB_PRODUCT_IDS, productIds);
        response.putExtra(CONTRACT_USB_VENDOR_IDS, vendorIds);

        response.putExtra(CONTRACT_CONTROL_PORT, addresses.getControlPort());
        response.putExtra(CONTRACT_TRANSFER_PORT, addresses.getTransferPort());

        finishWith(RESULT_OK);
    }

    private void handleException(DvbException e) {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        Collection<UsbDevice> availableDevices = manager.getDeviceList().values();
        int[] productIds = new int[availableDevices.size()];
        int[] vendorIds = new int[availableDevices.size()];

        int id = 0;
        for (UsbDevice usbDevice : availableDevices) {
            productIds[id] = usbDevice.getProductId();
            vendorIds[id] = usbDevice.getVendorId();
            id++;
        }

        response.putExtra(CONTRACT_ERROR_CODE, e.getErrorCode().name());
        response.putExtra(CONTRACT_RAW_TRACE, StackTraceSerializer.serialize(e));
        response.putExtra(CONTRACT_USB_PRODUCT_IDS, productIds);
        response.putExtra(CONTRACT_USB_VENDOR_IDS, vendorIds);

        finishWith(RESULT_ERROR);
    }

    private void finishWith(int code) {
        if (getParent() == null) {
            setResult(code, response);
        } else {
            getParent().setResult(code, response);
        }
        finish();
    }
}
