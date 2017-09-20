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

package info.martinmarinov.drivers.tools;

import android.support.annotation.VisibleForTesting;

import info.martinmarinov.drivers.DvbException;

import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;

public class RegMap {
    private final int address;
    private final int reg_bytes;
    private final I2cAdapter i2CAdapter;

    private final Object locker = new Object();
    private final byte[] regbuff;
    private byte[] tmpbuff = new byte[0];

    public RegMap(int address, int reg_bits, I2cAdapter i2CAdapter) {
        this.address = address;
        this.reg_bytes = reg_bits >> 3;

        if (reg_bytes > 8) {
            throw new IllegalArgumentException("Only up to 64 bit register supported");
        }
        this.regbuff = new byte[reg_bytes];

        this.i2CAdapter = i2CAdapter;
    }

    public void read_regs(long reg, byte[] vals, int offset, int length) throws DvbException {
        synchronized (locker) {
            writeValue(regbuff, reg);

            byte[] buf = offset == 0 ? vals : getTmpBuffer(length);
            i2CAdapter.transfer(
                    address, 0, regbuff, regbuff.length,
                    address, I2C_M_RD, buf, length
            );

            if (offset != 0) {
                System.arraycopy(buf, 0, vals, offset, length);
            }
        }
    }

    public int read_reg(long reg) throws DvbException {
        byte[] ans = new byte[1];
        read_regs(reg, ans, 0, 1);
        return ans[0] & 0xFF;
    }

    public void write_reg(long reg, int val) throws DvbException {
        if ((val & 0xFF) != val) throw new IllegalArgumentException();
        write_regs(reg, new byte[] { (byte) val });
    }

    public void write_regs(long reg, byte[] vals) throws DvbException {
        synchronized (locker) {
            writeValue(regbuff, reg);
            byte[] buf = getTmpBuffer(regbuff.length + vals.length);
            System.arraycopy(regbuff, 0, buf, 0, regbuff.length);
            System.arraycopy(vals, 0, buf, regbuff.length, vals.length);
            i2CAdapter.transfer(address, 0, buf, regbuff.length + vals.length);
        }
    }

    @VisibleForTesting
    static long readValue(byte[] buff) {
        long res = 0;
        for (int i = 0; i < buff.length; i++) {
            res |= ((long) (buff[i] & 0xFF)) << ((buff.length - i - 1) * 8);
        }
        return res;
    }

    @VisibleForTesting
    static void writeValue(byte[] buff, long value) {
        for (int i = 0; i < buff.length; i++) {
            buff[i] = (byte) (value >> ((buff.length - i - 1) * 8));
        }
    }

    private byte[] getTmpBuffer(int size) {
        if (tmpbuff.length < size) {
            tmpbuff = new byte[size];
        }
        return tmpbuff;
    }
}
