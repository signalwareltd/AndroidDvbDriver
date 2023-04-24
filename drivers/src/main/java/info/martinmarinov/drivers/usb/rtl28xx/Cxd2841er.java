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

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;
import static info.martinmarinov.drivers.tools.SetUtils.setOf;
import static info.martinmarinov.drivers.tools.SleepUtils.usleep;

import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.Check;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxDvbDevice.Rtl28xxI2cAdapter;

public class Cxd2841er implements DvbFrontend {
    private final static String TAG = Cxd2841er.class.getSimpleName();
    private ChipId chipId;
    private DvbTuner tuner;

    enum Xtal {
        SONY_XTAL_20500, /* 20.5 MHz */
        SONY_XTAL_24000, /* 24 MHz */
        SONY_XTAL_41000 /* 41 MHz */
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
                setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2, DeliverySystem.DVBC)
        )),
        CXD2841ER(0xa7, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2, DeliverySystem.DVBC)
        )),
        CXD2843ER(0xa4, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2, DeliverySystem.DVBC)
        )),
        CXD2854ER(0xc1, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2) // also ISDBT
        ));

        private final int chip_id;
        final @NonNull
        DvbCapabilities dvbCapabilities;

        ChipId(int chip_id, DvbCapabilities dvbCapabilities) {
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

    public Cxd2841er(Rtl28xxI2cAdapter i2cAdapter, Resources resources, Xtal xtal, int ic2_addr, boolean tsbits, boolean no_agcneg, boolean ts_serial, boolean early_tune) {
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

        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public int readSnr() throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public int readBer() throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public Set<DvbStatus> getStatus() throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public void setPids(int... pids) throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public void disablePidFilter() throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
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

        state = State.SHUTDOWN;
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
            throw new IllegalStateException("Device in bad state");
        }
    }

    private void activeTToSleepTc() throws DvbException {
        if (state != State.ACTIVE_TC) {
            throw new IllegalStateException("Device in bad state");
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
            throw new IllegalStateException("Device in bad state");
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
            throw new IllegalStateException("Device in bad state");
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
}
