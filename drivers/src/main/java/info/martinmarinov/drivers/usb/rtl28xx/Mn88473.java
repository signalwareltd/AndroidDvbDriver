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

package info.martinmarinov.drivers.usb.rtl28xx;

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.DvbMath;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.tools.SetUtils;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;
import static info.martinmarinov.drivers.DvbException.ErrorCode.IO_EXCEPTION;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_CARRIER;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_LOCK;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_SIGNAL;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_SYNC;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_VITERBI;
import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;

class Mn88473 implements DvbFrontend {
    private final static String TAG = Mn88473.class.getSimpleName();

    private final static DvbCapabilities CAPABILITIES = new DvbCapabilities(
            174000000L,
            862000000L,
            166667L,
            SetUtils.setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2, DeliverySystem.DVBC));

    private final static int I2C_WR_MAX = 22;
    private final static int[] I2C_ADDRESS = new int[] { 0x18, 0x1a, 0x1c };
    private final static long XTAL = 25_000_000L;

    private final Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter;
    private final I2cAdapter.I2GateControl i2GateController;
    private final Resources resources;

    private DeliverySystem currentDeliverySystem;
    private Set<DvbStatus> cachedStatus = SetUtils.setOf();
    private long cachedStatusTime = 0;

    Mn88473(Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter, I2cAdapter.I2GateControl i2GateController, Resources resources) {
        this.i2cAdapter = i2cAdapter;
        this.i2GateController = i2GateController;
        this.resources = resources;
    }

    private void write(int addressId, int reg, byte[] bytes) throws DvbException {
        write(addressId, reg, bytes, bytes.length);
    }

    private void write(int addressId, int reg, byte[] value, int len) throws DvbException {
        if (len + 1 > I2C_WR_MAX) throw new DvbException(BAD_API_USAGE, resources.getString(R.string.i2c_communication_failure));

        byte[] buf = new byte[len+1];

        buf[0] = (byte) reg;
        System.arraycopy(value, 0, buf, 1, len);

        i2cAdapter.transfer(I2C_ADDRESS[addressId], 0, buf, len + 1);
    }

    private void writeReg(int addressId, int reg, int val) throws DvbException {
        write(addressId, reg, new byte[] { (byte) val });
    }

    private void read(int addressId, int reg, byte[] val, int len) throws DvbException {
        i2cAdapter.transfer(
                I2C_ADDRESS[addressId], 0, new byte[] {(byte) reg}, 1,
                I2C_ADDRESS[addressId], I2C_M_RD, val, len
        );
    }

    private int readReg(int addressId, int reg) throws DvbException {
        byte[] ans = new byte[1];
        read(addressId, reg, ans, ans.length);
        return ans[0] & 0xFF;
    }

    @Override
    public DvbCapabilities getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public void attatch() throws DvbException {
        currentDeliverySystem = null;

        if (readReg(2, 0xFF) != 0x03) throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.unsupported_tuner_on_device));

        /* Sleep because chip is active by default */
        writeReg(2, 0x05, 0x3e);
        Log.d(TAG, "Attatch finished successfully");
    }

    @Override
    public void release() {
        try {
            // sleep
            writeReg(2, 0x05, 0x3e);
        } catch (DvbException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(DvbTuner tuner) throws DvbException {
        boolean isWarm = (readReg(0, 0xf5) & 0x01) == 0;
        if (!isWarm) {
            loadFirmware();

            /* Parity check of firmware */
            if ((readReg(0, 0xf8) & 0x10) != 0) {
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_load_firmware));
            }

            writeReg(0, 0xf5, 0x00);
        }
        Log.d(TAG, "Device is warm");

        /* TS config */
        writeReg(2, 0x09, 0x08);
        writeReg(2, 0x08, 0x1d);
    }

    private void loadFirmware() throws DvbException {
        Log.d(TAG, "Loading firmware");
        writeReg(0, 0xf5, 0x03);
        InputStream inputStream = resources.openRawResource(R.raw.mn8847301fw);

        try {
            byte[] buff = new byte[I2C_WR_MAX - 1];
            int remain = inputStream.available();
            while (remain > 0) {
                int toRead = remain > buff.length ? buff.length : remain;
                int read = inputStream.read(buff, 0, toRead);

                write(0, 0xf6, buff, read);
                remain -= read;
            }
        } catch (IOException e) {
            throw new DvbException(IO_EXCEPTION, e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setParams(long frequency, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        // TODO! implement me!
        throw new IllegalStateException("Implement me");
    }

    @Override
    public int readSnr() throws DvbException {
        Set<DvbStatus> cachedStatus = cachedStatus();
        if (!cachedStatus.contains(FE_HAS_VITERBI)) return 0;

        byte[] buf = new byte[4];
        int[] ibuf = new int[3];
        int tmp, tmp1, tmp2;

        switch (currentDeliverySystem) {
            case DVBT:
                read(0, 0x8f, buf, 2);
                tmp = ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);
                if (tmp != 0) {
                    /* CNR[dB]: 10 * (log10(65536 / value) + 0.2) */
			        /* log10(65536) = 80807124, 0.2 = 3355443 */
                    return (int) DvbMath.divU64((80807124L - DvbMath.intlog10(tmp) + 3355443L) * 10000L, 1 << 24);
                } else {
                    return 0;
                }
            case DVBT2:
                ibuf[0] = readReg(2, 0xb7);
                ibuf[1] = readReg(2, 0xb8);
                ibuf[2] = readReg(2, 0xb9);

                tmp = (ibuf[1] << 8) | ibuf[2];
                tmp1 = (ibuf[0] >> 2) & 0x01; /* 0=SISO, 1=MISO */

                if (tmp != 0) {
                    if (tmp1 != 0) {
                        /* CNR[dB]: 10 * (log10(16384 / value) - 0.6) */
				        /* log10(16384) = 70706234, 0.6 = 10066330 */
                        return (int) DvbMath.divU64((70706234L - DvbMath.intlog10(tmp) - 10066330L) * 10000L, 1 << 24);
                    } else {
                        /* CNR[dB]: 10 * (log10(65536 / value) + 0.2) */
				        /* log10(65536) = 80807124, 0.2 = 3355443 */
                        return (int) DvbMath.divU64((80807124L - DvbMath.intlog10(tmp) + 3355443L) * 10000L, 1 << 24);
                    }
                } else {
                    return 0;
                }
            case DVBC:
                read(1, 0xa1, buf, 4);

                tmp1 = ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF); /* signal */
                tmp2 = ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF); /* noise */

                if (tmp1 != 0 && tmp2 != 0) {
                    /* CNR[dB]: 10 * log10(8 * (signal / noise)) */
			        /* log10(8) = 15151336 */
                    return (int) DvbMath.divU64((15151336L + DvbMath.intlog10(tmp1) - DvbMath.intlog10(tmp2)) * 10000L, 1 << 24);
                } else {
                    return  0;
                }
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        Set<DvbStatus> cachedStatus = cachedStatus();
        if (!cachedStatus.contains(FE_HAS_SIGNAL)) return 0;

        // There's signal, read it
        int[] buf = new int[2];

        buf[0] = readReg(2, 0x86);
        buf[1] = readReg(2, 0x87);

        /* AGCRD[15:6] gives us a 10bit value ([5:0] are always 0) */
        int strength = (buf[0] << 8) | buf[1] | (buf[0] >> 2);

        return (100 * strength) / 0xffff;
    }

    @Override
    public int readBer() throws DvbException {
        Set<DvbStatus> cachedStatus = cachedStatus();
        if (!cachedStatus.contains(FE_HAS_LOCK)) return 0;

        byte[] buf = new byte[5];
        read(0, 0x92, buf, 5);

        int bitErrors = ((buf[0] & 0xFF) << 16) | ((buf[1] & 0xFF) << 8) | (buf[2] & 0xFF);
        int tmp2 = ((buf[3] & 0xFF) << 8) | (buf[4] & 0xFF);
        int bitCount = tmp2 * 8 * 204;

        // Default unit is bit error per 1MB
        return (int) ((bitErrors * 1_000_000L) / bitCount);
    }

    @Override
    public Set<DvbStatus> getStatus() throws DvbException {
        cachedStatus = readStatusFromHw();
        cachedStatusTime = System.currentTimeMillis();
        return cachedStatus;
    }

    private Set<DvbStatus> readStatusFromHw() throws DvbException {
        if (currentDeliverySystem == null) return SetUtils.setOf();

        int tmp;
        switch (currentDeliverySystem) {
            case DVBT:
                tmp = readReg(0, 0x62);

                if ((tmp & 0xa0) == 0) {
                    if ((tmp & 0x0f) >= 0x09) {
                        return SetUtils.setOf(FE_HAS_SIGNAL, FE_HAS_CARRIER,
                                FE_HAS_VITERBI, FE_HAS_SYNC,
                                FE_HAS_LOCK);
                    } else if ((tmp & 0x0f) >= 0x03) {
                        return SetUtils.setOf(FE_HAS_SIGNAL, FE_HAS_CARRIER);
                    } else {
                        return SetUtils.setOf();
                    }
                } else {
                    return SetUtils.setOf();
                }
            case DVBT2:
                tmp = readReg(2, 0x8b);

                if ((tmp & 0x40) == 0) {
                    if ((tmp & 0x0f) >= 0x0d) {
                        return SetUtils.setOf(FE_HAS_SIGNAL, FE_HAS_CARRIER,
                                FE_HAS_VITERBI, FE_HAS_SYNC,
                                FE_HAS_LOCK);
                    } else if ((tmp & 0x0f) >= 0x0a) {
                        return SetUtils.setOf(FE_HAS_SIGNAL, FE_HAS_CARRIER,
                                FE_HAS_VITERBI);
                    } else if ((tmp & 0x0f) >= 0x07) {
                        return SetUtils.setOf(FE_HAS_SIGNAL, FE_HAS_CARRIER);
                    } else {
                        return SetUtils.setOf();
                    }
                } else {
                    return SetUtils.setOf();
                }
            case DVBC:
                tmp = readReg(1, 0x85);

                if ((tmp & 0x40) == 0) {
                    tmp = readReg(1, 0x89);

                    if ((tmp & 0x01) != 0) {
                        return SetUtils.setOf(FE_HAS_SIGNAL, FE_HAS_CARRIER,
                                FE_HAS_VITERBI, FE_HAS_SYNC,
                                FE_HAS_LOCK);
                    } else {
                        return SetUtils.setOf();
                    }
                } else {
                    return SetUtils.setOf();
                }
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }
    }

    private Set<DvbStatus> cachedStatus() throws DvbException {
        // Don't get status if it was already updated in the last 0.5 sec
        if (System.currentTimeMillis() > cachedStatusTime + 500L) {
            getStatus();
        }
        return cachedStatus;
    }
}
