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

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class BitReverseTest {
    @Test
    public void bitRev8() throws Exception {
        assertThat(BitReverse.bitRev8((byte) 0x00), is((byte) 0x00));
        assertThat(BitReverse.bitRev8((byte) 0xFF), is((byte) 0xFF));
        assertThat(BitReverse.bitRev8((byte) 0x40), is((byte) 0x02));
        assertThat(BitReverse.bitRev8((byte) 0x02), is((byte) 0x40));
        assertThat(BitReverse.bitRev8((byte) 0x80), is((byte) 0x01));
        assertThat(BitReverse.bitRev8((byte) 0x01), is((byte) 0x80));
        assertThat(BitReverse.bitRev8((byte) 0xA0), is((byte) 0x05));
        assertThat(BitReverse.bitRev8((byte) 0x18), is((byte) 0x18));
        assertThat(BitReverse.bitRev8((byte) 0x1C), is((byte) 0x38));
        assertThat(BitReverse.bitRev8((byte) 0x38), is((byte) 0x1C));
    }

    @Test
    public void bitRev16() throws Exception {
        assertThat(BitReverse.bitRev16((short) 0x0001), is((short) 0x8000));
        assertThat(BitReverse.bitRev16((short) 0x8000), is((short) 0x0001));
        assertThat(BitReverse.bitRev16((short) 0xFF00), is((short) 0x00FF));
        assertThat(BitReverse.bitRev16((short) 0xFFF0), is((short) 0x0FFF));
        assertThat(BitReverse.bitRev16((short) 0x000F), is((short) 0xF000));
    }

    @Test
    public void bitRev32() throws Exception {
        assertThat(BitReverse.bitRev32(0x00000001), is(0x80000000));
        assertThat(BitReverse.bitRev32(0x80000000), is(0x00000001));
        assertThat(BitReverse.bitRev32(0x0F000000), is(0x000000F0));
        assertThat(BitReverse.bitRev32(0x000000F0), is(0x0F000000));
        assertThat(BitReverse.bitRev32(0xF0000000), is(0x0000000F));
        assertThat(BitReverse.bitRev32(0x0000000F), is(0xF0000000));
        assertThat(BitReverse.bitRev32(0xFF000000), is(0x000000FF));
        assertThat(BitReverse.bitRev32(0x000000FF), is(0xFF000000));
        assertThat(BitReverse.bitRev32(0xFFFFF0FF), is(0xFF0FFFFF));
        assertThat(BitReverse.bitRev32(0xFF0FFFFF), is(0xFFFFF0FF));
        assertThat(BitReverse.bitRev32(0xFFFFFFF0), is(0x0FFFFFFF));
        assertThat(BitReverse.bitRev32(0x0FFFFFFF), is(0xFFFFFFF0));
    }

}