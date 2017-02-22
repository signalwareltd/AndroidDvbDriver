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
import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.Serializable;
import java.util.List;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.usb.DvbUsbDeviceRegistry;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.dvbservice.tools.InetAddressTools;
import info.martinmarinov.dvbservice.tools.TsDumpFileUtils;

import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_OPEN_USB;

public class DvbService extends IntentService {
    private static final String TAG = DvbService.class.getSimpleName();

    public static final String BROADCAST_ACTION = "info.martinmarinov.dvbservice.DvbService.BROADCAST";

    private final static String DEVICE_FILTER = "DeviceFilter";
    private final static String STATUS_MESSAGE = "StatusMessage";

    public DvbService() {
        super(DvbService.class.getSimpleName());
    }

    static void requestOpen(Activity activity, DeviceFilter deviceFilter) {
        Intent intent = new Intent(activity, DvbService.class)
                .putExtra(DEVICE_FILTER, deviceFilter);
        activity.startService(intent);
    }

    static StatusMessage parseMessage(Intent intent) {
        return (StatusMessage) intent.getSerializableExtra(STATUS_MESSAGE);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        DeviceFilter deviceFilter = (DeviceFilter) intent.getSerializableExtra(DEVICE_FILTER);

        DvbServer dvbServer = null;
        try {
            dvbServer = new DvbServer(getDeviceFromFilter(deviceFilter));
            DvbServerPorts dvbServerPorts = dvbServer.bind(InetAddressTools.getLocalLoopback());
            dvbServer.open();
            // Device was opened! Tell client it's time to connect
            broadcastStatus(new StatusMessage(null, dvbServerPorts, deviceFilter));
            dvbServer.serve();
        } catch (Exception e) {
            e.printStackTrace();
            broadcastStatus(new StatusMessage(e, null, deviceFilter));
        } finally {
            if (dvbServer != null) dvbServer.close();
        }

        Log.d(TAG, "Finished");
    }

    private DvbDevice getDeviceFromFilter(DeviceFilter deviceFilter) throws DvbException {
        List<DvbDevice> dvbDevices = DvbUsbDeviceRegistry.getUsbDvbDevices(this);
        dvbDevices.addAll(TsDumpFileUtils.getDevicesForAllRecordings(this));

        for (DvbDevice dvbDevice : dvbDevices) {
            if (dvbDevice.getDeviceFilter().equals(deviceFilter)) return dvbDevice;
        }
        throw new DvbException(CANNOT_OPEN_USB, getString(R.string.device_no_longer_available));
    }

    private void broadcastStatus(StatusMessage statusMessage) {
        Intent intent = new Intent(BROADCAST_ACTION)
                        .putExtra(STATUS_MESSAGE, statusMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    class StatusMessage implements Serializable {
        final Exception exception;
        final DvbServerPorts serverAddresses;
        final DeviceFilter deviceFilter;

        private StatusMessage(Exception exception, DvbServerPorts serverAddresses, DeviceFilter deviceFilter) {
            this.exception = exception;
            this.serverAddresses = serverAddresses;
            this.deviceFilter = deviceFilter;
        }
    }
}
