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

public class FastIntFilter {
    private final byte[] bitmap;
    private int[] filter = new int[0];

    public FastIntFilter(int size) {
        int bitmapSize = (size + 7) >> 3;
        this.bitmap = new byte[bitmapSize];
    }

    public void setFilter(int ... ids) {
        synchronized (bitmap) {
            for (int aFilter : filter) setFilterOff(aFilter);
            this.filter = ids;
            for (int aFilter : filter) setFilterOn(aFilter);
        }
    }

    public boolean isFiltered(int id) {
        int bid = id >> 3;
        int rem = id - (bid << 3);

        int mask = 1 << rem;
        return (bitmap[bid] & mask) != 0;
    }

    private void setFilterOn(int id) {
        int bid = id >> 3;
        int rem = id - (bid << 3);

        int mask = 1 << rem;
        bitmap[bid] |= mask;
    }

    private void setFilterOff(int id) {
        int bid = id >> 3;
        int rem = id - (bid << 3);

        int mask = 1 << rem;
        bitmap[bid] &= ~mask;
    }
}
