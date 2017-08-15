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

import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.DvbMath;
import info.martinmarinov.drivers.tools.SetUtils;
import info.martinmarinov.drivers.usb.DvbTuner;

import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ;
import static info.martinmarinov.drivers.DvbException.ErrorCode.UNSUPPORTED_BANDWIDTH;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_CARRIER;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_LOCK;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_SIGNAL;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_SYNC;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_VITERBI;

class Mn88472 extends Mn8847X {
    private final static int DVBT2_STREAM_ID = 0;
    private final static long XTAL = 20_500_000L;

    private DvbTuner tuner;
    private DeliverySystem currentDeliverySystem = null;


    Mn88472(Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter, Resources resources) {
        super(i2cAdapter, resources, 0x02);
    }

    @Override
    public synchronized void release() {
        /* Power down */
        try {
            writeReg(2, 0x0c, 0x30);
            writeReg(2, 0x0b, 0x30);
            writeReg(2, 0x05, 0x3e);
        } catch (DvbException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void init(DvbTuner tuner) throws DvbException {
        this.tuner = tuner;

        /* Power up */
        writeReg(2, 0x05, 0x00);
        writeReg(2, 0x0b, 0x00);
        writeReg(2, 0x0c, 0x00);

        loadFirmware(R.raw.mn8847202fw);

        /* TS config */
        writeReg(2, 0x08, 0x1d);
        writeReg(0, 0xd9, 0xe3);
    }

    @Override
    public synchronized void setParams(long frequency, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        int delivery_system_val, reg_bank0_b4_val,
                reg_bank0_cd_val, reg_bank0_d4_val, reg_bank0_d6_val;

        switch (deliverySystem) {
            case DVBT:
                delivery_system_val = 0x02;
                reg_bank0_b4_val = 0x00;
                reg_bank0_cd_val = 0x1f;
                reg_bank0_d4_val = 0x0a;
                reg_bank0_d6_val = 0x48;
                break;
            case DVBT2:
                delivery_system_val = 0x03;
                reg_bank0_b4_val = 0xf6;
                reg_bank0_cd_val = 0x01;
                reg_bank0_d4_val = 0x09;
                reg_bank0_d6_val = 0x46;
                break;
            case DVBC:
                delivery_system_val = 0x04;
                reg_bank0_b4_val = 0x00;
                reg_bank0_cd_val = 0x17;
                reg_bank0_d4_val = 0x09;
                reg_bank0_d6_val = 0x48;
                break;
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }

        byte[] bandwidth_vals_ptr;
        int bandwidth_val;
        switch (deliverySystem) {
            case DVBT:
            case DVBT2:
                switch ((int) bandwidthHz) {
                    case 5000000:
                        bandwidth_vals_ptr = new byte[] {(byte) 0xe5, (byte) 0x99, (byte) 0x9a, (byte) 0x1b, (byte) 0xa9, (byte) 0x1b, (byte) 0xa9};
                        bandwidth_val = 0x03;
                        break;
                    case 6000000:
                        bandwidth_vals_ptr = new byte[] {(byte) 0xbf, (byte) 0x55, (byte) 0x55, (byte) 0x15, (byte) 0x6b, (byte) 0x15, (byte) 0x6b};
                        bandwidth_val = 0x02;
                        break;
                    case 7000000:
                        bandwidth_vals_ptr = new byte[] {(byte) 0xa4, (byte) 0x00, (byte) 0x00, (byte) 0x0f, (byte) 0x2c, (byte) 0x0f, (byte) 0x2c};
                        bandwidth_val = 0x01;
                        break;
                    case 8000000:
                        bandwidth_vals_ptr = new byte[] {(byte) 0x8f, (byte) 0x80, (byte) 0x00, (byte) 0x08, (byte) 0xee, (byte) 0x08, (byte) 0xee};
                        bandwidth_val = 0x00;
                        break;
                    default:
                        throw new DvbException(UNSUPPORTED_BANDWIDTH, resources.getString(R.string.invalid_bw));
                }
                break;
            case DVBC:
                bandwidth_vals_ptr = null;
                bandwidth_val = 0x00;
                break;
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }

        /* Program tuner */
        tuner.setParams(frequency, bandwidthHz, deliverySystem);
        long ifFrequency = tuner.getIfFrequency();

        writeReg(2, 0x00, 0x66);
        writeReg(2, 0x01, 0x00);
        writeReg(2, 0x02, 0x01);
        writeReg(2, 0x03, delivery_system_val);
        writeReg(2, 0x04, bandwidth_val);

	    /* IF */
        long itmp = DvbMath.divRoundClosest(ifFrequency * 0x1000000L, XTAL);
        writeReg(2, 0x10, (int) ((itmp >> 16) & 0xff));
        writeReg(2, 0x11, (int) ((itmp >>  8) & 0xff));
        writeReg(2, 0x12, (int) (itmp & 0xff));

	    /* Bandwidth */
        if (bandwidth_vals_ptr != null) {
            for (int i = 0; i < 7; i++) {
                writeReg(2, 0x13 + i, bandwidth_vals_ptr[i] & 0xFF);
            }
        }

        writeReg(0, 0xb4, reg_bank0_b4_val);
        writeReg(0, 0xcd, reg_bank0_cd_val);
        writeReg(0, 0xd4, reg_bank0_d4_val);
        writeReg(0, 0xd6, reg_bank0_d6_val);

        switch (deliverySystem) {
            case DVBT:
                writeReg(0, 0x07, 0x26);
                writeReg(0, 0x00, 0xba);
                writeReg(0, 0x01, 0x13);
                break;
            case DVBT2:
                writeReg(2, 0x2b, 0x13);
                writeReg(2, 0x4f, 0x05);
                writeReg(1, 0xf6, 0x05);
                writeReg(2, 0x32, DVBT2_STREAM_ID);
                break;
            case DVBC:
                break;
            default:
                break;
        }

        /* Reset FSM */
        writeReg(2, 0xf8, 0x9f);
        this.currentDeliverySystem = deliverySystem;
    }

    @Override
    public int readSnr() throws DvbException {
        return -1;
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        Set<DvbStatus> cachedStatus = getStatus();
        if (!cachedStatus.contains(FE_HAS_SIGNAL)) return 0;
        return 100;
    }

    @Override
    public int readBer() throws DvbException {
        Set<DvbStatus> cachedStatus = getStatus();
        if (!cachedStatus.contains(FE_HAS_VITERBI)) return 0xFFFF;
        return 0;
    }

    @Override
    public synchronized Set<DvbStatus> getStatus() throws DvbException {
        if (currentDeliverySystem == null) return SetUtils.setOf();
        int tmp;
        switch (currentDeliverySystem) {
            case DVBT:
                tmp = readReg(0, 0x7f);

                if ((tmp & 0x0f) >= 0x09) {
                    return SetUtils.setOf(FE_HAS_SIGNAL, FE_HAS_CARRIER,
                            FE_HAS_VITERBI, FE_HAS_SYNC,
                            FE_HAS_LOCK);
                } else {
                    return SetUtils.setOf();
                }
            case DVBT2:
                tmp = readReg(2, 0x92);

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
            case DVBC:
                tmp = readReg(1, 0x84);

                if ((tmp & 0x0f) >= 0x08) {
                    return SetUtils.setOf(FE_HAS_SIGNAL, FE_HAS_CARRIER,
                            FE_HAS_VITERBI, FE_HAS_SYNC,
                            FE_HAS_LOCK);
                } else {
                    return SetUtils.setOf();
                }
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));

        }
    }
}
