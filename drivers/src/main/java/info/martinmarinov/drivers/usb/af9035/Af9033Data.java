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

package info.martinmarinov.drivers.usb.af9035;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.tools.SetUtils;

class Af9033Data {
    final static DvbCapabilities CAPABILITIES = new DvbCapabilities(
            174_000_000L,
            862_000_000L,
            250_000L,
            SetUtils.setOf(DeliverySystem.DVBT)
    );

    final int[][] reg_val_mask_tab(int tuner, int ts_mode_serial, int ts_mode_parallel, int adc_multiplier) {
        return new int[][] {
            { 0x80fb24, 0x00, 0x08 },
            { 0x80004c, 0x00, 0xff },
            { 0x00f641, tuner, 0xff },
            { 0x80f5ca, 0x01, 0x01 },
            { 0x80f715, 0x01, 0x01 },
            { 0x00f41f, 0x04, 0x04 },
            { 0x00f41a, 0x01, 0x01 },
            { 0x80f731, 0x00, 0x01 },
            { 0x00d91e, 0x00, 0x01 },
            { 0x00d919, 0x00, 0x01 },
            { 0x80f732, 0x00, 0x01 },
            { 0x00d91f, 0x00, 0x01 },
            { 0x00d91a, 0x00, 0x01 },
            { 0x80f730, 0x00, 0x01 },
            { 0x80f778, 0x00, 0xff },
            { 0x80f73c, 0x01, 0x01 },
            { 0x80f776, 0x00, 0x01 },
            { 0x00d8fd, 0x01, 0xff },
            { 0x00d830, 0x01, 0xff },
            { 0x00d831, 0x00, 0xff },
            { 0x00d832, 0x00, 0xff },
            { 0x80f985, ts_mode_serial, 0x01 },
            { 0x80f986, ts_mode_parallel, 0x01 },
            { 0x00d827, 0x00, 0xff },
            { 0x00d829, 0x00, 0xff },
            { 0x800045, adc_multiplier, 0xff },
        };
    }
}
