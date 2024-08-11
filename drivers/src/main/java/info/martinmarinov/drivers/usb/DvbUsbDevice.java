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

package info.martinmarinov.drivers.usb;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.UNSUPPORTED_PLATFORM;
import static info.martinmarinov.drivers.DvbException.ErrorCode.USB_PERMISSION_DENIED;
import static info.martinmarinov.drivers.tools.Retry.retry;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbDemux;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.Check;
import info.martinmarinov.drivers.tools.ThrowingCallable;
import info.martinmarinov.drivers.tools.ThrowingRunnable;
import info.martinmarinov.drivers.tools.UsbPermissionObtainer;
import info.martinmarinov.usbxfer.AlternateUsbInterface;
import info.martinmarinov.usbxfer.ByteSource;
import info.martinmarinov.usbxfer.UsbBulkSource;
import info.martinmarinov.usbxfer.UsbHiSpeedBulk;

public abstract class DvbUsbDevice extends DvbDevice {
    private final static int RETRIES = 4;

    public interface Creator {
        /**
         * Try to instantiate a {@link DvbDevice} with the provided {@link UsbDevice} instance.
         * @param usbDevice a usb device that is attached to the system
         * @param context the application context, used for accessing usb system service and obtaining permissions
         * @param filter
         * @return a {@link DvbDevice} instance to control the device if the current creator supports it
         * or null if the {@link UsbDevice} is not supported by the creator.
         */
        DvbDevice create(UsbDevice usbDevice, Context context, DeviceFilter filter) throws DvbException;
        Set<DeviceFilter> getSupportedDevices();
    }

    private final static String TAG = DvbUsbDevice.class.getSimpleName();

    private final UsbDevice usbDevice;
    protected final Resources resources;
    private final Context context;
    private final DeviceFilter deviceFilter;
    public final boolean isRtlSdrBlogV4;

    protected DvbFrontend frontend;
    protected DvbTuner tuner;
    protected UsbDeviceConnection usbDeviceConnection;
    private AlternateUsbInterface usbInterface;
    private DvbCapabilities capabilities;

    protected DvbUsbDevice(UsbDevice usbDevice, Context context, DeviceFilter deviceFilter, DvbDemux dvbDemux) throws DvbException {
        super(dvbDemux);
        this.usbDevice = usbDevice;
        this.isRtlSdrBlogV4 = isRtlSdrBlogV4(usbDevice);
        this.context = context;
        this.resources = context.getResources();
        this.deviceFilter = deviceFilter;
        if (!UsbHiSpeedBulk.IS_PLATFORM_SUPPORTED) throw new DvbException(UNSUPPORTED_PLATFORM, resources.getString(R.string.unsuported_platform));
    }

    private static boolean isRtlSdrBlogV4(UsbDevice usbDevice) {
        return "RTLSDRBlog".equals(usbDevice.getManufacturerName()) && "Blog V4".equals(usbDevice.getProductName());
    }

    @Override
    public final void open() throws DvbException {
        try {
            usbDeviceConnection = UsbPermissionObtainer.obtainFdFor(context, usbDevice).get();
            if (usbDeviceConnection == null)
                throw new DvbException(USB_PERMISSION_DENIED, resources.getString(R.string.cannot_open_usb_connection));
            usbInterface = getUsbInterface();

            retry(RETRIES, new ThrowingRunnable<DvbException>() {
                @Override
                public void run() throws DvbException {
                    powerControl(true);
                    readConfig();

                    frontend = frontendAttatch();
                    frontend.attach();
                    // capabilities should only be accessed after frontend is attached
                    capabilities = frontend.getCapabilities();
                    tuner = tunerAttatch();
                    tuner.attatch();

                    frontend.init(tuner);
                    init();
                }
            });
        } catch (DvbException e) {
            throw e;
        } catch (Exception e) {
            throw new DvbException(BAD_API_USAGE, e);
        }
    }

    @Override
    public final void close() throws IOException {
        super.close();
        if (usbDeviceConnection != null) {
            if (frontend != null) frontend.release();
            if (tuner != null) tuner.release();

            try {
                powerControl(false);
            } catch (DvbException e) {
                e.printStackTrace();
            }

            usbDeviceConnection.close();
        }
        Log.d(TAG, "closed");
    }

    @Override
    public DeviceFilter getDeviceFilter() {
        return deviceFilter;
    }

    @Override
    public String toString() {
        return deviceFilter.getName();
    }

    @Override
    public void setPidFilter(int... pids) throws DvbException {
        super.setPidFilter(pids);
        frontend.setPids(pids);
    }

    @Override
    public void disablePidFilter() throws DvbException {
        super.disablePidFilter();
        frontend.disablePidFilter();
    }

    @Override
    public DvbCapabilities readCapabilities() throws DvbException {
        Check.notNull(capabilities, "Frontend not initialized");
        return capabilities;
    }

    @Override
    protected void tuneTo(final long freqHz, final long bandwidthHz, @NonNull final DeliverySystem deliverySystem) throws DvbException {
        Check.notNull(frontend, "Frontend not initialized");
        retry(RETRIES, new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                frontend.setParams(freqHz, bandwidthHz, deliverySystem);
            }
        });
    }

    @Override
    public int readSnr() throws DvbException {
        Check.notNull(frontend, "Frontend not initialized");
        return retry(RETRIES, new ThrowingCallable<Integer, DvbException>() {
            @Override
            public Integer call() throws DvbException {
                return frontend.readSnr();
            }
        });
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        Check.notNull(frontend, "Frontend not initialized");
        return retry(RETRIES, new ThrowingCallable<Integer, DvbException>() {
            @Override
            public Integer call() throws DvbException {
                return frontend.readRfStrengthPercentage();
            }
        });
    }

    @Override
    public int readBitErrorRate() throws DvbException {
        Check.notNull(frontend, "Frontend not initialized");
        return retry(RETRIES, new ThrowingCallable<Integer, DvbException>() {
            @Override
            public Integer call() throws DvbException {
                return frontend.readBer();
            }
        });
    }

    @Override
    public Set<DvbStatus> getStatus() throws DvbException {
        Check.notNull(frontend, "Frontend not initialized");
        return retry(RETRIES, new ThrowingCallable<Set<DvbStatus>, DvbException>() {
            @Override
            public Set<DvbStatus> call() throws DvbException {
                return frontend.getStatus();
            }
        });
    }

    protected int getNumRequests() {
        return 40;
    }

    protected int getNumPacketsPerRequest() {
        return 10;
    }

    @Override
    protected ByteSource createTsSource() {
        return new UsbBulkSource(usbDeviceConnection, getUsbEndpoint(), usbInterface, getNumRequests(), getNumPacketsPerRequest());
    }

    /** API for drivers to implement **/

    // Turn tuner on or off
    protected abstract void powerControl(boolean turnOn) throws DvbException;

    // Allows determining the tuner type so correct commands could be used later
    protected abstract void readConfig() throws DvbException;

    protected abstract DvbFrontend frontendAttatch() throws DvbException;
    protected abstract DvbTuner tunerAttatch() throws DvbException;

    protected abstract void init() throws DvbException;
    protected abstract AlternateUsbInterface getUsbInterface();
    protected abstract UsbEndpoint getUsbEndpoint();
}
