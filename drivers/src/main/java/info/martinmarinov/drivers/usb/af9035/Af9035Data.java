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

class Af9035Data {
    final static int EEPROM_BASE_AF9035    =      0x42f5;
    final static int EEPROM_BASE_IT9135    =      0x4994;
    final static int EEPROM_SHIFT          =      0x10;

    final static int EEPROM_IR_MODE        =      0x18;
    final static int EEPROM_TS_MODE        =      0x31;
    final static int EEPROM_2ND_DEMOD_ADDR =      0x32;
    final static int EEPROM_IR_TYPE        =      0x34;
    final static int EEPROM_1_IF_L         =      0x38;
    final static int EEPROM_1_IF_H         =      0x39;
    final static int EEPROM_1_TUNER_ID     =      0x3c;
    final static int EEPROM_2_IF_L         =      0x48;
    final static int EEPROM_2_IF_H         =      0x49;
    final static int EEPROM_2_TUNER_ID     =      0x4c;

    /* USB commands */
    final static int CMD_MEM_RD            =      0x00;
    final static int CMD_MEM_WR            =      0x01;
    final static int CMD_I2C_RD            =      0x02;
    final static int CMD_I2C_WR            =      0x03;
    final static int CMD_IR_GET            =      0x18;
    final static int CMD_FW_DL             =      0x21;
    final static int CMD_FW_QUERYINFO      =      0x22;
    final static int CMD_FW_BOOT           =      0x23;
    final static int CMD_FW_DL_BEGIN       =      0x24;
    final static int CMD_FW_DL_END         =      0x25;
    final static int CMD_FW_SCATTER_WR     =      0x29;
    final static int CMD_GENERIC_I2C_RD    =      0x2a;
    final static int CMD_GENERIC_I2C_WR    =      0x2b;

    final static int[] CLOCK_LUT_AF9035 = new int[] {
            20480000, /*      FPGA */
            16384000, /* 16.38 MHz */
            20480000, /* 20.48 MHz */
            36000000, /* 36.00 MHz */
            30000000, /* 30.00 MHz */
            26000000, /* 26.00 MHz */
            28000000, /* 28.00 MHz */
            32000000, /* 32.00 MHz */
            34000000, /* 34.00 MHz */
            24000000, /* 24.00 MHz */
            22000000, /* 22.00 MHz */
            12000000, /* 12.00 MHz */
    };

    final static int[] CLOCK_LUT_IT9135 = new int[] {
            12000000, /* 12.00 MHz */
            20480000, /* 20.48 MHz */
            36000000, /* 36.00 MHz */
            30000000, /* 30.00 MHz */
            26000000, /* 26.00 MHz */
            28000000, /* 28.00 MHz */
            32000000, /* 32.00 MHz */
            34000000, /* 34.00 MHz */
            24000000, /* 24.00 MHz */
            22000000, /* 22.00 MHz */
    };

    static int[][] reg_val_mask_tab(int frame_size, int packet_size, boolean dual_mode) {
        return new int[][] {
                { 0x80f99d, 0x01, 0x01 },
                { 0x80f9a4, 0x01, 0x01 },
                { 0x00dd11, 0x00, 0x20 },
                { 0x00dd11, 0x00, 0x40 },
                { 0x00dd13, 0x00, 0x20 },
                { 0x00dd13, 0x00, 0x40 },
                { 0x00dd11, 0x20, 0x20 },
                { 0x00dd88, (frame_size) & 0xff, 0xff},
                { 0x00dd89, (frame_size >> 8) & 0xff, 0xff},
                { 0x00dd0c, packet_size, 0xff},
                { 0x00dd11, (dual_mode ? 1 : 0) << 6, 0x40 },
                { 0x00dd8a, (frame_size) & 0xff, 0xff},
                { 0x00dd8b, (frame_size >> 8) & 0xff, 0xff},
                { 0x00dd0d, packet_size, 0xff },
                { 0x80f9a3, (dual_mode ? 1 : 0), 0x01 },
                { 0x80f9cd, (dual_mode ? 1 : 0), 0x01 },
                { 0x80f99d, 0x00, 0x01 },
                { 0x80f9a4, 0x00, 0x01 },
        };
    }
}
