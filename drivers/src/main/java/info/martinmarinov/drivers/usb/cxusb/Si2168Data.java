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

package info.martinmarinov.drivers.usb.cxusb;

import android.support.annotation.Nullable;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.tools.SetUtils;

class Si2168Data {
    enum Si2168Chip {
        SI2168_CHIP_ID_A20(('A' << 24) | (68 << 16) | ('2' << 8) | '0'),
        SI2168_CHIP_ID_A30(('A' << 24) | (68 << 16) | ('3' << 8) | '0'),
        SI2168_CHIP_ID_B40(('B' << 24) | (68 << 16) | ('4' << 8) | '0');

        private final int id;

        Si2168Chip(int id) {
            this.id = id;
        }

        static @Nullable Si2168Chip fromId(int id) {
            for (Si2168Chip chip : values()) {
                if (chip.id == id) return chip;
            }
            return null;
        }
    }

    final static DvbCapabilities CAPABILITIES = new DvbCapabilities(
            174000000L,
            862000000L,
            166667L,
            SetUtils.setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2, DeliverySystem.DVBC));
}
