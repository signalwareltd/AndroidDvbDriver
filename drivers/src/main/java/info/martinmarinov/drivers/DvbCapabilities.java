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

package info.martinmarinov.drivers;


import androidx.annotation.NonNull;

import java.util.Set;

public class DvbCapabilities {
    private final long frequencyMin;
    private final long frequencyMax;
    private final long frequencyStepSize;
    private final @NonNull Set<DeliverySystem> supportedDeliverySystems;

    public DvbCapabilities(long frequencyMin, long frequencyMax, long frequencyStepSize, @NonNull Set<DeliverySystem> supportedDeliverySystems) {
        this.frequencyMin = frequencyMin;
        this.frequencyMax = frequencyMax;
        this.frequencyStepSize = frequencyStepSize;
        this.supportedDeliverySystems = supportedDeliverySystems;
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

    public @NonNull Set<DeliverySystem> getSupportedDeliverySystems() {
        return supportedDeliverySystems;
    }

    @Override
    public String toString() {
        return "DvbCapabilities{" +
                "frequencyMin=" + frequencyMin +
                ", frequencyMax=" + frequencyMax +
                ", frequencyStepSize=" + frequencyStepSize +
                ", supportedDeliverySystems=" + supportedDeliverySystems +
                '}';
    }
}
