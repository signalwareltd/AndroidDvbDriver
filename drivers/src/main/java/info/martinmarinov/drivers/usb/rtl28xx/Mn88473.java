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
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.I2cAdapter;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;
import static info.martinmarinov.drivers.DvbException.ErrorCode.IO_EXCEPTION;
import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;

class Mn88473 implements DvbFrontend {
    private final static String TAG = Mn88473.class.getSimpleName();

    private final static int I2C_WR_MAX = 22;
    private final static int[] I2C_ADDRESS = new int[] { 0x18, 0x1a, 0x1c };
    private final static long XTAL = 25_000_000L;

    private final Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter;
    private final I2cAdapter.I2GateControl i2GateController;
    private final Resources resources;

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
        return null;
    }

    @Override
    public void attatch() throws DvbException {
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
    public void setParams(long frequency, long bandwidthHz) throws DvbException {

    }

    @Override
    public int readSnr() throws DvbException {
        return 0;
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        return 0;
    }

    @Override
    public int readBer() throws DvbException {
        return 0;
    }

    @Override
    public Set<DvbStatus> getStatus() throws DvbException {
        return null;
    }
}
