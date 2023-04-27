package info.martinmarinov.drivers.usb.generic;

import static android.hardware.usb.UsbConstants.USB_DIR_OUT;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.locks.ReentrantLock;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDemux;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.usb.DvbUsbDevice;

public abstract class AbstractGenericDvbUsbDevice extends DvbUsbDevice {
    private final static int DEFAULT_USB_COMM_TIMEOUT_MS = 100;
    private final static long DEFAULT_READ_OR_WRITE_TIMEOUT_MS = 700L;

    private final ReentrantLock usbReentrantLock = new ReentrantLock();
    protected final UsbEndpoint controlEndpointIn;
    protected final UsbEndpoint controlEndpointOut;

    protected AbstractGenericDvbUsbDevice(
            UsbDevice usbDevice,
            Context context,
            DeviceFilter deviceFilter,
            DvbDemux dvbDemux,
            UsbEndpoint controlEndpointIn,
            UsbEndpoint controlEndpointOut
    ) throws DvbException {
        super(usbDevice, context, deviceFilter, dvbDemux);
        this.controlEndpointIn = controlEndpointIn;
        this.controlEndpointOut = controlEndpointOut;
    }

    private int bulkTransfer(UsbEndpoint endpoint, byte[] buffer, int offset, int length) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return usbDeviceConnection.bulkTransfer(endpoint, buffer, offset, length, DEFAULT_USB_COMM_TIMEOUT_MS);
        } else if (offset == 0) {
            return usbDeviceConnection.bulkTransfer(endpoint, buffer, length, DEFAULT_USB_COMM_TIMEOUT_MS);
        } else {
            byte[] tempbuff = new byte[length - offset];
            if (endpoint.getDirection() == USB_DIR_OUT) {
                System.arraycopy(buffer, offset, tempbuff, 0, length - offset);
                return usbDeviceConnection.bulkTransfer(endpoint, tempbuff, tempbuff.length, DEFAULT_USB_COMM_TIMEOUT_MS);
            } else {
                int read = usbDeviceConnection.bulkTransfer(endpoint, tempbuff, tempbuff.length, DEFAULT_USB_COMM_TIMEOUT_MS);
                if (read <= 0) {
                    return read;
                }
                System.arraycopy(tempbuff, 0, buffer, offset, read);
                return read;
            }
        }
    }

    protected void dvb_usb_generic_rw(@NonNull byte[] wbuf, int wlen, @Nullable byte[] rbuf, int rlen) throws DvbException {
        long startTime = System.currentTimeMillis();
        usbReentrantLock.lock();
        try {
            int bytesTransferred = 0;
            while (bytesTransferred < wlen) {
                int actlen = bulkTransfer(controlEndpointOut, wbuf, bytesTransferred, wlen - bytesTransferred);
                if (System.currentTimeMillis() - startTime > DEFAULT_READ_OR_WRITE_TIMEOUT_MS) {
                    actlen = -99999999;
                }
                if (actlen < 0) {
                    throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_send_control_message, actlen));
                }
                bytesTransferred += actlen;
            }

            if (rbuf != null && rlen >= 0) {
                bytesTransferred = 0;

                while (bytesTransferred < rlen) {
                    int actlen = bulkTransfer(controlEndpointIn, rbuf, bytesTransferred, rlen - bytesTransferred);
                    if (System.currentTimeMillis() - startTime > 2*DEFAULT_READ_OR_WRITE_TIMEOUT_MS) {
                        actlen = -99999999;
                    }
                    if (actlen < 0) {
                        throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_send_control_message, actlen));
                    }
                    bytesTransferred += actlen;
                }
            }
        } finally {
            usbReentrantLock.unlock();
        }
    }
}
