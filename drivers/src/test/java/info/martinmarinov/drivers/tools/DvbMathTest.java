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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DvbMathTest {
    @Test
    public void fls() throws Exception {
        assertThat(DvbMath.fls(1), is(1));
        assertThat(DvbMath.fls(0), is(0));
        assertThat(DvbMath.fls(0x80000000), is(32));
    }

    @Test
    public void intlog2() throws Exception {
        assertThat(DvbMath.intlog2(8), is(3 << 24));
        assertThat(DvbMath.intlog2(1024), is(10 << 24));
        assertThat(DvbMath.intlog2(0x80000000), is(31 << 24));
    }

    @Test
    public void intlog10() throws Exception {
        //example: intlog10(1000) will give 3 << 24 = 3 * 2^24
        // due to the implementation intlog10(1000) might be not exactly 3 * 2^24
        assertThat(DvbMath.intlog10(1_000), is(50331675));
    }
}