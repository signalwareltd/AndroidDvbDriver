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

package info.martinmarinov.drivers;


public class DvbCapabilities {
    private final long frequencyMin;
    private final long frequencyMax;
    private final long frequencyStepSize;

    public DvbCapabilities(long frequencyMin, long frequencyMax, long frequencyStepSize) {
        this.frequencyMin = frequencyMin;
        this.frequencyMax = frequencyMax;
        this.frequencyStepSize = frequencyStepSize;
    }

    public long getFrequencyMin() {
        return frequencyMin;
    }

    public long getFrequencyMax() {
        return frequencyMax;
    }

    public long getFrequencyStepSize() {
        return frequencyStepSize;
    }

    @Override
    public String toString() {
        return "DvbCapabilities{" +
                "frequencyMin=" + frequencyMin +
                ", frequencyMax=" + frequencyMax +
                ", frequencyStepSize=" + frequencyStepSize +
                '}';
    }
}
