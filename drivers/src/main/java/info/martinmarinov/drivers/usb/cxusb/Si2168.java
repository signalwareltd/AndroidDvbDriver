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
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.tools.SetUtils;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.usb.cxusb.Si2168Data.Si2168Chip;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;
import static info.martinmarinov.drivers.DvbException.ErrorCode.IO_EXCEPTION;
import static info.martinmarinov.drivers.DvbException.ErrorCode.UNSUPPORTED_BANDWIDTH;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_CARRIER;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_LOCK;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_SIGNAL;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_SYNC;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_VITERBI;

class Si2168 implements DvbFrontend {
    // TODO: si2168_select and si2168_deselect for i2c toggling?

    private final static String TAG = Si2168.class.getSimpleName();
    private final static int TIMEOUT_MS = 70;
    private final static int NO_STREAM_ID_FILTER = 0xFFFFFFFF;
    private final static int DVBT2_STREAM_ID = 0;
    private final static int DVBC_SYMBOL_RATE = 0;

    static final int SI2168_TS_PARALLEL = 0x06;
    private final static int SI2168_ARGLEN = 30;

    private final Resources resources;
    private final I2cAdapter i2c;
    private final int addr;
    private final int ts_mode;
    private final boolean ts_clock_inv;

    private int version;
    private boolean warm = false;
    private boolean active = false;
    private Si2168Chip chip;
    private DvbTuner tuner;
    private DeliverySystem deliverySystem;

    Si2168(Resources resources, I2cAdapter i2c, int addr, int ts_mode, boolean ts_clock_inv) {
        this.resources = resources;
        this.i2c = i2c;
        this.addr = addr;
        this.ts_mode = ts_mode;
        this.ts_clock_inv = ts_clock_inv;
    }

    private void si2168_cmd_execute_wr(byte[] wargs, int wlen) throws DvbException {
        si2168_cmd_execute(wargs, wlen, 0);
    }

    private final static byte[] EMPTY = new byte[0];
    private synchronized @NonNull byte[] si2168_cmd_execute(@Nullable byte[] wargs, int wlen, int rlen) throws DvbException {
        if (wlen > 0 && wlen <= wargs.length) {
            i2c.send(addr, wargs, wlen);
        } else {
            if (wargs != null || wlen != 0) throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
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

            /* error bit set? */
            if ((((rout[0] & 0xFF) >> 6) & 0x01) != 0) {
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.failed_to_read_from_register));
            }

            if ((((rout[0] & 0xFF) >> 7) & 0x01) == 0) {
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.timed_out_read_from_register));
            }

            return rout;
        }

        return EMPTY;
    }

    @Override
    public DvbCapabilities getCapabilities() {
        return Si2168Data.CAPABILITIES;
    }

    @Override
    public void attatch() throws DvbException {
        /* Initialize */
        si2168_cmd_execute_wr(
                new byte[] {(byte) 0xc0, (byte) 0x12, (byte) 0x00, (byte) 0x0c, (byte) 0x00, (byte) 0x0d, (byte) 0x16, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00},
                13
        );

        /* Power up */
        si2168_cmd_execute(
                new byte[] {(byte) 0xc0, (byte) 0x06, (byte) 0x01, (byte) 0x0f, (byte) 0x00, (byte) 0x20, (byte) 0x20, (byte) 0x01},
                8, 1
        );

        /* Query chip revision */
        byte[] chipInfo = si2168_cmd_execute(new byte[] {0x02}, 1, 13);

        int chipId = ((chipInfo[1] & 0xFF) << 24) | ((chipInfo[2] & 0xFF) << 16) | ((chipInfo[3] & 0xFF) << 8) | (chipInfo[4] & 0xFF);
        chip = Si2168Chip.fromId(chipId);
        if (chip == null) throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_tuner_on_device));

        version = ((chipInfo[1] & 0xFF) << 24) | (((chipInfo[3] & 0xFF) - '0') << 16) | (((chipInfo[4] & 0xFF) - '0') << 8) | (chipInfo[5] & 0xFF);

        Log.d(TAG, "Found chip "+chip);
    }

    @Override
    public void release() {
        active = false;

        /* Firmware B 4.0-11 or later loses warm state during sleep */
        if (version >= ('B' << 24 | 4 << 16 | 11)) {
            warm = false;
        }

        try {
            si2168_cmd_execute_wr(new byte[]{(byte) 0x13}, 1);
        } catch (DvbException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(DvbTuner tuner) throws DvbException {
        this.tuner = tuner;

        /* initialize */
        si2168_cmd_execute_wr(new byte[] {(byte) 0xc0, (byte) 0x12, (byte) 0x00, (byte) 0x0c, (byte) 0x00, (byte) 0x0d, (byte) 0x16, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00}, 13);

        if (warm) {
		    /* resume */
            si2168_cmd_execute(new byte[] {(byte) 0xc0, (byte) 0x06, (byte) 0x08, (byte) 0x0f, (byte) 0x00, (byte) 0x20, (byte) 0x21, (byte) 0x01}, 8, 1);
            si2168_cmd_execute(new byte[] {(byte) 0x85}, 1, 1);
        } else {
	        /* power up */
	        si2168_cmd_execute(new byte[] {(byte) 0xc0, (byte) 0x06, (byte) 0x01, (byte) 0x0f, (byte) 0x00, (byte) 0x20, (byte) 0x20, (byte) 0x01}, 8, 1);

            Log.d(TAG, "Uploading firmware to "+chip);

            try {
                byte[] fw = readFirmware(chip.firmwareFile);

                if ((fw.length % 17 == 0) && ((fw[0] & 0xFF) > 5)) {
                    Log.d(TAG, "firmware is in the new format");
                    for (int remaining = fw.length; remaining > 0; remaining -= 17) {
                        int len = fw[fw.length - remaining];
                        if (len > SI2168_ARGLEN) {
                            throw new DvbException(IO_EXCEPTION, resources.getString(R.string.cannot_load_firmware));
                        }

                        byte[] args = new byte[len];
                        System.arraycopy(fw, fw.length - remaining + 1, args, 0, len);
                        si2168_cmd_execute(args, len, 1);
                    }
                } else if (fw.length % 8 == 0) {
                    Log.d(TAG, "firmware is in the old format");
                    for (int remaining = fw.length; remaining > 0; remaining -= 8) {
                        int len = 8;
                        byte[] args = new byte[len];
                        System.arraycopy(fw, fw.length - remaining, args, 0, len);
                        si2168_cmd_execute(args, len, 1);
                    }
                } else {
                    throw new DvbException(IO_EXCEPTION, resources.getString(R.string.cannot_load_firmware));
                }
            } catch (IOException e) {
                throw new DvbException(IO_EXCEPTION, e);
            }

            si2168_cmd_execute(new byte[] {0x01, 0x01}, 2, 1);

        	/* query firmware version */
        	byte[] fwVerRaw = si2168_cmd_execute(new byte[] {(byte) 0x11}, 1, 10);

            version = (((fwVerRaw[9] & 0xFF) + '@') << 24) | (((fwVerRaw[6] & 0xFF) - '0') << 16) | (((fwVerRaw[7] & 0xFF) - '0') << 8) | (fwVerRaw[8] & 0xFF);
            Log.d(TAG, "firmware version: "+((char) ((version >> 24) & 0xff))+" "+((version >> 16) & 0xff)+"."+((version >> 8) & 0xff)+"."+(version & 0xff));

        	/* set ts mode */

            byte[] args = new byte[] {(byte) 0x14, (byte) 0x00, (byte) 0x01, (byte) 0x10, (byte) 0x10, (byte) 0x00};
            args[4] |= ts_mode;

            si2168_cmd_execute(args, 6, 4);
            // skip ts_clock_gapped since this is always false from what I can see
            warm = true;
        }
        active = true;
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
    public void setParams(long frequency, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        if (!active) {
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
        }

        int delivery_system;
        switch (deliverySystem) {
            case DVBT:
                delivery_system = 0x20;
                break;
            case DVBC:
                delivery_system = 0x30;
                break;
            case DVBT2:
                delivery_system = 0x70;
                break;
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }

        int bandwidth;
        if (bandwidthHz == 0) {
            throw new DvbException(UNSUPPORTED_BANDWIDTH, resources.getString(R.string.invalid_bw));
        } else if (bandwidthHz <= 2_000_000L) {
            bandwidth = 0x02;
        } else if (bandwidthHz <= 5_000_000L) {
            bandwidth = 0x05;
        } else if (bandwidthHz <= 6_000_000L) {
            bandwidth = 0x06;
        } else if (bandwidthHz <= 7_000_000L) {
            bandwidth = 0x07;
        } else if (bandwidthHz <= 8_000_000L) {
            bandwidth = 0x08;
        } else if (bandwidthHz <= 9_000_000L) {
            bandwidth = 0x09;
        } else if (bandwidthHz <= 10_000_000L) {
            bandwidth = 0x0a;
        } else {
            bandwidth = 0x0f;
        }

        /* program tuner */
        tuner.setParams(frequency, bandwidthHz, deliverySystem);

        si2168_cmd_execute(new byte[] {(byte) 0x88, (byte) 0x02, (byte) 0x02, (byte) 0x02, (byte) 0x02}, 5, 5);

        /* that has no big effect */
        switch (deliverySystem) {
            case DVBT:
                si2168_cmd_execute(new byte[] {(byte) 0x89, (byte) 0x21, (byte) 0x06, (byte) 0x11, (byte) 0xff, (byte) 0x98}, 6, 3);
                break;
            case DVBC:
                si2168_cmd_execute(new byte[] {(byte) 0x89, (byte) 0x21, (byte) 0x06, (byte) 0x11, (byte) 0x89, (byte) 0xf0}, 6, 3);
                break;
            case DVBT2:
                si2168_cmd_execute(new byte[] {(byte) 0x89, (byte) 0x21, (byte) 0x06, (byte) 0x11, (byte) 0x89, (byte) 0x20}, 6, 3);
                break;
        }

        if (deliverySystem == DeliverySystem.DVBT2) {
            /* select PLP */
            //noinspection PointlessBitwiseExpression,ConstantConditions
            si2168_cmd_execute(new byte[] {
                    (byte) 0x52, (byte) (DVBT2_STREAM_ID & 0xFF), DVBT2_STREAM_ID == NO_STREAM_ID_FILTER ? 0 : (byte) 1
            }, 3, 1);
        }

        si2168_cmd_execute(new byte[] {(byte) 0x51, (byte) 0x03}, 2, 12);
        si2168_cmd_execute(new byte[] {(byte) 0x12, (byte) 0x08, (byte) 0x04}, 3, 3);
        si2168_cmd_execute(new byte[] {(byte) 0x14, (byte) 0x00, (byte) 0x0c, (byte) 0x10, (byte) 0x12, (byte) 0x00}, 6, 4);
        si2168_cmd_execute(new byte[] {(byte) 0x14, (byte) 0x00, (byte) 0x06, (byte) 0x10, (byte) 0x24, (byte) 0x00}, 6, 4);
        si2168_cmd_execute(new byte[] {(byte) 0x14, (byte) 0x00, (byte) 0x07, (byte) 0x10, (byte) 0x00, (byte) 0x24}, 6, 4);
        si2168_cmd_execute(new byte[] {(byte) 0x14, (byte) 0x00, (byte) 0x0a, (byte) 0x10, (byte) (delivery_system | bandwidth), (byte) 0x00}, 6, 4);

        /* set DVB-C symbol rate */
        if (deliverySystem == DeliverySystem.DVBC) {
            //noinspection PointlessBitwiseExpression
            si2168_cmd_execute(new byte[] {
                    (byte) 0x14, (byte) 0x00, (byte) 0x02 , (byte) 0x11, (byte) (((DVBC_SYMBOL_RATE / 1000) >> 0) & 0xff), (byte) ( ((DVBC_SYMBOL_RATE / 1000) >> 8) & 0xff)
            }, 6, 4);
        }

        si2168_cmd_execute(new byte[] {(byte) 0x14, (byte) 0x00, (byte) 0x0f, (byte) 0x10, (byte) 0x10, (byte) 0x00}, 6, 4);
        si2168_cmd_execute(new byte[] {(byte) 0x14, (byte) 0x00, (byte) 0x09, (byte) 0x10, (byte) 0xe3, (byte) (0x08 | (ts_clock_inv ? 0x00 : 0x10))}, 6, 4);
        si2168_cmd_execute(new byte[] {(byte) 0x14, (byte) 0x00, (byte) 0x08, (byte) 0x10, (byte) 0xd7, (byte) (0x05 | (ts_clock_inv ? 0x00 : 0x10))}, 6, 4);
        si2168_cmd_execute(new byte[] {(byte) 0x14, (byte) 0x00, (byte) 0x01, (byte) 0x12, (byte) 0x00, (byte) 0x00}, 6, 4);
        si2168_cmd_execute(new byte[] {(byte) 0x14, (byte) 0x00, (byte) 0x01, (byte) 0x03, (byte) 0x0c, (byte) 0x00}, 6, 4);
        si2168_cmd_execute(new byte[] {(byte) 0x85}, 1, 1);

        this.deliverySystem = deliverySystem;
    }

    @Override
    public int readSnr() throws DvbException {
        return -1;
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        if (!getStatus().contains(FE_HAS_SIGNAL)) return 0;
        return 100;
    }

    @Override
    public int readBer() throws DvbException {
        if (!getStatus().contains(FE_HAS_VITERBI)) return 0;

        byte[] res = si2168_cmd_execute(new byte[] { (byte) 0x82, (byte) 0x00 }, 2, 3);

        /*
		 * Firmware returns [0, 255] mantissa and [0, 8] exponent.
		 * Convert to DVB API: mantissa * 10^(8 - exponent) / 10^8
		 */
        int diff = 8 - (res[1] & 0xFF);
        int utmp = diff < 0 ? 0 : (diff > 8 ? 8 : diff);
        long bitErrors = 1;
        for (int i = 0; i < utmp; i++) {
            bitErrors = bitErrors * 10;
        }

        bitErrors = (res[2] & 0xFF) * bitErrors;
        long bitCount = 10_000_000L; /* 10^8 */

        return (int) ((bitErrors * 1_000_000L) / bitCount);
    }

    @Override
    public Set<DvbStatus> getStatus() throws DvbException {
        if (!active) {
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
        }

        byte[] res;
        switch (deliverySystem) {
            case DVBT:
                res = si2168_cmd_execute(new byte[] {(byte) 0xa0, (byte) 0x01}, 2, 13);
                break;
            case DVBC:
                res = si2168_cmd_execute(new byte[] {(byte) 0x90, (byte) 0x01}, 2, 9);
                break;
            case DVBT2:
                res = si2168_cmd_execute(new byte[] {(byte) 0x50, (byte) 0x01}, 2, 14);
                break;
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }

        switch (((res[2] & 0xFF) >> 1) & 0x03) {
            case 0x01:
		        return SetUtils.setOf(FE_HAS_SIGNAL, FE_HAS_CARRIER);
            case 0x03:
		        return SetUtils.setOf(FE_HAS_SIGNAL, FE_HAS_CARRIER, FE_HAS_VITERBI, FE_HAS_SYNC, FE_HAS_LOCK);
        }
        return SetUtils.setOf();
    }

    @Override
    public void setPids(int... pids) throws DvbException {
        // no-op
    }

    @Override
    public void disablePidFilter() throws DvbException {
        // no-op
    }
}
