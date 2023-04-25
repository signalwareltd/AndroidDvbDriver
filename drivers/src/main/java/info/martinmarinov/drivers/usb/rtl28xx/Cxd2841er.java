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

package info.martinmarinov.drivers.usb.rtl28xx;

import static java.util.Collections.addAll;
import static java.util.Collections.emptySet;
import static info.martinmarinov.drivers.DeliverySystem.DVBC;
import static info.martinmarinov.drivers.DeliverySystem.DVBT2;
import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.DvbException.ErrorCode.UNSUPPORTED_BANDWIDTH;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_SIGNAL;
import static info.martinmarinov.drivers.DvbStatus.FE_HAS_VITERBI;
import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;
import static info.martinmarinov.drivers.tools.SetUtils.setOf;
import static info.martinmarinov.drivers.tools.SleepUtils.usleep;

import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.LinkedHashSet;
import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.Check;
import info.martinmarinov.drivers.tools.ThrowingRunnable;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxDvbDevice.Rtl28xxI2cAdapter;

public class Cxd2841er implements DvbFrontend {
    private final static String TAG = Cxd2841er.class.getSimpleName();
    private ChipId chipId;
    private DvbTuner tuner;

    enum Xtal {
        SONY_XTAL_20500(
                new byte[]{0x11, (byte) 0xF0, 0x00, 0x00, 0x00},
                new byte[]{0x14, (byte) 0x80, 0x00, 0x00, 0x00},
                new byte[]{0x17, (byte) 0xEA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA},
                new byte[]{0x1C, (byte) 0xB3, 0x33, 0x33, 0x33},
                new byte[]{0x58, (byte) 0xE2, (byte) 0xAF, (byte) 0xE0, (byte) 0xBC}),
        SONY_XTAL_24000(
                new byte[]{0x15, 0x00, 0x00, 0x00, 0x00},
                new byte[]{0x18, 0x00, 0x00, 0x00, 0x00},
                new byte[]{0x1C, 0x00, 0x00, 0x00, 0x00},
                new byte[]{0x21, (byte) 0x99, (byte) 0x99, (byte) 0x99, (byte) 0x99},
                new byte[]{0x68, 0x0F, (byte) 0xA2, 0x32, (byte) 0xD0}),
        SONY_XTAL_41000(
                new byte[]{0x11, (byte) 0xF0, 0x00, 0x00, 0x00},
                new byte[]{0x14, (byte) 0x80, 0x00, 0x00, 0x00},
                new byte[]{0x17, (byte) 0xEA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA},
                new byte[]{0x1C, (byte) 0xB3, 0x33, 0x33, 0x33},
                new byte[]{0x58, (byte) 0xE2, (byte) 0xAF, (byte) 0xE0, (byte) 0xBC});

        private final byte[] nominalRate8bw, nominalRate7bw, nominalRate6bw, nominalRate5bw, nominalRate17bw;

        Xtal(byte[] nominalRate8bw, byte[] nominalRate7bw, byte[] nominalRate6bw, byte[] nominalRate5bw, byte[] nominalRate17bw) {
            this.nominalRate8bw = nominalRate8bw;
            this.nominalRate7bw = nominalRate7bw;
            this.nominalRate6bw = nominalRate6bw;
            this.nominalRate5bw = nominalRate5bw;
            this.nominalRate17bw = nominalRate17bw;
        }
    }

    private enum State {
        SHUTDOWN,
        SLEEP_TC,
        ACTIVE_TC
    }

    private enum ChipId {
        CXD2837ER(0xb1, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DVBT2, DVBC)
        )),
        CXD2841ER(0xa7, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DVBT2, DVBC)
        )),
        CXD2843ER(0xa4, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DVBT2, DVBC)
        )),
        CXD2854ER(0xc1, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DVBT2) // also ISDBT
        ));

        private final int chip_id;
        final @NonNull
        DvbCapabilities dvbCapabilities;

        ChipId(int chip_id, @NonNull DvbCapabilities dvbCapabilities) {
            this.chip_id = chip_id;
            this.dvbCapabilities = dvbCapabilities;
        }
    }

    private enum I2C {
        SLVX,
        SLVT
    }

    private final static int MAX_WRITE_REGSIZE = 16;

    private final Rtl28xxI2cAdapter i2cAdapter;
    private final Resources resources;
    private final Xtal xtal;
    private final int i2c_addr_slvx, i2c_addr_slvt;
    private final boolean tsbits, no_agcneg, ts_serial, early_tune;

    private State state = State.SHUTDOWN;
    private DeliverySystem currentDeliverySystem = null;

    public Cxd2841er(
            Rtl28xxI2cAdapter i2cAdapter,
            Resources resources,
            Xtal xtal,
            int ic2_addr,
            boolean tsbits,
            boolean no_agcneg,
            boolean ts_serial,
            boolean early_tune) {
        this.i2cAdapter = i2cAdapter;
        this.resources = resources;
        this.xtal = xtal;
        this.i2c_addr_slvx = (ic2_addr + 4) >> 1;
        this.i2c_addr_slvt = ic2_addr >> 1;
        this.tsbits = tsbits;
        this.no_agcneg = no_agcneg;
        this.ts_serial = ts_serial;
        this.early_tune = early_tune;
    }

    @Override
    public DvbCapabilities getCapabilities() {
        Check.notNull(chipId, "Capabilities check before tuner is attached");
        return chipId.dvbCapabilities;
    }

    @Override
    public void attach() throws DvbException {
        this.chipId = cxd2841erChipId();
        Log.d(TAG, "Chip found: " + chipId);
    }

    @Override
    public void release() {
        chipId = null;
        currentDeliverySystem = null;
        try {
            sleepTcToShutdown();
        } catch (DvbException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(DvbTuner tuner) throws DvbException {
        this.tuner = tuner;

        shutdownToSleepTc();

        /* SONY_DEMOD_CONFIG_IFAGCNEG = 1 (0 for NO_AGCNEG */
        writeReg(I2C.SLVT, 0x00, 0x10);
        setRegBits(I2C.SLVT, 0xcb, (no_agcneg ? 0x00 : 0x40), 0x40);
        /* SONY_DEMOD_CONFIG_IFAGC_ADC_FS = 0 */
        writeReg(I2C.SLVT, 0xcd, 0x50);
        /* SONY_DEMOD_CONFIG_PARALLEL_SEL = 1 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        setRegBits(I2C.SLVT, 0xc4, (ts_serial ? 0x80 : 0x00), 0x80);

        /* clear TSCFG bits 3+4 */
        if (tsbits) {
            setRegBits(I2C.SLVT, 0xc4, 0x00, 0x18);
        }

        tuner.init();
    }

    @Override
    public void setParams(long frequency, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        if (early_tune) {
            tuner.setParams(frequency, bandwidthHz, deliverySystem);
        }

        /* deconfigure/put demod to sleep on delsys switch if active */
        if (state == State.ACTIVE_TC && deliverySystem != currentDeliverySystem) {
            sleepTc();
        }

        switch (deliverySystem) {
            case DVBT:
                currentDeliverySystem = DeliverySystem.DVBT;
                switch (state) {
                    case SLEEP_TC:
                        sleepTcToActiveT(bandwidthHz);
                        break;
                    case ACTIVE_TC:
                        retuneActive(bandwidthHz, deliverySystem);
                        break;
                    default:
                        throw new IllegalStateException("Device in bad state "+state);
                }
                break;
            case DVBT2:
                currentDeliverySystem = DVBT2;
                switch (state) {
                    case SLEEP_TC:
                        sleepTcToActiveT2(bandwidthHz);
                        break;
                    case ACTIVE_TC:
                        retuneActive(bandwidthHz, deliverySystem);
                        break;
                    default:
                        throw new IllegalStateException("Device in bad state "+state);
                }
                break;
            case DVBC:
                currentDeliverySystem = DVBC;

                if (bandwidthHz != 6000000L && bandwidthHz != 7000000L && bandwidthHz != 8000000L) {
                    bandwidthHz = 8000000L;
                    Log.d(TAG, "Forcing bandwidth to " + bandwidthHz);
                }

                switch (state) {
                    case SLEEP_TC:
                        sleepTcToActiveC(bandwidthHz);
                        break;
                    case ACTIVE_TC:
                        retuneActive(bandwidthHz, deliverySystem);
                        break;
                    default:
                        throw new IllegalStateException("Device in bad state "+state);
                }
                break;
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }

        if (!early_tune) {
            tuner.setParams(frequency, bandwidthHz, deliverySystem);
        }

        tuneDone();
    }

    private void tuneDone() throws DvbException {
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0, 0);
        /* SW Reset */
        writeReg(I2C.SLVT, 0xfe, 0x01);
        /* Enable TS output */
        writeReg(I2C.SLVT, 0xc3, 0x00);
    }

    private void retuneActive(long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        if (state != State.ACTIVE_TC) {
            throw new IllegalStateException("Device in bad state "+state);
        }
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* disable TS output */
        writeReg(I2C.SLVT, 0xc3, 0x01);

        switch (deliverySystem) {
            case DVBT:
                sleepTcToActiveTBand(bandwidthHz);
                break;
            case DVBT2:
                sleepTcToActiveT2Band(bandwidthHz);
                break;
            case DVBC:
                sleepTcToActiveCBand(bandwidthHz);
                break;
            default:
                throw new DvbException(CANNOT_TUNE_TO_FREQ, resources.getString(R.string.unsupported_delivery_system));
        }
    }

    private void sleepTcToActiveT(long bandwidthHz) throws DvbException {
        setTsClockMode(DeliverySystem.DVBT);
        /* Set SLV-X Bank : 0x00 */
        writeReg(I2C.SLVX, 0x00, 0x00);
        /* Set demod mode */
        writeReg(I2C.SLVX, 0x17, 0x01);
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* Enable demod clock */
        writeReg(I2C.SLVT, 0x2c, 0x01);
        /* Disable RF level monitor */
        writeReg(I2C.SLVT, 0x2f, 0x00);
        /* Enable ADC clock */
        writeReg(I2C.SLVT, 0x30, 0x00);
        /* Enable ADC 1 */
        writeReg(I2C.SLVT, 0x41, 0x1a);
        /* Enable ADC 2 & 3 */
        if (xtal == Xtal.SONY_XTAL_41000) {
            write(I2C.SLVT, 0x43, new byte[]{0x0A, (byte) 0xD4});
        } else {
            write(I2C.SLVT, 0x43, new byte[]{0x09, 0x54});
        }
        /* Enable ADC 4 */
        writeReg(I2C.SLVX, 0x18, 0x00);
        /* Set SLV-T Bank : 0x10 */
        writeReg(I2C.SLVT, 0x00, 0x10);
        /* IFAGC gain settings */
        setRegBits(I2C.SLVT, 0xd2, 0x0c, 0x1f);
        /* Set SLV-T Bank : 0x11 */
        writeReg(I2C.SLVT, 0x00, 0x11);
        /* BBAGC TARGET level setting */
        writeReg(I2C.SLVT, 0x6a, 0x50);
        /* Set SLV-T Bank : 0x10 */
        writeReg(I2C.SLVT, 0x00, 0x10);
        /* ASCOT setting */
        setRegBits(I2C.SLVT, 0xa5, 0x00, 0x01);
        /* Set SLV-T Bank : 0x18 */
        writeReg(I2C.SLVT, 0x00, 0x18);
        /* Pre-RS BER monitor setting */
        setRegBits(I2C.SLVT, 0x36, 0x40, 0x07);
        /* FEC Auto Recovery setting */
        setRegBits(I2C.SLVT, 0x30, 0x01, 0x01);
        setRegBits(I2C.SLVT, 0x31, 0x01, 0x01);
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* TSIF setting */
        setRegBits(I2C.SLVT, 0xce, 0x01, 0x01);
        setRegBits(I2C.SLVT, 0xcf, 0x01, 0x01);

        if (xtal == Xtal.SONY_XTAL_24000) {
            /* Set SLV-T Bank : 0x10 */
            writeReg(I2C.SLVT, 0x00, 0x10);
            writeReg(I2C.SLVT, 0xBF, 0x60);
            writeReg(I2C.SLVT, 0x00, 0x18);
            write(I2C.SLVT, 0x24, new byte[]{(byte) 0xDC, 0x6C, 0x00});
        }

        sleepTcToActiveTBand(bandwidthHz);
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* Disable HiZ Setting 1 */
        writeReg(I2C.SLVT, 0x80, 0x28);
        /* Disable HiZ Setting 2 */
        writeReg(I2C.SLVT, 0x81, 0x00);

        state = State.ACTIVE_TC;
    }

    private void sleepTcToActiveT2(long bandwidthHz) throws DvbException {
        setTsClockMode(DVBT2);

        /* Set SLV-X Bank : 0x00 */
        writeReg(I2C.SLVX, 0x00, 0x00);
        /* Set demod mode */
        writeReg(I2C.SLVX, 0x17, 0x02);
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* Enable demod clock */
        writeReg(I2C.SLVT, 0x2c, 0x01);
        /* Disable RF level monitor */
        writeReg(I2C.SLVT, 0x59, 0x00);
        writeReg(I2C.SLVT, 0x2f, 0x00);
        /* Enable ADC clock */
        writeReg(I2C.SLVT, 0x30, 0x00);
        /* Enable ADC 1 */
        writeReg(I2C.SLVT, 0x41, 0x1a);

        if (xtal == Xtal.SONY_XTAL_41000) {
            write(I2C.SLVT, 0x43, new byte[]{0x0A, (byte) 0xD4});
        } else {
            write(I2C.SLVT, 0x43, new byte[]{0x09, 0x54});
        }

        /* Enable ADC 4 */
        writeReg(I2C.SLVX, 0x18, 0x00);
        /* Set SLV-T Bank : 0x10 */
        writeReg(I2C.SLVT, 0x00, 0x10);
        /* IFAGC gain settings */
        setRegBits(I2C.SLVT, 0xd2, 0x0c, 0x1f);
        /* Set SLV-T Bank : 0x11 */
        writeReg(I2C.SLVT, 0x00, 0x11);
        /* BBAGC TARGET level setting */
        writeReg(I2C.SLVT, 0x6a, 0x50);
        /* Set SLV-T Bank : 0x10 */
        writeReg(I2C.SLVT, 0x00, 0x10);
        /* ASCOT setting */
        setRegBits(I2C.SLVT, 0xa5, 0x00, 0x01);
        /* Set SLV-T Bank : 0x20 */
        writeReg(I2C.SLVT, 0x00, 0x20);
        /* Acquisition optimization setting */
        writeReg(I2C.SLVT, 0x8b, 0x3c);
        /* Set SLV-T Bank : 0x2b */
        writeReg(I2C.SLVT, 0x00, 0x2b);
        setRegBits(I2C.SLVT, 0x76, 0x20, 0x70);
        /* Set SLV-T Bank : 0x23 */
        writeReg(I2C.SLVT, 0x00, 0x23);
        /* L1 Control setting */
        setRegBits(I2C.SLVT, 0xE6, 0x00, 0x03);
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* TSIF setting */
        setRegBits(I2C.SLVT, 0xce, 0x01, 0x01);
        setRegBits(I2C.SLVT, 0xcf, 0x01, 0x01);
        /* DVB-T2 initial setting */
        writeReg(I2C.SLVT, 0x00, 0x13);
        writeReg(I2C.SLVT, 0x83, 0x10);
        writeReg(I2C.SLVT, 0x86, 0x34);
        setRegBits(I2C.SLVT, 0x9e, 0x09, 0x0f);
        writeReg(I2C.SLVT, 0x9f, 0xd8);
        /* Set SLV-T Bank : 0x2a */
        writeReg(I2C.SLVT, 0x00, 0x2a);
        setRegBits(I2C.SLVT, 0x38, 0x04, 0x0f);
        /* Set SLV-T Bank : 0x2b */
        writeReg(I2C.SLVT, 0x00, 0x2b);
        setRegBits(I2C.SLVT, 0x11, 0x20, 0x3f);

        /* 24MHz Xtal setting */
        if (xtal == Xtal.SONY_XTAL_24000) {
            /* Set SLV-T Bank : 0x11 */
            writeReg(I2C.SLVT, 0x00, 0x11);
            write(I2C.SLVT, 0x33, new byte[]{(byte) 0xEB, 0x03, 0x3B});

            /* Set SLV-T Bank : 0x20 */
            writeReg(I2C.SLVT, 0x00, 0x20);
            write(I2C.SLVT, 0x95, new byte[]{0x5E, 0x5E, 0x47});

            writeReg(I2C.SLVT, 0x99, 0x18);
            write(I2C.SLVT, 0xD9, new byte[]{0x3F, (byte) 0xFF});

            /* Set SLV-T Bank : 0x24 */
            writeReg(I2C.SLVT, 0x00, 0x24);
            write(I2C.SLVT, 0x34, new byte[]{0x0B, 0x72});
            write(I2C.SLVT, 0xD2, new byte[]{(byte) 0x93, (byte) 0xF3, 0x00});
            write(I2C.SLVT, 0xDD, new byte[]{0x05, (byte) 0xB8, (byte) 0xD8});

            writeReg(I2C.SLVT, 0xE0, 0x00);

            /* Set SLV-T Bank : 0x25 */
            writeReg(I2C.SLVT, 0x00, 0x25);
            writeReg(I2C.SLVT, 0xED, 0x60);

            /* Set SLV-T Bank : 0x27 */
            writeReg(I2C.SLVT, 0x00, 0x27);
            writeReg(I2C.SLVT, 0xFA, 0x34);

            /* Set SLV-T Bank : 0x2B */
            writeReg(I2C.SLVT, 0x00, 0x2B);
            writeReg(I2C.SLVT, 0x4B, 0x2F);
            writeReg(I2C.SLVT, 0x9E, 0x0E);

            /* Set SLV-T Bank : 0x2D */
            writeReg(I2C.SLVT, 0x00, 0x2D);
            write(I2C.SLVT, 0x24, new byte[]{(byte) 0x89, (byte) 0x89});

            /* Set SLV-T Bank : 0x5E */
            writeReg(I2C.SLVT, 0x00, 0x5E);
            write(I2C.SLVT, 0x8C, new byte[]{0x24, (byte) 0x95});
        }

        sleepTcToActiveT2Band(bandwidthHz);

        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* Disable HiZ Setting 1 */
        writeReg(I2C.SLVT, 0x80, 0x28);
        /* Disable HiZ Setting 2 */
        writeReg(I2C.SLVT, 0x81, 0x00);

        state = State.ACTIVE_TC;
    }

    private void sleepTcToActiveC(long bandwidthHz) throws DvbException {
        setTsClockMode(DVBC);

        /* Set SLV-X Bank : 0x00 */
        writeReg(I2C.SLVX, 0x00, 0x00);
        /* Set demod mode */
        writeReg(I2C.SLVX, 0x17, 0x04);
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* Enable demod clock */
        writeReg(I2C.SLVT, 0x2c, 0x01);
        /* Disable RF level monitor */
        writeReg(I2C.SLVT, 0x59, 0x00);
        writeReg(I2C.SLVT, 0x2f, 0x00);
        /* Enable ADC clock */
        writeReg(I2C.SLVT, 0x30, 0x00);
        /* Enable ADC 1 */
        writeReg(I2C.SLVT, 0x41, 0x1a);
        /* xtal freq 20.5MHz */
        write(I2C.SLVT, 0x43, new byte[]{0x09, 0x54});
        /* Enable ADC 4 */
        writeReg(I2C.SLVX, 0x18, 0x00);
        /* Set SLV-T Bank : 0x10 */
        writeReg(I2C.SLVT, 0x00, 0x10);
        /* IFAGC gain settings */
        setRegBits(I2C.SLVT, 0xd2, 0x09, 0x1f);
        /* Set SLV-T Bank : 0x11 */
        writeReg(I2C.SLVT, 0x00, 0x11);
        /* BBAGC TARGET level setting */
        writeReg(I2C.SLVT, 0x6a, 0x48);
        /* Set SLV-T Bank : 0x10 */
        writeReg(I2C.SLVT, 0x00, 0x10);
        /* ASCOT setting */
        setRegBits(I2C.SLVT, 0xa5, 0x00, 0x01);
        /* Set SLV-T Bank : 0x40 */
        writeReg(I2C.SLVT, 0x00, 0x40);
        /* Demod setting */
        setRegBits(I2C.SLVT, 0xc3, 0x00, 0x04);
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* TSIF setting */
        setRegBits(I2C.SLVT, 0xce, 0x01, 0x01);
        setRegBits(I2C.SLVT, 0xcf, 0x01, 0x01);

        sleepTcToActiveCBand(bandwidthHz);

        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* Disable HiZ Setting 1 */
        writeReg(I2C.SLVT, 0x80, 0x28);
        /* Disable HiZ Setting 2 */
        writeReg(I2C.SLVT, 0x81, 0x00);

        state = State.ACTIVE_TC;
    }

    private void sleepTcToActiveTBand(long bandwidthHz) throws DvbException {
        /* Set SLV-T Bank : 0x13 */
        writeReg(I2C.SLVT, 0x00, 0x13);
        /* Echo performance optimization setting */
        write(I2C.SLVT, 0x9C, new byte[]{0x01, 0x14});
        /* Set SLV-T Bank : 0x10 */
        writeReg(I2C.SLVT, 0x00, 0x10);

        long iffreq = calcIffreqXtal();
        switch ((int) bandwidthHz) {
            case 8000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate8bw);
                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});
                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x00, 0x07);

                /* Demod core latency setting */
                if (xtal == Xtal.SONY_XTAL_24000) {
                    write(I2C.SLVT, 0xD9, new byte[]{0x15, 0x28});
                } else {
                    write(I2C.SLVT, 0xD9, new byte[]{0x01, (byte) 0xE0});
                }

                /* Notch filter setting */
                writeReg(I2C.SLVT, 0x00, 0x17);
                write(I2C.SLVT, 0x38, new byte[]{0x01, 0x02});
                break;
            case 7000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate7bw);
                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});
                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x02, 0x07);

                /* Demod core latency setting */
                if (xtal == Xtal.SONY_XTAL_24000) {
                    write(I2C.SLVT, 0xD9, new byte[]{0x1F, (byte) 0xF8});
                } else {
                    write(I2C.SLVT, 0xD9, new byte[]{0x12, (byte) 0xF8});
                }

                /* Notch filter setting */
                writeReg(I2C.SLVT, 0x00, 0x17);
                write(I2C.SLVT, 0x38, new byte[]{0x00, 0x03});
                break;
            case 6000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate6bw);
                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});
                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x04, 0x07);

                /* Demod core latency setting */
                if (xtal == Xtal.SONY_XTAL_24000) {
                    write(I2C.SLVT, 0xD9, new byte[]{0x25, 0x4C});
                } else {
                    write(I2C.SLVT, 0xD9, new byte[]{0x1F, (byte) 0xDC});
                }

                /* Notch filter setting */
                writeReg(I2C.SLVT, 0x00, 0x17);
                write(I2C.SLVT, 0x38, new byte[]{0x00, 0x03});
                break;
            case 5000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate5bw);
                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});
                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x06, 0x07);

                /* Demod core latency setting */
                if (xtal == Xtal.SONY_XTAL_24000) {
                    write(I2C.SLVT, 0xD9, new byte[]{0x2C, (byte) 0xC2});
                } else {
                    write(I2C.SLVT, 0xD9, new byte[]{0x26, 0x3C});
                }

                /* Notch filter setting */
                writeReg(I2C.SLVT, 0x00, 0x17);
                write(I2C.SLVT, 0x38, new byte[]{0x00, 0x03});
                break;
            default:
                throw new DvbException(UNSUPPORTED_BANDWIDTH, resources.getString(R.string.invalid_bw));
        }
    }

    private void sleepTcToActiveT2Band(long bandwidthHz) throws DvbException {
        /* Set SLV-T Bank : 0x20 */
        writeReg(I2C.SLVT, 0x00, 0x20);

        long iffreq = calcIffreqXtal();
        switch ((int) bandwidthHz) {
            case 8000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate8bw);

                /* Set SLV-T Bank : 0x27 */
                writeReg(I2C.SLVT, 0x00, 0x27);
                setRegBits(I2C.SLVT, 0x7a, 0x00, 0x0f);

                /* Set SLV-T Bank : 0x10 */
                writeReg(I2C.SLVT, 0x00, 0x10);

                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});

                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x00, 0x07);
                break;
            case 7000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate7bw);

                /* Set SLV-T Bank : 0x27 */
                writeReg(I2C.SLVT, 0x00, 0x27);
                setRegBits(I2C.SLVT, 0x7a, 0x00, 0x0f);

                /* Set SLV-T Bank : 0x10 */
                writeReg(I2C.SLVT, 0x00, 0x10);

                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});

                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x02, 0x07);
                break;
            case 6000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate6bw);

                /* Set SLV-T Bank : 0x27 */
                writeReg(I2C.SLVT, 0x00, 0x27);
                setRegBits(I2C.SLVT, 0x7a, 0x00, 0x0f);

                /* Set SLV-T Bank : 0x10 */
                writeReg(I2C.SLVT, 0x00, 0x10);

                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});

                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x04, 0x07);
                break;
            case 5000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate5bw);

                /* Set SLV-T Bank : 0x27 */
                writeReg(I2C.SLVT, 0x00, 0x27);
                setRegBits(I2C.SLVT, 0x7a, 0x00, 0x0f);

                /* Set SLV-T Bank : 0x10 */
                writeReg(I2C.SLVT, 0x00, 0x10);

                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});

                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x06, 0x07);
                break;
            case 1712000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate17bw);

                /* Set SLV-T Bank : 0x27 */
                writeReg(I2C.SLVT, 0x00, 0x27);
                setRegBits(I2C.SLVT, 0x7a, 0x03, 0x0f);

                /* Set SLV-T Bank : 0x10 */
                writeReg(I2C.SLVT, 0x00, 0x10);

                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});

                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x03, 0x07);
                break;
            default:
                throw new DvbException(UNSUPPORTED_BANDWIDTH, resources.getString(R.string.invalid_bw));
        }
    }

    private void sleepTcToActiveCBand(long bandwidthHz) throws DvbException {
        /* Set SLV-T Bank : 0x13 */
        writeReg(I2C.SLVT, 0x00, 0x13);
        /* Echo performance optimization setting */
        write(I2C.SLVT, 0x9C, new byte[]{0x01, 0x14});
        /* Set SLV-T Bank : 0x10 */
        writeReg(I2C.SLVT, 0x00, 0x10);

        long iffreq = calcIffreqXtal();
        switch ((int) bandwidthHz) {
            case 8000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate8bw);

                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});
                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x00, 0x07);

                /* Demod core latency setting */
                if (xtal == Xtal.SONY_XTAL_24000) {
                    write(I2C.SLVT, 0xD9, new byte[]{0x15, 0x28});
                } else {
                    write(I2C.SLVT, 0xD9, new byte[]{0x01, (byte) 0xE0});
                }

                /* Notch filter setting */
                writeReg(I2C.SLVT, 0x00, 0x17);
                write(I2C.SLVT, 0x38, new byte[]{0x01, 0x02});
                break;
            case 7000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate7bw);

                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});
                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x02, 0x07);

                /* Demod core latency setting */
                if (xtal == Xtal.SONY_XTAL_24000) {
                    write(I2C.SLVT, 0xD9, new byte[]{0x1F, (byte) 0xF8});
                } else {
                    write(I2C.SLVT, 0xD9, new byte[]{0x12, (byte) 0xF8});
                }

                /* Notch filter setting */
                writeReg(I2C.SLVT, 0x00, 0x17);
                write(I2C.SLVT, 0x38, new byte[]{0x00, 0x03});
                break;
            case 6000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate6bw);

                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});
                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x04, 0x07);

                /* Demod core latency setting */
                if (xtal == Xtal.SONY_XTAL_24000) {
                    write(I2C.SLVT, 0xD9, new byte[]{0x25, 0x4C});
                } else {
                    write(I2C.SLVT, 0xD9, new byte[]{0x1F, (byte) 0xDC});
                }

                /* Notch filter setting */
                writeReg(I2C.SLVT, 0x00, 0x17);
                write(I2C.SLVT, 0x38, new byte[]{0x00, 0x03});
                break;
            case 5000000:
                /* <Timing Recovery setting> */
                write(I2C.SLVT, 0x9F, xtal.nominalRate5bw);

                /* <IF freq setting> */
                write(I2C.SLVT, 0xB6, new byte[]{(byte) ((iffreq >> 16) & 0xff), (byte) ((iffreq >> 8) & 0xff), (byte) (iffreq & 0xff)});
                /* System bandwidth setting */
                setRegBits(I2C.SLVT, 0xD7, 0x06, 0x07);

                /* Demod core latency setting */
                if (xtal == Xtal.SONY_XTAL_24000) {
                    write(I2C.SLVT, 0xD9, new byte[]{0x2C, (byte) 0xC2});
                } else {
                    write(I2C.SLVT, 0xD9, new byte[]{0x26, 0x3C});
                }

                /* Notch filter setting */
                writeReg(I2C.SLVT, 0x00, 0x17);
                write(I2C.SLVT, 0x38, new byte[]{0x00, 0x03});
                break;
            default:
                throw new DvbException(UNSUPPORTED_BANDWIDTH, resources.getString(R.string.invalid_bw));
        }
    }

    private long calcIffreqXtal() throws DvbException {
        long ifhz = tuner.getIfFrequency() * 16777216L;
        return ifhz / (xtal == Xtal.SONY_XTAL_24000 ? 48000000L : 41000000L);
    }

    private void setTsClockMode(DeliverySystem system) throws DvbException {
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);

        setRegBits(I2C.SLVT, 0xc4, ts_serial ? 0x01 : 0x00, 0x03);
        setRegBits(I2C.SLVT, 0xd1, ts_serial ? 0x01 : 0x00, 0x03);
        writeReg(I2C.SLVT, 0xd9, 0x08);
        setRegBits(I2C.SLVT, 0x32, 0x00, 0x01);
        setRegBits(I2C.SLVT, 0x33, ts_serial ? 0x01 : 0x00, 0x03);
        setRegBits(I2C.SLVT, 0x32, 0x01, 0x01);

        if (system == DeliverySystem.DVBT) {
            /* Enable parity period for DVB-T */
            writeReg(I2C.SLVT, 0x00, 0x10);
            setRegBits(I2C.SLVT, 0x66, 0x01, 0x01);
        } else if (system == DVBC) {
            /* Enable parity period for DVB-C */
            writeReg(I2C.SLVT, 0x00, 0x40);
            setRegBits(I2C.SLVT, 0x66, 0x01, 0x01);
        }
    }

    @Override
    public int readSnr() throws DvbException {
        if (currentDeliverySystem == null) {
            return -1;
        }

        byte[] data = new byte[2];
        withFrozenRegisters(() -> {
            writeReg(I2C.SLVT, 0x00, 0x10);
            read(I2C.SLVT, 0x28, data, data.length);
        });
        int reg = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        if (reg == 0) {
            return -1;
        }

        switch (currentDeliverySystem) {
            case DVBT:
                if (reg > 4996) {
                    reg = 4996;
                }
                return 100 * (intlog10x100(reg) - intlog10x100(5350 - reg) + 285);
            case DVBT2:
                if (reg > 10876) {
                    reg = 10876;
                }
                return 100 * (intlog10x100(reg) - intlog10x100(12600 - reg) + 320);
        }

        // Unsupported for DVBC
        return -1;
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        if (state != State.ACTIVE_TC) {
            return 0;
        }
        // Fallback to dumb heuristics for DVB-C
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
    public Set<DvbStatus> getStatus() throws DvbException {
        if (state != State.ACTIVE_TC) {
            return emptySet();
        }

        Set<DvbStatus> statuses = new LinkedHashSet<>();
        switch (currentDeliverySystem) {
            case DVBT:
                writeReg(I2C.SLVT, 0x00, 0x10);
                break;
            case DVBT2:
                writeReg(I2C.SLVT, 0x00, 0x20);
                break;
            case DVBC:
                writeReg(I2C.SLVT, 0x00, 0x40);
                break;
            default:
                throw new IllegalStateException("Unexpected delivery system");
        }

        int data;
        switch (currentDeliverySystem) {
            case DVBT:
            case DVBT2:
                data = readReg(I2C.SLVT, 0x10);
                if ((data & 0x07) != 0x07) {
                    if ((data & 0x10) != 0) {
                        // unlock
                        return emptySet();
                    }
                    if ((data & 0x07) == 0x6) {
                        // sync
                        addAll(
                                statuses,
                                DvbStatus.FE_HAS_SIGNAL,
                                DvbStatus.FE_HAS_CARRIER,
                                DvbStatus.FE_HAS_VITERBI,
                                DvbStatus.FE_HAS_SYNC
                        );
                    }
                    if ((data & 0x20) != 0) {
                        statuses.add(DvbStatus.FE_HAS_LOCK);
                    }
                }
                break;
            case DVBC:
                data = readReg(I2C.SLVT, 0x88);
                if ((data & 0x01) != 0) {
                    data = readReg(I2C.SLVT, 0x10);
                    if ((data & 0x20) != 0) {
                        addAll(
                                statuses,
                                DvbStatus.FE_HAS_LOCK,
                                DvbStatus.FE_HAS_SIGNAL,
                                DvbStatus.FE_HAS_CARRIER,
                                DvbStatus.FE_HAS_VITERBI,
                                DvbStatus.FE_HAS_SYNC
                        );
                    }
                }
                break;
        }
        return statuses;
    }

    @Override
    public void setPids(int... pids) throws DvbException {
        // Not supported
    }

    @Override
    public void disablePidFilter() throws DvbException {
        // Not supported
    }

    private ChipId cxd2841erChipId() throws DvbException {
        int chip_id;
        try {
            writeReg(I2C.SLVT, 0, 0);
            chip_id = readReg(I2C.SLVT, 0xfd);
        } catch (Throwable t) {
            writeReg(I2C.SLVX, 0, 0);
            chip_id = readReg(I2C.SLVX, 0xfd);
        }
        for (ChipId c : ChipId.values()) {
            if (c.chip_id == chip_id) {
                return c;
            }
        }
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_chip_id));
    }

    private void shutdownToSleepTc() throws DvbException {
        /* Set SLV-X Bank : 0x00 */
        writeReg(I2C.SLVX, 0x00, 0x00);
        /* Clear all demodulator registers */
        writeReg(I2C.SLVX, 0x02, 0x00);

        usleep(5000);

        /* Set SLV-X Bank : 0x00 */
        writeReg(I2C.SLVX, 0x00, 0x00);
        /* Set demod SW reset */
        writeReg(I2C.SLVX, 0x10, 0x01);
        /* Select ADC clock mode */
        writeReg(I2C.SLVX, 0x13, 0x00);

        int data;
        switch (xtal) {
            case SONY_XTAL_20500:
                data = 0x0;
                break;
            case SONY_XTAL_24000:
                writeReg(I2C.SLVX, 0x12, 0x00);
                data = 0x3;
                break;
            case SONY_XTAL_41000:
                writeReg(I2C.SLVX, 0x12, 0x00);
                data = 0x1;
                break;
            default:
                throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.usupported_clock_speed));
        }

        writeReg(I2C.SLVX, 0x14, data);
        /* Clear demod SW reset */
        writeReg(I2C.SLVX, 0x10, 0x00);

        usleep(5000);

        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* TADC Bias On */
        writeReg(I2C.SLVT, 0x43, 0x0a);
        writeReg(I2C.SLVT, 0x41, 0x0a);
        /* SADC Bias On */
        writeReg(I2C.SLVT, 0x63, 0x16);
        writeReg(I2C.SLVT, 0x65, 0x27);
        writeReg(I2C.SLVT, 0x69, 0x06);

        state = State.SLEEP_TC;
    }

    private void sleepTcToShutdown() throws DvbException {
        /* Set SLV-X Bank : 0x00 */
        writeReg(I2C.SLVX, 0x00, 0x00);
        /* Disable oscillator */
        writeReg(I2C.SLVX, 0x15, 0x01);
        /* Set demod mode */
        writeReg(I2C.SLVX, 0x17, 0x01);

        state = State.SHUTDOWN;
    }

    private void sleepTc() throws DvbException {
        if (state == State.ACTIVE_TC) {
            switch (currentDeliverySystem) {
                case DVBT:
                    activeTToSleepTc();
                    break;
                case DVBT2:
                    activeT2ToSleepTc();
                    break;
                case DVBC:
                    activeCToSleepTc();
                    break;
            }
        }
        if (state != State.SLEEP_TC) {
            throw new IllegalStateException("Device in bad state "+state);
        }
    }

    private void activeTToSleepTc() throws DvbException {
        if (state != State.ACTIVE_TC) {
            throw new IllegalStateException("Device in bad state "+state);
        }

        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* disable TS output */
        writeReg(I2C.SLVT, 0xc3, 0x01);
        /* enable Hi-Z setting 1 */
        writeReg(I2C.SLVT, 0x80, 0x3f);
        /* enable Hi-Z setting 2 */
        writeReg(I2C.SLVT, 0x81, 0xff);
        /* Set SLV-X Bank : 0x00 */
        writeReg(I2C.SLVX, 0x00, 0x00);
        /* disable ADC 1 */
        writeReg(I2C.SLVX, 0x18, 0x01);
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* Disable ADC 2 */
        writeReg(I2C.SLVT, 0x43, 0x0a);
        /* Disable ADC 3 */
        writeReg(I2C.SLVT, 0x41, 0x0a);
        /* Disable ADC clock */
        writeReg(I2C.SLVT, 0x30, 0x00);
        /* Disable RF level monitor */
        writeReg(I2C.SLVT, 0x2f, 0x00);
        /* Disable demod clock */
        writeReg(I2C.SLVT, 0x2c, 0x00);

        state = State.SLEEP_TC;
    }

    private void activeT2ToSleepTc() throws DvbException {
        if (state != State.ACTIVE_TC) {
            throw new IllegalStateException("Device in bad state "+state);
        }

        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* disable TS output */
        writeReg(I2C.SLVT, 0xc3, 0x01);
        /* enable Hi-Z setting 1 */
        writeReg(I2C.SLVT, 0x80, 0x3f);
        /* enable Hi-Z setting 2 */
        writeReg(I2C.SLVT, 0x81, 0xff);
        /* Cancel DVB-T2 setting */
        writeReg(I2C.SLVT, 0x00, 0x13);
        writeReg(I2C.SLVT, 0x83, 0x40);
        writeReg(I2C.SLVT, 0x86, 0x21);
        setRegBits(I2C.SLVT, 0x9e, 0x09, 0x0f);
        writeReg(I2C.SLVT, 0x9f, 0xfb);
        writeReg(I2C.SLVT, 0x00, 0x2a);
        setRegBits(I2C.SLVT, 0x38, 0x00, 0x0f);
        writeReg(I2C.SLVT, 0x00, 0x2b);
        setRegBits(I2C.SLVT, 0x11, 0x00, 0x3f);
        /* Set SLV-X Bank : 0x00 */
        writeReg(I2C.SLVX, 0x00, 0x00);
        /* disable ADC 1 */
        writeReg(I2C.SLVX, 0x18, 0x01);
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* Disable ADC 2 */
        writeReg(I2C.SLVT, 0x43, 0x0a);
        /* Disable ADC 3 */
        writeReg(I2C.SLVT, 0x41, 0x0a);
        /* Disable ADC clock */
        writeReg(I2C.SLVT, 0x30, 0x00);
        /* Disable RF level monitor */
        writeReg(I2C.SLVT, 0x2f, 0x00);
        /* Disable demod clock */
        writeReg(I2C.SLVT, 0x2c, 0x00);

        state = State.SLEEP_TC;
    }

    private void activeCToSleepTc() throws DvbException {
        if (state != State.ACTIVE_TC) {
            throw new IllegalStateException("Device in bad state "+state);
        }

        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* disable TS output */
        writeReg(I2C.SLVT, 0xc3, 0x01);
        /* enable Hi-Z setting 1 */
        writeReg(I2C.SLVT, 0x80, 0x3f);
        /* enable Hi-Z setting 2 */
        writeReg(I2C.SLVT, 0x81, 0xff);
        /* Cancel DVB-C setting */
        writeReg(I2C.SLVT, 0x00, 0x11);
        setRegBits(I2C.SLVT, 0xa3, 0x00, 0x1f);
        /* Set SLV-X Bank : 0x00 */
        writeReg(I2C.SLVX, 0x00, 0x00);
        /* disable ADC 1 */
        writeReg(I2C.SLVX, 0x18, 0x01);
        /* Set SLV-T Bank : 0x00 */
        writeReg(I2C.SLVT, 0x00, 0x00);
        /* Disable ADC 2 */
        writeReg(I2C.SLVT, 0x43, 0x0a);
        /* Disable ADC 3 */
        writeReg(I2C.SLVT, 0x41, 0x0a);
        /* Disable ADC clock */
        writeReg(I2C.SLVT, 0x30, 0x00);
        /* Disable RF level monitor */
        writeReg(I2C.SLVT, 0x2f, 0x00);
        /* Disable demod clock */
        writeReg(I2C.SLVT, 0x2c, 0x00);

        state = State.SLEEP_TC;
    }

    private void withFrozenRegisters(ThrowingRunnable<DvbException> r) throws DvbException {
        try {
            /*
             * Freeze registers: ensure multiple separate register reads
             * are from the same snapshot
             */
            writeReg(I2C.SLVT, 0x01, 0x01);

            r.run();
        } finally {
            writeReg(I2C.SLVT, 0x01, 0x00);
        }
    }

    synchronized void write(I2C i2C_addr, int reg, byte[] bytes) throws DvbException {
        write(i2C_addr, reg, bytes, bytes.length);
    }

    synchronized void write(I2C i2C_addr, int reg, byte[] value, int len) throws DvbException {
        if (len + 1 > MAX_WRITE_REGSIZE)
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.i2c_communication_failure));

        int i2c_addr = (i2C_addr == I2C.SLVX ? i2c_addr_slvx : i2c_addr_slvt);

        byte[] buf = new byte[len + 1];

        buf[0] = (byte) reg;
        System.arraycopy(value, 0, buf, 1, len);

        i2cAdapter.transfer(i2c_addr, 0, buf, len + 1);
    }

    synchronized void writeReg(I2C i2C_addr, int reg, int val) throws DvbException {
        write(i2C_addr, reg, new byte[]{(byte) val});
    }

    synchronized void setRegBits(I2C i2C_addr, int reg, int data, int mask) throws DvbException {
        if (mask != 0xFF) {
            int rdata = readReg(i2C_addr, reg);
            data = (data & mask) | (rdata & (mask ^ 0xFF));
        }
        writeReg(i2C_addr, reg, data);
    }

    synchronized void read(I2C i2C_addr, int reg, byte[] val, int len) throws DvbException {
        int i2c_addr = (i2C_addr == I2C.SLVX ? i2c_addr_slvx : i2c_addr_slvt);
        i2cAdapter.transfer(
                i2c_addr, 0, new byte[]{(byte) reg}, 1,
                i2c_addr, I2C_M_RD, val, len
        );
    }

    synchronized int readReg(I2C i2C_addr, int reg) throws DvbException {
        byte[] ans = new byte[1];
        read(i2C_addr, reg, ans, ans.length);
        return ans[0] & 0xFF;
    }

    private static int intlog10x100(double reg) {
        return (int) Math.round(Math.log10(reg) * 100.0);
    }
}
