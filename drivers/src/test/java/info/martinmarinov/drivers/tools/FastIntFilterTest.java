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

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class FastIntFilterTest {
    private FastIntFilter f;

    @Before
    public void setUp() {
        f = new FastIntFilter(20);
    }

    @Test
    public void testSimpleCase() {
        f.setFilter(0, 3, 8, 18, 19);
        confirmOnlyFiltered(0, 3, 8, 18, 19);
    }

    @Test
    public void testWithReset() {
        f.setFilter(1, 7, 8, 17);
        confirmOnlyFiltered(1, 7, 8, 17);
        f.setFilter(0);
        confirmOnlyFiltered(0);
        f.setFilter(13, 19);
        confirmOnlyFiltered(13, 19);
    }

    @Test
    public void testSlightlyOverSize() {
        f.setFilter(23); // Doesn't throw exception
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testTrulyOverSize() {
        f.setFilter(24);
    }

    private void confirmOnlyFiltered(int ... vals) {
        Set<Integer> set = new HashSet<>();
        for (int val : vals) set.add(val);
        int filteredCount = 0;
        for (int i = 0; i < 20; i++) {
            boolean isActuallyFiltered = set.contains(i);
            boolean isFiltered = f.isFiltered(i);

            if (isFiltered) filteredCount++;
            assertThat(isFiltered, is(isActuallyFiltered));
        }
        assertThat(filteredCount, is(vals.length));
    }
}