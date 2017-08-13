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

class Mn88473 extends Mn8847X {
    private final static int DVBT2_STREAM_ID = 0;
    private final static long XTAL = 25_000_000L;

    private DeliverySystem currentDeliverySystem = null;
    private DvbTuner tuner;

    Mn88473(Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter, Resources resources) {
        super(i2cAdapter, resources, 0x03);
    }

    @Override
    public synchronized void release() {
        try {
            // sleep
            writeReg(2, 0x05, 0x3e);
        } catch (DvbException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void init(DvbTuner tuner) throws DvbException {
        this.tuner = tuner;

        loadFirmware(R.raw.mn8847301fw);

        /* TS config */
        writeReg(2, 0x09, 0x08);
        writeReg(2, 0x08, 0x1d);

        tuner.init();
    }

    @Override
    public synchronized void setParams(long frequency, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        int deliverySystemVal, regBank22dval, regBank0d2val;

        switch (deliverySystem) {
            case DVBT:
                deliverySystemVal = 0x02;
                regBank22dval = 0x23;
                regBank0d2val = 0x2a;
                break;
            case DVBT2:
                deliverySystemVal = 0x03;
                regBank22dval = 0x3b;
                regBank0d2val = 0x29;
                break;
            case DVBC:
                deliverySystemVal = 0x04;
                regBank22dval = 0x3b;
                regBank0d2val = 0x29;
                break;
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }

        byte[] confValPtr;
        switch (deliverySystem) {
            case DVBT:
            case DVBT2:
                switch ((int) bandwidthHz) {
                    case 6_000_000:
                        confValPtr = new byte[] {(byte) 0xe9, (byte) 0x55, (byte) 0x55, (byte) 0x1c, (byte) 0x29, (byte) 0x1c, (byte) 0x29};
                        break;
                    case 7_000_000:
                        confValPtr = new byte[] {(byte) 0xc8, (byte) 0x00, (byte) 0x00, (byte) 0x17, (byte) 0x0a, (byte) 0x17, (byte) 0x0a};
                        break;
                    case 8_000_000:
                        confValPtr = new byte[] {(byte) 0xaf, (byte) 0x00, (byte) 0x00, (byte) 0x11, (byte) 0xec, (byte) 0x11, (byte) 0xec};
                        break;
                    default:
                        throw new DvbException(UNSUPPORTED_BANDWIDTH, resources.getString(R.string.invalid_bw));
                }
                break;
            case DVBC:
                confValPtr = new byte[] {(byte) 0x10, (byte) 0xab, (byte) 0x0d, (byte) 0xae, (byte) 0x1d, (byte) 0x9d};
                break;
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }

        tuner.setParams(frequency, bandwidthHz, deliverySystem);
        long ifFrequency = tuner.getIfFrequency();

        long itmp = DvbMath.divRoundClosest(ifFrequency * 0x1000000L, XTAL);

        writeReg(2, 0x05, 0x00);
        writeReg(2, 0xfb, 0x13);
        writeReg(2, 0xef, 0x13);
        writeReg(2, 0xf9, 0x13);
        writeReg(2, 0x00, 0x18);
        writeReg(2, 0x01, 0x01);
        writeReg(2, 0x02, 0x21);
        writeReg(2, 0x03, deliverySystemVal);
        writeReg(2, 0x0b, 0x00);

        writeReg(2, 0x10, (int) ((itmp >> 16) & 0xff));
        writeReg(2, 0x11, (int) ((itmp >> 8) & 0xff));
        writeReg(2, 0x12, (int) (itmp & 0xff));

        switch (deliverySystem) {
            case DVBT:
            case DVBT2:
                for (int i = 0; i < 7; i++) {
                    writeReg(2, 0x13 + i, confValPtr[i] & 0xFF);
                }
                break;
            case DVBC:
                write(1, 0x10, confValPtr, 6);
                break;
        }

        writeReg(2, 0x2d, regBank22dval);
        writeReg(2, 0x2e, 0x00);
        writeReg(2, 0x56, 0x0d);
        write(0, 0x01, new byte[] { (byte) 0xba, (byte) 0x13, (byte) 0x80, (byte) 0xba, (byte) 0x91, (byte) 0xdd, (byte) 0xe7, (byte) 0x28 });
        writeReg(0, 0x0a, 0x1a);
        writeReg(0, 0x13, 0x1f);
        writeReg(0, 0x19, 0x03);
        writeReg(0, 0x1d, 0xb0);
        writeReg(0, 0x2a, 0x72);
        writeReg(0, 0x2d, 0x00);
        writeReg(0, 0x3c, 0x00);
        writeReg(0, 0x3f, 0xf8);
        write(0, 0x40, new byte[] { (byte) 0xf4, (byte) 0x08 });
        writeReg(0, 0xd2, regBank0d2val);
        writeReg(0, 0xd4, 0x55);
        writeReg(1, 0xbe, 0x08);
        writeReg(0, 0xb2, 0x37);
        writeReg(0, 0xd7, 0x04);

        /* PLP */
        if (deliverySystem == DeliverySystem.DVBT2) {
            writeReg(2, 0x36, DVBT2_STREAM_ID);
        }

        /* Reset FSM */
        writeReg(2, 0xf8, 0x9f);

        this.currentDeliverySystem = deliverySystem;
    }

    @Override
    public synchronized int readSnr() throws DvbException {
        Set<DvbStatus> cachedStatus = getStatus();
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
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }
    }

    @Override
    public synchronized int readRfStrengthPercentage() throws DvbException {
        Set<DvbStatus> cachedStatus = getStatus();
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
    public synchronized int readBer() throws DvbException {
        Set<DvbStatus> cachedStatus = getStatus();
        if (!cachedStatus.contains(FE_HAS_LOCK)) return 100;

        byte[] buf = new byte[5];
        read(0, 0x92, buf, 5);

        int bitErrors = ((buf[0] & 0xFF) << 16) | ((buf[1] & 0xFF) << 8) | (buf[2] & 0xFF);
        int tmp2 = ((buf[3] & 0xFF) << 8) | (buf[4] & 0xFF);
        int bitCount = tmp2 * 8 * 204;

        if (bitCount == 0) return 100;

        // Default unit is bit error per 1MB
        return (int) ((bitErrors * 1_000_000L) / bitCount);
    }

    @Override
    public synchronized Set<DvbStatus> getStatus() throws DvbException {
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
}
