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

package info.martinmarinov.drivers.usb.cxusb;

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.tools.ThrowingRunnable;
import info.martinmarinov.drivers.usb.DvbTuner;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;
import static info.martinmarinov.drivers.DvbException.ErrorCode.IO_EXCEPTION;
import static info.martinmarinov.drivers.usb.cxusb.Si2157.Type.SI2157_CHIPTYPE_SI2146;

class Si2157 implements DvbTuner {
    enum Type {
        SI2157_CHIPTYPE_SI2157,
        SI2157_CHIPTYPE_SI2146
    }

    private final static String TAG = Si2157.class.getSimpleName();
    private final static int TIMEOUT_MS = 80;
    private final static int SI2157_ARGLEN = 30;
    private final static boolean INVERSION = false;

    private final static int SI2158_A20 = (('A' << 24) | (58 << 16) | ('2' << 8) | '0');
    private final static int SI2148_A20 = (('A' << 24) | (48 << 16) | ('2' << 8) | '0');
    private final static int SI2157_A30 = (('A' << 24) | (57 << 16) | ('3' << 8) | '0');
    private final static int SI2147_A30 = (('A' << 24) | (47 << 16) | ('3' << 8) | '0');
    private final static int SI2146_A10 = (('A' << 24) | (46 << 16) | ('1' << 8) | '0');

    private final Resources resources;
    private final I2cAdapter i2c;
    private final I2cAdapter.I2GateControl i2GateControl;
    private final int addr;
    private final boolean if_port;
    private final Type chiptype;

    private int if_frequency;
    private boolean active = false;

    Si2157(Resources resources, I2cAdapter i2c, I2cAdapter.I2GateControl i2GateControl, int addr, boolean if_port, Type chiptype) {
        this.resources = resources;
        this.i2c = i2c;
        this.i2GateControl = i2GateControl;
        this.addr = addr;
        this.if_port = if_port;
        this.chiptype = chiptype;
    }

    private final static byte[] EMPTY = new byte[0];
    private synchronized @NonNull byte[] si2157_cmd_execute(@NonNull byte[] wargs, int wlen, int rlen) throws DvbException {
        if (wlen > 0 && wlen == wargs.length) {
            i2c.send(addr, wargs, wlen);
        } else {
            if (wlen != 0) throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
        }

        if (rlen > 0) {
            byte[] rout = new byte[rlen];
            long endTime = System.nanoTime() + TIMEOUT_MS * 1_000_000L;

            while (System.nanoTime() < endTime) {
                i2c.recv(addr, rout, rlen);

                if ((((rout[0] & 0xFF) >> 7) & 0x01) != 0) {
                    break;
                }
            }

            if ((((rout[0] & 0xFF) >> 7) & 0x01) == 0) {
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.timed_out_read_from_register));
            }
            return rout;
        }

        return EMPTY;
    }

    @Override
    public void attatch() throws DvbException {
        if_frequency = 5_000_000; /* default value of property 0x0706 */

        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                /* check if the tuner is there */
                si2157_cmd_execute(new byte[0], 0, 1);
            }
        });
    }

    @Override
    public void release() {
        try {
            i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
                @Override
                public void run() throws DvbException {
                    si2157_cmd_execute(new byte[]{(byte) 0x16, (byte) 0x00}, 2, 1);
                }
            });
        } catch (DvbException e) {
            e.printStackTrace();
        }
        active = false;
    }

    @Override
    public void init() throws DvbException {
        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
            /* Returned IF frequency is garbage when firmware is not running */
                byte[] res = si2157_cmd_execute(new byte[]{(byte) 0x15, (byte) 0x00, (byte) 0x06, (byte) 0x07}, 4, 4);

                int if_freq_khz = (res[2] & 0xFF) | ((res[3] & 0xFF) << 8);
                Log.d(TAG, "IF frequency KHz " + if_freq_khz);

                if (if_freq_khz != if_frequency / 1000) {
        	        /* power up */
                    if (chiptype == SI2157_CHIPTYPE_SI2146) {
                        si2157_cmd_execute(new byte[]{(byte) 0xc0, (byte) 0x05, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x0b, (byte) 0x00, (byte) 0x00, (byte) 0x01}, 9, 1);
                    } else {
                        si2157_cmd_execute(new byte[]{(byte) 0xc0, (byte) 0x00, (byte) 0x0c, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x01}, 15, 1);
                    }

                    /* query chip revision */
                    res = si2157_cmd_execute(new byte[]{(byte) 0x02}, 1, 13);

                    int chip_id = ((res[1] & 0xFF) << 24) | ((res[2] & 0xFF) << 16) | ((res[3] & 0xFF) << 8) | (res[4] & 0xFF);

                    Log.d(TAG, "found a Silicon Labs Si21" + (res[2] & 0xFF) + "-" + ((char) (res[1] & 0xFF)) + " " + ((char) (res[3] & 0xFF)) + " " + ((char) (res[4] & 0xFF)));

                    switch (chip_id) {
                        case SI2158_A20:
                        case SI2148_A20:
                            loadFirmware(R.raw.dvbtunersi2158a2001fw);
                            break;
                        case SI2157_A30:
                        case SI2147_A30:
                        case SI2146_A10:
                            Log.d(TAG, "No need to load fw");
                            break;
                        default:
                            throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_tuner_on_device));
                    }

                    /* reboot the tuner with new firmware? */
                    si2157_cmd_execute(new byte[]{(byte) 0x01, (byte) 0x01}, 2, 1);

                    /* query firmware version */
                    res = si2157_cmd_execute(new byte[]{(byte) 0x11}, 1, 10);

                    Log.d(TAG, "firmware version: " + ((char) (res[6] & 0xFF)) + "." + ((char) (res[7] & 0xFF)) + "." + (res[8] & 0xFF));
                }
                active = true;
            }
        });
    }

    private void loadFirmware(int firmware) throws DvbException {
        try {
            byte[] fw = readFirmware(firmware);

            /* firmware should be n chunks of 17 bytes */
            if (fw.length % 17 != 0) {
                throw new DvbException(IO_EXCEPTION, resources.getString(R.string.cannot_load_firmware));
            }

            Log.d(TAG, "Downloading firmware");

            for (int remaining = fw.length; remaining > 0; remaining -= 17) {
                int len = fw[fw.length - remaining] & 0xFF;
                if (len > SI2157_ARGLEN) {
                    throw new DvbException(IO_EXCEPTION, resources.getString(R.string.cannot_load_firmware));
                }
                byte[] args = new byte[len];
                System.arraycopy(fw, fw.length - remaining + 1, args, 0, len);
                si2157_cmd_execute(args, len, 1);
            }

        } catch (IOException e) {
            throw new DvbException(IO_EXCEPTION, e);
        }
    }

    private byte[] readFirmware(int resource) throws IOException {
        InputStream inputStream = resources.openRawResource(resource);
        //noinspection TryFinallyCanBeTryWithResources
        try {
            byte[] fw = new byte[inputStream.available()];
            if (inputStream.read(fw) != fw.length) {
                throw new DvbException(IO_EXCEPTION, resources.getString(R.string.cannot_load_firmware));
            }
            return fw;
        } finally {
            inputStream.close();
        }
    }

    @Override
    public void setParams(final long frequency, final long bandwidthHz, final DeliverySystem deliverySystem) throws DvbException {
        i2GateControl.runInOpenGate(new ThrowingRunnable<DvbException>() {
            @Override
            public void run() throws DvbException {
                if (!active) {
                    throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
                }

                int bandwidth;
                if (bandwidthHz <= 6_000_000L) {
                    bandwidth = 0x06;
                } else if (bandwidthHz <= 7_000_000L) {
                    bandwidth = 0x07;
                } else if (bandwidthHz <= 8_000_000L) {
                    bandwidth = 0x08;
                } else {
                    bandwidth = 0x0f;
                }

                int delivery_system;
                int if_frequency;
                switch (deliverySystem) {
                    case DVBT:
                    case DVBT2: /* it seems DVB-T and DVB-T2 both are 0x20 here */
                        delivery_system = 0x20;
                        if_frequency = 5_000_000;
                        break;
                    case DVBC:
                        delivery_system = 0x30;
                        if_frequency = 5_000_000;
                        break;
                    default:
                        throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
                }

                //noinspection ConstantConditions
                si2157_cmd_execute(
                        new byte[]{
                                (byte) 0x15, (byte) 0x00, (byte) 0x03, (byte) 0x07, (byte) (delivery_system | bandwidth), (byte) (INVERSION ? 1 : 0)
                        }, 6, 4
                );

                if (chiptype == SI2157_CHIPTYPE_SI2146) {
                    si2157_cmd_execute(new byte[]{(byte) 0x14, (byte) 0x00, (byte) 0x02, (byte) 0x07, (byte) (if_port ? 1 : 0), (byte) 0x01}, 6, 4);
                } else {
                    si2157_cmd_execute(new byte[]{(byte) 0x14, (byte) 0x00, (byte) 0x02, (byte) 0x07, (byte) (if_port ? 1 : 0), (byte) 0x00}, 6, 4);
                }

	            /* set if frequency if needed */
                if (if_frequency != Si2157.this.if_frequency) {
                    si2157_cmd_execute(new byte[]{(byte) 0x14, (byte) 0x00, (byte) 0x06, (byte) 0x07, (byte) ((if_frequency / 1000) & 0xff), (byte) (((if_frequency / 1000) >> 8) & 0xff)}, 6, 4);
                    Si2157.this.if_frequency = if_frequency;
                }

	            /* set frequency */
                si2157_cmd_execute(new byte[]{
                        (byte) 0x41, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                        (byte) (frequency & 0xff),
                        (byte) ((frequency >> 8) & 0xff),
                        (byte) ((frequency >> 16) & 0xff),
                        (byte) ((frequency >> 24) & 0xff)
                }, 8, 41);
            }
        });
    }

    @Override
    public long getIfFrequency() throws DvbException {
        return if_frequency;
    }
}
