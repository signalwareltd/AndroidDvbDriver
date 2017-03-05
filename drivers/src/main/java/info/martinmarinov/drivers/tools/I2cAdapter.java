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

import info.martinmarinov.drivers.DvbException;

public abstract class I2cAdapter {
    private final static int RETRIES = 10;

    public void transfer(int addr, int flags, byte[] buf) throws DvbException {
       transfer(addr, flags, buf, buf.length);
    }

    public void transfer(int addr, int flags, byte[] buf, int len) throws DvbException {
        transfer(new I2cMessage(addr, flags, buf, len));
    }

    public void transfer(int addr1, int flags1, byte[] buf1,
                         int addr2, int flags2, byte[] buf2) throws DvbException {
        transfer(addr1, flags1, buf1, buf1.length,
                addr2, flags2, buf2, buf2.length);
    }

    public void transfer(int addr1, int flags1, byte[] buf1, int len1,
                         int addr2, int flags2, byte[] buf2, int len2) throws DvbException {
        transfer(new I2cMessage(addr1, flags1, buf1, len1),
                new I2cMessage(addr2, flags2, buf2, len2));
    }

    private void transfer(I2cMessage ... messages) throws DvbException {
        for (int i = 0; i < RETRIES; i++) {
            try {
                if (masterXfer(messages) == messages.length) {
                    return;
                }
            } catch (DvbException e) {
                if (i == RETRIES - 1) throw e;
            }
        }
    }

    protected abstract int masterXfer(I2cMessage[] messages) throws DvbException;

    public class I2cMessage {
        public static final int I2C_M_TEN		= 0x0010	/* this is a ten bit chip address */;
        public static final int I2C_M_RD		= 0x0001	/* read data, from slave to master */;
        public static final int I2C_M_STOP		= 0x8000	/* if I2C_FUNC_PROTOCOL_MANGLING */;
        public static final int I2C_M_NOSTART		= 0x4000	/* if I2C_FUNC_NOSTART */;
        public static final int I2C_M_REV_DIR_ADDR	= 0x2000	/* if I2C_FUNC_PROTOCOL_MANGLING */;
        public static final int I2C_M_IGNORE_NAK	= 0x1000	/* if I2C_FUNC_PROTOCOL_MANGLING */;
        public static final int I2C_M_NO_RD_ACK		= 0x0800	/* if I2C_FUNC_PROTOCOL_MANGLING */;
        public static final int I2C_M_RECV_LEN		= 0x0400	/* length will be first received byte */;

        // These are 16 bit integers, however using int in Java
        // since Java doesn't have 16 bit unsigned type
        public final int addr;
        public final int flags;
        public final byte[] buf;
        public final int len;

        I2cMessage(int addr, int flags, byte[] buf, int len) {
            this.addr = addr;
            this.flags = flags;
            this.buf = buf;
            this.len = len;
        }
    }

    public interface I2GateControl {
        void i2cGateCtrl(boolean enable) throws DvbException;
    }
}
