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

package info.martinmarinov.drivers.usb.rtl28xx;

import info.martinmarinov.drivers.DvbCapabilities;

class Rtl2832FrontendData {
    final static DvbCapabilities CAPABILITIES = new DvbCapabilities(
            174000000L,
            862000000L,
            166667L
    );

    final static byte[][] BW_PARAMS = {
	        /* 6 MHz bandwidth */
            new byte[] {
                    (byte) 0xf5, (byte) 0xff, (byte) 0x15, (byte) 0x38, (byte) 0x5d, (byte) 0x6d, (byte) 0x52, (byte) 0x07, (byte) 0xfa, (byte) 0x2f,
                    (byte) 0x53, (byte) 0xf5, (byte) 0x3f, (byte) 0xca, (byte) 0x0b, (byte) 0x91, (byte) 0xea, (byte) 0x30, (byte) 0x63, (byte) 0xb2,
                    (byte) 0x13, (byte) 0xda, (byte) 0x0b, (byte) 0xc4, (byte) 0x18, (byte) 0x7e, (byte) 0x16, (byte) 0x66, (byte) 0x08, (byte) 0x67,
                    (byte) 0x19, (byte) 0xe0,
            },
	        /*  7 MHz bandwidth */
            new byte[] {
                    (byte) 0xe7, (byte) 0xcc, (byte) 0xb5, (byte) 0xba, (byte) 0xe8, (byte) 0x2f, (byte) 0x67, (byte) 0x61, (byte) 0x00, (byte) 0xaf,
                    (byte) 0x86, (byte) 0xf2, (byte) 0xbf, (byte) 0x59, (byte) 0x04, (byte) 0x11, (byte) 0xb6, (byte) 0x33, (byte) 0xa4, (byte) 0x30,
                    (byte) 0x15, (byte) 0x10, (byte) 0x0a, (byte) 0x42, (byte) 0x18, (byte) 0xf8, (byte) 0x17, (byte) 0xd9, (byte) 0x07, (byte) 0x22,
                    (byte) 0x19, (byte) 0x10,
            },
	        /*  8 MHz bandwidth */
            new byte[] {
                    (byte) 0x09, (byte) 0xf6, (byte) 0xd2, (byte) 0xa7, (byte) 0x9a, (byte) 0xc9, (byte) 0x27, (byte) 0x77, (byte) 0x06, (byte) 0xbf,
                    (byte) 0xec, (byte) 0xf4, (byte) 0x4f, (byte) 0x0b, (byte) 0xfc, (byte) 0x01, (byte) 0x63, (byte) 0x35, (byte) 0x54, (byte) 0xa7,
                    (byte) 0x16, (byte) 0x66, (byte) 0x08, (byte) 0xb4, (byte) 0x19, (byte) 0x6e, (byte) 0x19, (byte) 0x65, (byte) 0x05, (byte) 0xc8,
                    (byte) 0x19, (byte) 0xe0,
            },
    };

    final static int[][] SNR_CONSTANTS = new int[][] {
            new int[] { 85387325, 85387325, 85387325, 85387325 },
            new int[] { 86676178, 86676178, 87167949, 87795660 },
            new int[] { 87659938, 87659938, 87885178, 88241743 },
    };
    final static int CONSTELLATION_NUM = SNR_CONSTANTS.length;
    final static int HIERARCHY_NUM = SNR_CONSTANTS[0].length;

    enum DvbtRegBitName {
        DVBT_SOFT_RST(0x1, 0x1, 2, 2),
        DVBT_IIC_REPEAT(0x1, 0x1, 3, 3),
        DVBT_TR_WAIT_MIN_8K(0x1, 0x88, 11, 2),
        DVBT_RSD_BER_FAIL_VAL(0x1, 0x8f, 15, 0),
        DVBT_EN_BK_TRK(0x1, 0xa6, 7, 7),
        DVBT_AD_EN_REG(0x0, 0x8, 7, 7),
        DVBT_AD_EN_REG1(0x0, 0x8, 6, 6),
        DVBT_EN_BBIN(0x1, 0xb1, 0, 0),
        DVBT_MGD_THD0(0x1, 0x95, 7, 0),
        DVBT_MGD_THD1(0x1, 0x96, 7, 0),
        DVBT_MGD_THD2(0x1, 0x97, 7, 0),
        DVBT_MGD_THD3(0x1, 0x98, 7, 0),
        DVBT_MGD_THD4(0x1, 0x99, 7, 0),
        DVBT_MGD_THD5(0x1, 0x9a, 7, 0),
        DVBT_MGD_THD6(0x1, 0x9b, 7, 0),
        DVBT_MGD_THD7(0x1, 0x9c, 7, 0),
        DVBT_EN_CACQ_NOTCH(0x1, 0x61, 4, 4),
        DVBT_AD_AV_REF(0x0, 0x9, 6, 0),
        DVBT_REG_PI(0x0, 0xa, 2, 0),
        DVBT_PIP_ON(0x0, 0x21, 3, 3),
        DVBT_SCALE1_B92(0x2, 0x92, 7, 0),
        DVBT_SCALE1_B93(0x2, 0x93, 7, 0),
        DVBT_SCALE1_BA7(0x2, 0xa7, 7, 0),
        DVBT_SCALE1_BA9(0x2, 0xa9, 7, 0),
        DVBT_SCALE1_BAA(0x2, 0xaa, 7, 0),
        DVBT_SCALE1_BAB(0x2, 0xab, 7, 0),
        DVBT_SCALE1_BAC(0x2, 0xac, 7, 0),
        DVBT_SCALE1_BB0(0x2, 0xb0, 7, 0),
        DVBT_SCALE1_BB1(0x2, 0xb1, 7, 0),
        DVBT_KB_P1(0x1, 0x64, 3, 1),
        DVBT_KB_P2(0x1, 0x64, 6, 4),
        DVBT_KB_P3(0x1, 0x65, 2, 0),
        DVBT_OPT_ADC_IQ(0x0, 0x6, 5, 4),
        DVBT_AD_AVI(0x0, 0x9, 1, 0),
        DVBT_AD_AVQ(0x0, 0x9, 3, 2),
        DVBT_K1_CR_STEP12(0x2, 0xad, 9, 4),
        DVBT_TRK_KS_P2(0x1, 0x6f, 2, 0),
        DVBT_TRK_KS_I2(0x1, 0x70, 5, 3),
        DVBT_TR_THD_SET2(0x1, 0x72, 3, 0),
        DVBT_TRK_KC_P2(0x1, 0x73, 5, 3),
        DVBT_TRK_KC_I2(0x1, 0x75, 2, 0),
        DVBT_CR_THD_SET2(0x1, 0x76, 7, 6),
        DVBT_PSET_IFFREQ(0x1, 0x19, 21, 0),
        DVBT_SPEC_INV(0x1, 0x15, 0, 0),
        DVBT_RSAMP_RATIO(0x1, 0x9f, 27, 2),
        DVBT_CFREQ_OFF_RATIO(0x1, 0x9d, 23, 4),
        DVBT_FSM_STAGE(0x3, 0x51, 6, 3),
        DVBT_RX_CONSTEL(0x3, 0x3c, 3, 2),
        DVBT_RX_HIER(0x3, 0x3c, 6, 4),
        DVBT_RX_C_RATE_LP(0x3, 0x3d, 2, 0),
        DVBT_RX_C_RATE_HP(0x3, 0x3d, 5, 3),
        DVBT_GI_IDX(0x3, 0x51, 1, 0),
        DVBT_FFT_MODE_IDX(0x3, 0x51, 2, 2),
        DVBT_RSD_BER_EST(0x3, 0x4e, 15, 0),
        DVBT_CE_EST_EVM(0x4, 0xc, 15, 0),
        DVBT_RF_AGC_VAL(0x3, 0x5b, 13, 0),
        DVBT_IF_AGC_VAL(0x3, 0x59, 13, 0),
        DVBT_DAGC_VAL(0x3, 0x5, 7, 0),
        DVBT_SFREQ_OFF(0x3, 0x18, 13, 0),
        DVBT_CFREQ_OFF(0x3, 0x5f, 17, 0),
        DVBT_POLAR_RF_AGC(0x0, 0xe, 1, 1),
        DVBT_POLAR_IF_AGC(0x0, 0xe, 0, 0),
        DVBT_AAGC_HOLD(0x1, 0x4, 5, 5),
        DVBT_EN_RF_AGC(0x1, 0x4, 6, 6),
        DVBT_EN_IF_AGC(0x1, 0x4, 7, 7),
        DVBT_IF_AGC_MIN(0x1, 0x8, 7, 0),
        DVBT_IF_AGC_MAX(0x1, 0x9, 7, 0),
        DVBT_RF_AGC_MIN(0x1, 0xa, 7, 0),
        DVBT_RF_AGC_MAX(0x1, 0xb, 7, 0),
        DVBT_IF_AGC_MAN(0x1, 0xc, 6, 6),
        DVBT_IF_AGC_MAN_VAL(0x1, 0xc, 13, 0),
        DVBT_RF_AGC_MAN(0x1, 0xe, 6, 6),
        DVBT_RF_AGC_MAN_VAL(0x1, 0xe, 13, 0),
        DVBT_DAGC_TRG_VAL(0x1, 0x12, 7, 0),
        DVBT_AGC_TARG_VAL_0(0x1, 0x2, 0, 0),
        DVBT_AGC_TARG_VAL_8_1(0x1, 0x3, 7, 0),
        DVBT_AAGC_LOOP_GAIN(0x1, 0xc7, 5, 1),
        DVBT_LOOP_GAIN2_3_0(0x1, 0x4, 4, 1),
        DVBT_LOOP_GAIN2_4(0x1, 0x5, 7, 7),
        DVBT_LOOP_GAIN3(0x1, 0xc8, 4, 0),
        DVBT_VTOP1(0x1, 0x6, 5, 0),
        DVBT_VTOP2(0x1, 0xc9, 5, 0),
        DVBT_VTOP3(0x1, 0xca, 5, 0),
        DVBT_KRF1(0x1, 0xcb, 7, 0),
        DVBT_KRF2(0x1, 0x7, 7, 0),
        DVBT_KRF3(0x1, 0xcd, 7, 0),
        DVBT_KRF4(0x1, 0xce, 7, 0),
        DVBT_EN_GI_PGA(0x1, 0xe5, 0, 0),
        DVBT_THD_LOCK_UP(0x1, 0xd9, 8, 0),
        DVBT_THD_LOCK_DW(0x1, 0xdb, 8, 0),
        DVBT_THD_UP1(0x1, 0xdd, 7, 0),
        DVBT_THD_DW1(0x1, 0xde, 7, 0),
        DVBT_INTER_CNT_LEN(0x1, 0xd8, 3, 0),
        DVBT_GI_PGA_STATE(0x1, 0xe6, 3, 3),
        DVBT_EN_AGC_PGA(0x1, 0xd7, 0, 0),
        DVBT_CKOUTPAR(0x1, 0x7b, 5, 5),
        DVBT_CKOUT_PWR(0x1, 0x7b, 6, 6),
        DVBT_SYNC_DUR(0x1, 0x7b, 7, 7),
        DVBT_ERR_DUR(0x1, 0x7c, 0, 0),
        DVBT_SYNC_LVL(0x1, 0x7c, 1, 1),
        DVBT_ERR_LVL(0x1, 0x7c, 2, 2),
        DVBT_VAL_LVL(0x1, 0x7c, 3, 3),
        DVBT_SERIAL(0x1, 0x7c, 4, 4),
        DVBT_SER_LSB(0x1, 0x7c, 5, 5),
        DVBT_CDIV_PH0(0x1, 0x7d, 3, 0),
        DVBT_CDIV_PH1(0x1, 0x7d, 7, 4),
        DVBT_MPEG_IO_OPT_2_2(0x0, 0x6, 7, 7),
        DVBT_MPEG_IO_OPT_1_0(0x0, 0x7, 7, 6),
        DVBT_CKOUTPAR_PIP(0x0, 0xb7, 4, 4),
        DVBT_CKOUT_PWR_PIP(0x0, 0xb7, 3, 3),
        DVBT_SYNC_LVL_PIP(0x0, 0xb7, 2, 2),
        DVBT_ERR_LVL_PIP(0x0, 0xb7, 1, 1),
        DVBT_VAL_LVL_PIP(0x0, 0xb7, 0, 0),
        DVBT_CKOUTPAR_PID(0x0, 0xb9, 4, 4),
        DVBT_CKOUT_PWR_PID(0x0, 0xb9, 3, 3),
        DVBT_SYNC_LVL_PID(0x0, 0xb9, 2, 2),
        DVBT_ERR_LVL_PID(0x0, 0xb9, 1, 1),
        DVBT_VAL_LVL_PID(0x0, 0xb9, 0, 0),
        DVBT_SM_PASS(0x1, 0x93, 11, 0),
        DVBT_AD7_SETTING(0x0, 0x11, 15, 0),
        DVBT_RSSI_R(0x3, 0x1, 6, 0),
        DVBT_ACI_DET_IND(0x3, 0x12, 0, 0),
        DVBT_REG_MON(0x0, 0xd, 1, 0),
        DVBT_REG_MONSEL(0x0, 0xd, 2, 2),
        DVBT_REG_GPE(0x0, 0xd, 7, 7),
        DVBT_REG_GPO(0x0, 0x10, 0, 0),
        DVBT_REG_4MSEL(0x0, 0x13, 0, 0);

        final int page;
        final int startAddress;
        final int msb;
        final int lsb;

        DvbtRegBitName(int page, int startAddress, int msb, int lsb) {
            this.page = page;
            this.startAddress = startAddress;
            this.msb = msb;
            this.lsb = lsb;
        }
    }

    static final RegValue[] INITIAL_REGS = new RegValue[] {
            new RegValue(DvbtRegBitName.DVBT_AD_EN_REG, 0x1),
            new RegValue(DvbtRegBitName.DVBT_AD_EN_REG1, 0x1),
            new RegValue(DvbtRegBitName.DVBT_RSD_BER_FAIL_VAL, 0x2800),
            new RegValue(DvbtRegBitName.DVBT_MGD_THD0, 0x10),
            new RegValue(DvbtRegBitName.DVBT_MGD_THD1, 0x20),
            new RegValue(DvbtRegBitName.DVBT_MGD_THD2, 0x20),
            new RegValue(DvbtRegBitName.DVBT_MGD_THD3, 0x40),
            new RegValue(DvbtRegBitName.DVBT_MGD_THD4, 0x22),
            new RegValue(DvbtRegBitName.DVBT_MGD_THD5, 0x32),
            new RegValue(DvbtRegBitName.DVBT_MGD_THD6, 0x37),
            new RegValue(DvbtRegBitName.DVBT_MGD_THD7, 0x39),
            new RegValue(DvbtRegBitName.DVBT_EN_BK_TRK, 0x0),
            new RegValue(DvbtRegBitName.DVBT_EN_CACQ_NOTCH, 0x0),
            new RegValue(DvbtRegBitName.DVBT_AD_AV_REF, 0x2a),
            new RegValue(DvbtRegBitName.DVBT_REG_PI, 0x6),
            new RegValue(DvbtRegBitName.DVBT_PIP_ON, 0x0),
            new RegValue(DvbtRegBitName.DVBT_CDIV_PH0, 0x8),
            new RegValue(DvbtRegBitName.DVBT_CDIV_PH1, 0x8),
            new RegValue(DvbtRegBitName.DVBT_SCALE1_B92, 0x4),
            new RegValue(DvbtRegBitName.DVBT_SCALE1_B93, 0xb0),
            new RegValue(DvbtRegBitName.DVBT_SCALE1_BA7, 0x78),
            new RegValue(DvbtRegBitName.DVBT_SCALE1_BA9, 0x28),
            new RegValue(DvbtRegBitName.DVBT_SCALE1_BAA, 0x59),
            new RegValue(DvbtRegBitName.DVBT_SCALE1_BAB, 0x83),
            new RegValue(DvbtRegBitName.DVBT_SCALE1_BAC, 0xd4),
            new RegValue(DvbtRegBitName.DVBT_SCALE1_BB0, 0x65),
            new RegValue(DvbtRegBitName.DVBT_SCALE1_BB1, 0x43),
            new RegValue(DvbtRegBitName.DVBT_KB_P1, 0x1),
            new RegValue(DvbtRegBitName.DVBT_KB_P2, 0x4),
            new RegValue(DvbtRegBitName.DVBT_KB_P3, 0x7),
            new RegValue(DvbtRegBitName.DVBT_K1_CR_STEP12, 0xa),
            new RegValue(DvbtRegBitName.DVBT_REG_GPE, 0x1),
            new RegValue(DvbtRegBitName.DVBT_SERIAL, 0x0),
            new RegValue(DvbtRegBitName.DVBT_CDIV_PH0, 0x9),
            new RegValue(DvbtRegBitName.DVBT_CDIV_PH1, 0x9),
            new RegValue(DvbtRegBitName.DVBT_MPEG_IO_OPT_2_2, 0x0),
            new RegValue(DvbtRegBitName.DVBT_MPEG_IO_OPT_1_0, 0x0),
            new RegValue(DvbtRegBitName.DVBT_TRK_KS_P2, 0x4),
            new RegValue(DvbtRegBitName.DVBT_TRK_KS_I2, 0x7),
            new RegValue(DvbtRegBitName.DVBT_TR_THD_SET2, 0x6),
            new RegValue(DvbtRegBitName.DVBT_TRK_KC_I2, 0x5),
            new RegValue(DvbtRegBitName.DVBT_CR_THD_SET2, 0x1)
    };

    static final RegValue[] TUNER_INIT_E4000 = new RegValue[] {
            new RegValue(DvbtRegBitName.DVBT_DAGC_TRG_VAL, 0x5a),
            new RegValue(DvbtRegBitName.DVBT_AGC_TARG_VAL_0, 0x0),
            new RegValue(DvbtRegBitName.DVBT_AGC_TARG_VAL_8_1, 0x5a),
            new RegValue(DvbtRegBitName.DVBT_AAGC_LOOP_GAIN, 0x18),
            new RegValue(DvbtRegBitName.DVBT_LOOP_GAIN2_3_0, 0x8),
            new RegValue(DvbtRegBitName.DVBT_LOOP_GAIN2_4, 0x1),
            new RegValue(DvbtRegBitName.DVBT_LOOP_GAIN3, 0x18),
            new RegValue(DvbtRegBitName.DVBT_VTOP1, 0x35),
            new RegValue(DvbtRegBitName.DVBT_VTOP2, 0x21),
            new RegValue(DvbtRegBitName.DVBT_VTOP3, 0x21),
            new RegValue(DvbtRegBitName.DVBT_KRF1, 0x0),
            new RegValue(DvbtRegBitName.DVBT_KRF2, 0x40),
            new RegValue(DvbtRegBitName.DVBT_KRF3, 0x10),
            new RegValue(DvbtRegBitName.DVBT_KRF4, 0x10),
            new RegValue(DvbtRegBitName.DVBT_IF_AGC_MIN, 0x80),
            new RegValue(DvbtRegBitName.DVBT_IF_AGC_MAX, 0x7f),
            new RegValue(DvbtRegBitName.DVBT_RF_AGC_MIN, 0x80),
            new RegValue(DvbtRegBitName.DVBT_RF_AGC_MAX, 0x7f),
            new RegValue(DvbtRegBitName.DVBT_POLAR_RF_AGC, 0x0),
            new RegValue(DvbtRegBitName.DVBT_POLAR_IF_AGC, 0x0),
            new RegValue(DvbtRegBitName.DVBT_AD7_SETTING, 0xe9d4),
            new RegValue(DvbtRegBitName.DVBT_EN_GI_PGA, 0x0),
            new RegValue(DvbtRegBitName.DVBT_THD_LOCK_UP, 0x0),
            new RegValue(DvbtRegBitName.DVBT_THD_LOCK_DW, 0x0),
            new RegValue(DvbtRegBitName.DVBT_THD_UP1, 0x14),
            new RegValue(DvbtRegBitName.DVBT_THD_DW1, 0xec),
            new RegValue(DvbtRegBitName.DVBT_INTER_CNT_LEN, 0xc),
            new RegValue(DvbtRegBitName.DVBT_GI_PGA_STATE, 0x0),
            new RegValue(DvbtRegBitName.DVBT_EN_AGC_PGA, 0x1),
            new RegValue(DvbtRegBitName.DVBT_REG_GPE, 0x1),
            new RegValue(DvbtRegBitName.DVBT_REG_GPO, 0x1),
            new RegValue(DvbtRegBitName.DVBT_REG_MONSEL, 0x1),
            new RegValue(DvbtRegBitName.DVBT_REG_MON, 0x1),
            new RegValue(DvbtRegBitName.DVBT_REG_4MSEL, 0x0),
            new RegValue(DvbtRegBitName.DVBT_SPEC_INV, 0x0)
    };

    static final RegValue[] TUNER_INIT_R820T = new RegValue[] {
            new RegValue(DvbtRegBitName.DVBT_DAGC_TRG_VAL, 0x39),
            new RegValue(DvbtRegBitName.DVBT_AGC_TARG_VAL_0, 0x0),
            new RegValue(DvbtRegBitName.DVBT_AGC_TARG_VAL_8_1, 0x40),
            new RegValue(DvbtRegBitName.DVBT_AAGC_LOOP_GAIN, 0x16),
            new RegValue(DvbtRegBitName.DVBT_LOOP_GAIN2_3_0, 0x8),
            new RegValue(DvbtRegBitName.DVBT_LOOP_GAIN2_4, 0x1),
            new RegValue(DvbtRegBitName.DVBT_LOOP_GAIN3, 0x18),
            new RegValue(DvbtRegBitName.DVBT_VTOP1, 0x35),
            new RegValue(DvbtRegBitName.DVBT_VTOP2, 0x21),
            new RegValue(DvbtRegBitName.DVBT_VTOP3, 0x21),
            new RegValue(DvbtRegBitName.DVBT_KRF1, 0x0),
            new RegValue(DvbtRegBitName.DVBT_KRF2, 0x40),
            new RegValue(DvbtRegBitName.DVBT_KRF3, 0x10),
            new RegValue(DvbtRegBitName.DVBT_KRF4, 0x10),
            new RegValue(DvbtRegBitName.DVBT_IF_AGC_MIN, 0x80),
            new RegValue(DvbtRegBitName.DVBT_IF_AGC_MAX, 0x7f),
            new RegValue(DvbtRegBitName.DVBT_RF_AGC_MIN, 0x80),
            new RegValue(DvbtRegBitName.DVBT_RF_AGC_MAX, 0x7f),
            new RegValue(DvbtRegBitName.DVBT_POLAR_RF_AGC, 0x0),
            new RegValue(DvbtRegBitName.DVBT_POLAR_IF_AGC, 0x0),
            new RegValue(DvbtRegBitName.DVBT_AD7_SETTING, 0xe9f4),
            new RegValue(DvbtRegBitName.DVBT_SPEC_INV, 0x1)
    };

    static class RegValue {
        final DvbtRegBitName reg;
        final long val;

        RegValue(DvbtRegBitName reg, long val) {
            this.reg = reg;
            this.val = val;
        }
    }
}
