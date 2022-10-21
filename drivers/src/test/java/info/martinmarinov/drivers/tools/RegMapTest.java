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

package info.martinmarinov.drivers.tools;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class RegMapTest {
    @Test
    public void readValueTest64bit() {
        long expected = 0x123456789ABCDEFFL;
        long actual = RegMap.readValue(new byte[] {
                (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78,
                (byte) 0x9A, (byte) 0xBC, (byte) 0xDE, (byte) 0xFF
        });

        assertThat(actual, is(expected));
    }

    @Test
    public void writeValueTest64bit() {
        byte[] expected = new byte[] {
                (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78,
                (byte) 0x9A, (byte) 0xBC, (byte) 0xDE, (byte) 0xFF
        };
        byte[] actual;
        RegMap.writeValue(actual = new byte[8], 0x123456789ABCDEFFL);

        assertThat(actual, is(expected));
    }

    @Test
    public void readValueTest16bit() {
        long expected = 0x1234L;
        long actual = RegMap.readValue(new byte[] {
                (byte) 0x12, (byte) 0x34
        });

        assertThat(actual, is(expected));
    }

    @Test
    public void writeValueTest16bit() {
        byte[] expected = new byte[] {
                (byte) 0x12, (byte) 0x34
        };
        byte[] actual;
        RegMap.writeValue(actual = new byte[2], 0x1234L);

        assertThat(actual, is(expected));
    }
}