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

package info.martinmarinov.drivers.usb.rtl28xx;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
class Rtl28xxConst {
    // These go int the index parameter when calling ctrlMsg in Rtl28xxDvbDevice

    static final int DEMOD              = 0x0000;
    static final int USB                = 0x0100;
    static final int SYS                = 0x0200;
    static final int I2C                = 0x0300;
    static final int I2C_DA             = 0x0600;

    static final int CMD_WR_FLAG        = 0x0010;
    static final int CMD_DEMOD_RD       = 0x0000;
    static final int CMD_DEMOD_WR       = 0x0010;
    static final int CMD_USB_RD         = 0x0100;
    static final int CMD_USB_WR         = 0x0110;
    static final int CMD_SYS_RD         = 0x0200;
    static final int CMD_IR_RD          = 0x0201;
    static final int CMD_IR_WR          = 0x0211;
    static final int CMD_SYS_WR         = 0x0210;
    static final int CMD_I2C_RD         = 0x0300;
    static final int CMD_I2C_WR         = 0x0310;
    static final int CMD_I2C_DA_RD      = 0x0600;
    static final int CMD_I2C_DA_WR      = 0x0610;

    // USB registers

    /* SIE Control Registers */
    static final int USB_SYSCTL         = 0x2000 /* USB system control */;
    static final int USB_SYSCTL_0       = 0x2000 /* USB system control */;
    static final int USB_SYSCTL_1       = 0x2001 /* USB system control */;
    static final int USB_SYSCTL_2       = 0x2002 /* USB system control */;
    static final int USB_SYSCTL_3       = 0x2003 /* USB system control */;
    static final int USB_IRQSTAT        = 0x2008 /* SIE interrupt status */;
    static final int USB_IRQEN          = 0x200C /* SIE interrupt enable */;
    static final int USB_CTRL           = 0x2010 /* USB control */;
    static final int USB_STAT           = 0x2014 /* USB status */;
    static final int USB_DEVADDR        = 0x2018 /* USB device address */;
    static final int USB_TEST           = 0x201C /* USB test mode */;
    static final int USB_FRAME_NUMBER   = 0x2020 /* frame number */;
    static final int USB_FIFO_ADDR      = 0x2028 /* address of SIE FIFO RAM */;
    static final int USB_FIFO_CMD       = 0x202A /* SIE FIFO RAM access command */;
    static final int USB_FIFO_DATA      = 0x2030 /* SIE FIFO RAM data */;

    /* Endpoint Registers */
    static final int EP0_SETUPA         = 0x20F8 /* EP 0 setup packet lower byte */;
    static final int EP0_SETUPB         = 0x20FC /* EP 0 setup packet higher byte */;
    static final int USB_EP0_CFG        = 0x2104 /* EP 0 configure */;
    static final int USB_EP0_CTL        = 0x2108 /* EP 0 control */;
    static final int USB_EP0_STAT       = 0x210C /* EP 0 status */;
    static final int USB_EP0_IRQSTAT    = 0x2110 /* EP 0 interrupt status */;
    static final int USB_EP0_IRQEN      = 0x2114 /* EP 0 interrupt enable */;
    static final int USB_EP0_MAXPKT     = 0x2118 /* EP 0 max packet size */;
    static final int USB_EP0_BC         = 0x2120 /* EP 0 FIFO byte counter */;
    static final int USB_EPA_CFG        = 0x2144 /* EP A configure */;
    static final int USB_EPA_CFG_0      = 0x2144 /* EP A configure */;
    static final int USB_EPA_CFG_1      = 0x2145 /* EP A configure */;
    static final int USB_EPA_CFG_2      = 0x2146 /* EP A configure */;
    static final int USB_EPA_CFG_3      = 0x2147 /* EP A configure */;
    static final int USB_EPA_CTL        = 0x2148 /* EP A control */;
    static final int USB_EPA_CTL_0      = 0x2148 /* EP A control */;
    static final int USB_EPA_CTL_1      = 0x2149 /* EP A control */;
    static final int USB_EPA_CTL_2      = 0x214A /* EP A control */;
    static final int USB_EPA_CTL_3      = 0x214B /* EP A control */;
    static final int USB_EPA_STAT       = 0x214C /* EP A status */;
    static final int USB_EPA_IRQSTAT    = 0x2150 /* EP A interrupt status */;
    static final int USB_EPA_IRQEN      = 0x2154 /* EP A interrupt enable */;
    static final int USB_EPA_MAXPKT     = 0x2158 /* EP A max packet size */;
    static final int USB_EPA_MAXPKT_0   = 0x2158 /* EP A max packet size */;
    static final int USB_EPA_MAXPKT_1   = 0x2159 /* EP A max packet size */;
    static final int USB_EPA_MAXPKT_2   = 0x215A /* EP A max packet size */;
    static final int USB_EPA_MAXPKT_3   = 0x215B /* EP A max packet size */;
    static final int USB_EPA_FIFO_CFG   = 0x2160 /* EP A FIFO configure */;
    static final int USB_EPA_FIFO_CFG_0 = 0x2160 /* EP A FIFO configure */;
    static final int USB_EPA_FIFO_CFG_1 = 0x2161 /* EP A FIFO configure */;
    static final int USB_EPA_FIFO_CFG_2 = 0x2162 /* EP A FIFO configure */;
    static final int USB_EPA_FIFO_CFG_3 = 0x2163 /* EP A FIFO configure */;

    /* Debug Registers */
    static final int USB_PHYTSTDIS      = 0x2F04 /* PHY test disable */;
    static final int USB_TOUT_VAL       = 0x2F08 /* USB time-out time */;
    static final int USB_VDRCTRL        = 0x2F10 /* UTMI vendor signal control */;
    static final int USB_VSTAIN         = 0x2F14 /* UTMI vendor signal status in */;
    static final int USB_VLOADM         = 0x2F18 /* UTMI load vendor signal status in */;
    static final int USB_VSTAOUT        = 0x2F1C /* UTMI vendor signal status out */;
    static final int USB_UTMI_TST       = 0x2F80 /* UTMI test */;
    static final int USB_UTMI_STATUS    = 0x2F84 /* UTMI status */;
    static final int USB_TSTCTL         = 0x2F88 /* test control */;
    static final int USB_TSTCTL2        = 0x2F8C /* test control 2 */;
    static final int USB_PID_FORCE      = 0x2F90 /* force PID */;
    static final int USB_PKTERR_CNT     = 0x2F94 /* packet error counter */;
    static final int USB_RXERR_CNT      = 0x2F98 /* RX error counter */;
    static final int USB_MEM_BIST       = 0x2F9C /* MEM BIST test */;
    static final int USB_SLBBIST        = 0x2FA0 /* self-loop-back BIST */;
    static final int USB_CNTTEST        = 0x2FA4 /* counter test */;
    static final int USB_PHYTST         = 0x2FC0 /* USB PHY test */;
    static final int USB_DBGIDX         = 0x2FF0 /* select individual block debug signal */;
    static final int USB_DBGMUX         = 0x2FF4 /* debug signal module mux */;

    // SYS registers

    /* demod control registers */
    static final int SYS_SYS0           = 0x3000 /* include DEMOD_CTL, GPO, GPI, GPOE */;
    static final int SYS_DEMOD_CTL      = 0x3000 /* control register for DVB-T demodulator */;

    /* GPIO registers */
    static final int SYS_GPIO_OUT_VAL   = 0x3001 /* output value of GPIO */;
    static final int SYS_GPIO_IN_VAL    = 0x3002 /* input value of GPIO */;
    static final int SYS_GPIO_OUT_EN    = 0x3003 /* output enable of GPIO */;
    static final int SYS_SYS1           = 0x3004 /* include GPD, SYSINTE, SYSINTS, GP_CFG0 */;
    static final int SYS_GPIO_DIR       = 0x3004 /* direction control for GPIO */;
    static final int SYS_SYSINTE        = 0x3005 /* system interrupt enable */;
    static final int SYS_SYSINTS        = 0x3006 /* system interrupt status */;
    static final int SYS_GPIO_CFG0      = 0x3007 /* PAD configuration for GPIO0-GPIO3 */;
    static final int SYS_SYS2           = 0x3008 /* include GP_CFG1 and 3 reserved bytes */;
    static final int SYS_GPIO_CFG1      = 0x3008 /* PAD configuration for GPIO4 */;
    static final int SYS_DEMOD_CTL1     = 0x300B;

    /* IrDA registers */
    static final int SYS_IRRC_PSR       = 0x3020 /* IR protocol selection */;
    static final int SYS_IRRC_PER       = 0x3024 /* IR protocol extension */;
    static final int SYS_IRRC_SF        = 0x3028 /* IR sampling frequency */;
    static final int SYS_IRRC_DPIR      = 0x302C /* IR data package interval */;
    static final int SYS_IRRC_CR        = 0x3030 /* IR control */;
    static final int SYS_IRRC_RP        = 0x3034 /* IR read port */;
    static final int SYS_IRRC_SR        = 0x3038 /* IR status */;

    /* I2C master registers */
    static final int SYS_I2CCR          = 0x3040 /* I2C clock */;
    static final int SYS_I2CMCR         = 0x3044 /* I2C master control */;
    static final int SYS_I2CMSTR        = 0x3048 /* I2C master SCL timing */;
    static final int SYS_I2CMSR         = 0x304C /* I2C master status */;
    static final int SYS_I2CMFR         = 0x3050 /* I2C master FIFO */;


    // IR registers
    static final int IR_RX_BUF          = 0xFC00;
    static final int IR_RX_IE           = 0xFD00;
    static final int IR_RX_IF           = 0xFD01;
    static final int IR_RX_CTRL         = 0xFD02;
    static final int IR_RX_CFG          = 0xFD03;
    static final int IR_MAX_DURATION0   = 0xFD04;
    static final int IR_MAX_DURATION1   = 0xFD05;
    static final int IR_IDLE_LEN0       = 0xFD06;
    static final int IR_IDLE_LEN1       = 0xFD07;
    static final int IR_GLITCH_LEN      = 0xFD08;
    static final int IR_RX_BUF_CTRL     = 0xFD09;
    static final int IR_RX_BUF_DATA     = 0xFD0A;
    static final int IR_RX_BC           = 0xFD0B;
    static final int IR_RX_CLK          = 0xFD0C;
    static final int IR_RX_C_COUNT_L    = 0xFD0D;
    static final int IR_RX_C_COUNT_H    = 0xFD0E;
    static final int IR_SUSPEND_CTRL    = 0xFD10;
    static final int IR_ERR_TOL_CTRL    = 0xFD11;
    static final int IR_UNIT_LEN        = 0xFD12;
    static final int IR_ERR_TOL_LEN     = 0xFD13;
    static final int IR_MAX_H_TOL_LEN   = 0xFD14;
    static final int IR_MAX_L_TOL_LEN   = 0xFD15;
    static final int IR_MASK_CTRL       = 0xFD16;
    static final int IR_MASK_DATA       = 0xFD17;
    static final int IR_RES_MASK_ADDR   = 0xFD18;
    static final int IR_RES_MASK_T_LEN  = 0xFD19;
}
