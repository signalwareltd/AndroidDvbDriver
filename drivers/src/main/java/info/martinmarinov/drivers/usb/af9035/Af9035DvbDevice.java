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

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDemux;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.SleepUtils;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.usb.DvbUsbDevice;
import info.martinmarinov.usbxfer.AlternateUsbInterface;

import static android.hardware.usb.UsbConstants.USB_DIR_IN;
import static android.hardware.usb.UsbConstants.USB_DIR_OUT;
import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.DvbException.ErrorCode.HARDWARE_EXCEPTION;
import static info.martinmarinov.drivers.DvbException.ErrorCode.IO_EXCEPTION;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_BOOT;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_DL;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_DL_BEGIN;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_DL_END;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_QUERYINFO;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_SCATTER_WR;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_MEM_RD;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_MEM_WR;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.EEPROM_2ND_DEMOD_ADDR;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.EEPROM_TS_MODE;

class Af9035DvbDevice extends DvbUsbDevice {
    private final static String TAG = Af9035DvbDevice.class.getSimpleName();

    private final UsbInterface iface;
    private final UsbEndpoint endpoint;
    private final UsbEndpoint controlEndpointIn;
    private final UsbEndpoint controlEndpointOut;

    private int chip_version, chip_type, prechip_version, firmware;
    private boolean ts_mode_invalid, dual_mode;
    private byte[] eeprom = new byte[256];

    Af9035DvbDevice(UsbDevice usbDevice, Context context, DeviceFilter filter) throws DvbException {
        super(usbDevice, context, filter, DvbDemux.DvbDmxSwfilter());
        iface = usbDevice.getInterface(0);

        controlEndpointIn = iface.getEndpoint(0);
        controlEndpointOut = iface.getEndpoint(1);
        endpoint = iface.getEndpoint(2);
        // Endpoint 3 is a TS USB_DIR_IN endpoint with address 0x85
        // but I don't know what it is used for

        if (controlEndpointIn.getAddress() != 0x81 || controlEndpointIn.getDirection() != USB_DIR_IN) throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_usb_endpoint));
        if (controlEndpointOut.getAddress() != 0x02 || controlEndpointOut.getDirection() != USB_DIR_OUT) throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_usb_endpoint));
        if (endpoint.getAddress() != 0x84 || endpoint.getDirection() != USB_DIR_IN) throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_usb_endpoint));
    }

    @Override
    public String getDebugString() {
        return "AF9035 device "+getDeviceFilter().getName();
    }

    @Override
    protected void powerControl(boolean turnOn) throws DvbException {
        // no-op
    }

    private boolean identifyState() throws DvbException {
        byte[] rbuf = new byte[4];

        rd_regs(0x1222, rbuf, 3);

        chip_version = rbuf[0] & 0xFF;
        chip_type = (rbuf[2] & 0xFF) << 8 | (rbuf[1] & 0xFF);

        prechip_version = rd_reg(0x384f);

        Log.d(TAG, String.format("prechip_version=%02x chip_version=%02x chip_type=%04x", prechip_version, chip_version, chip_type));

        int utmp;
        boolean no_eeprom = false;
        int eeprom_addr = -1;

        if (chip_type == 0x9135) {
            if (chip_version == 0x02) {
                firmware = R.raw.dvbusbit913502fw;
                utmp = 0x00461d;
            } else {
                firmware = R.raw.dvbusbit913501fw;
                utmp = 0x00461b;
            }

		    /* Check if eeprom exists */
            int tmp = rd_reg(utmp);

            if (tmp == 0x00) {
                Log.d(TAG, "no eeprom");
                no_eeprom = true;
            }

            eeprom_addr = 0x4994; // EEPROM_BASE_IT9135
        } else if (chip_type == 0x9306) {
            firmware = R.raw.dvbusbit930301fw;
            no_eeprom = true;
        } else {
            firmware = R.raw.dvbusbaf903502fw;
            eeprom_addr = 0x42f5; //EEPROM_BASE_AF9035;
        }

        if (!no_eeprom) {
	        /* Read and store eeprom */
	        byte[] tempbuff = new byte[32];
            for (int i = 0; i < 256; i += 32) {
                rd_regs(eeprom_addr + i, tempbuff, 32);
                System.arraycopy(tempbuff, 0, eeprom, i, 32);
            }

	        /* check for dual tuner mode */
            int tmp = eeprom[EEPROM_TS_MODE] & 0xFF;
            ts_mode_invalid = false;
            switch (tmp) {
                case 0:
                    break;
                case 1:
                case 3:
                    dual_mode = true;
                    break;
                case 5:
                    if (chip_type != 0x9135 && chip_type != 0x9306) {
                        dual_mode = true;	/* AF9035 */
                    } else {
                        ts_mode_invalid = true;
                    }
                    break;
                default:
                    ts_mode_invalid = true;
            }

            Log.d(TAG, "ts mode="+tmp+" dual mode="+dual_mode);

            if (ts_mode_invalid) {
                Log.d(TAG, "ts mode="+tmp+" not supported, defaulting to single tuner mode!");
            }
        }

        ctrlMsg(CMD_FW_QUERYINFO, 0, 1, new byte[] { 1 }, rbuf.length, rbuf);

        return rbuf[0] != 0 || rbuf[1] != 0 || rbuf[2] != 0 || rbuf[3] != 0;
    }

    private void download_firmware_old(byte[] fw_data) throws DvbException {
        /*
         * Thanks to Daniel GlÃ¶ckner <daniel-gl@gmx.net> about that info!
         *
         * byte 0: MCS 51 core
         *  There are two inside the AF9035 (1=Link and 2=OFDM) with separate
         *  address spaces
         * byte 1-2: Big endian destination address
         * byte 3-4: Big endian number of data bytes following the header
         * byte 5-6: Big endian header checksum, apparently ignored by the chip
         *  Calculated as ~(h[0]*256+h[1]+h[2]*256+h[3]+h[4]*256)
         */
        final int HDR_SIZE = 7;
        final int MAX_DATA = 58;
        byte[] tbuff = new byte[MAX_DATA];

        int i;
        for (i = fw_data.length; i > HDR_SIZE;) {
            int hdr_core = fw_data[fw_data.length - i] & 0xFF;
            int hdr_addr = (fw_data[fw_data.length - i + 1] & 0xFF) << 8;
            hdr_addr |= fw_data[fw_data.length - i + 2] & 0xFF;
            int hdr_data_len = (fw_data[fw_data.length - i + 3] & 0xFF) << 8;
            hdr_data_len |= fw_data[fw_data.length - i + 4] & 0xFF;
            int hdr_checksum = (fw_data[fw_data.length - i + 5] & 0xFF) << 8;
            hdr_checksum |= fw_data[fw_data.length - i + 6] & 0xFF;

            Log.d(TAG, String.format("core=%d addr=%04x data_len=%d checksum=%04x",
                    hdr_core, hdr_addr, hdr_data_len, hdr_checksum));

            if (((hdr_core != 1) && (hdr_core != 2)) || (hdr_data_len > i)) {
                Log.e(TAG, "bad firmware");
                break;
            }

		    /* download begin packet */
            ctrlMsg(CMD_FW_DL_BEGIN, 0, 0, null, 0, null);

		    /* download firmware packet(s) */
            for (int j = HDR_SIZE + hdr_data_len; j > 0; j -= MAX_DATA) {
                int len = j;
                if (len > MAX_DATA) len = MAX_DATA;

                System.arraycopy(fw_data, fw_data.length - i + HDR_SIZE + hdr_data_len - j, tbuff, 0, len);
                ctrlMsg(CMD_FW_DL, 0, 0, tbuff, 0, null);
            }

		    /* download end packet */
            ctrlMsg(CMD_FW_DL_END, 0, 0, null, 0, null);

            i -= hdr_data_len + HDR_SIZE;

            Log.d(TAG, "data uploaded=" + (fw_data.length - i));
        }

	    /* print warn if firmware is bad, continue and see what happens */
        if (i != 0) {
            Log.e(TAG, "bad firmware");
        }
    }

    private void download_firmware_new(byte[] fw_data) throws DvbException {
        final int HDR_SIZE = 7;
        byte[] tbuff = new byte[fw_data.length];

        /*
         * There seems to be following firmware header. Meaning of bytes 0-3
         * is unknown.
         *
         * 0: 3
         * 1: 0, 1
         * 2: 0
         * 3: 1, 2, 3
         * 4: addr MSB
         * 5: addr LSB
         * 6: count of data bytes ?
         */
        for (int i = HDR_SIZE, i_prev = 0; i <= fw_data.length; i++) {
            if (i == fw_data.length || (fw_data[i] == 0x03 && (fw_data[i + 1] == 0x00 || fw_data[i + 1] == 0x01) && fw_data[i + 2] == 0x00)) {
                System.arraycopy(fw_data, i_prev, tbuff, 0, i - i_prev);
                ctrlMsg(CMD_FW_SCATTER_WR, 0, i - i_prev, tbuff, 0, null);
                i_prev = i;
            }
        }
    }

    private void download_firmware(byte[] fw_data) throws DvbException {
        /*
         * In case of dual tuner configuration we need to do some extra
         * initialization in order to download firmware to slave demod too,
         * which is done by master demod.
         * Master feeds also clock and controls power via GPIO.
         */
        if (dual_mode) {
		    /* configure gpioh1, reset & power slave demod */
            wr_reg_mask(0x00d8b0, 0x01, 0x01);
            wr_reg_mask(0x00d8b1, 0x01, 0x01);
            wr_reg_mask(0x00d8af, 0x00, 0x01);

            SleepUtils.usleep(50_000);

            wr_reg_mask(0x00d8af, 0x01, 0x01);

		    /* tell the slave I2C address */
            int tmp = eeprom[EEPROM_2ND_DEMOD_ADDR] & 0xFF;

		    /* Use default I2C address if eeprom has no address set */
            if (tmp == 0) {
                tmp = 0x1d << 1; /* 8-bit format used by chip */
            }

            if ((chip_type == 0x9135) || (chip_type == 0x9306)) {
                wr_reg(0x004bfb, tmp);
            } else {
                wr_reg(0x00417f, tmp);

			    /* enable clock out */
                wr_reg_mask(0x00d81a, 0x01, 0x01);
            }
        }

        if (fw_data[0] == 0x01) {
            download_firmware_old(fw_data);
        } else {
            download_firmware_new(fw_data);
        }

	    /* firmware loaded, request boot */
        ctrlMsg(CMD_FW_BOOT, 0, 0, null, 0, null);

	    /* ensure firmware starts */
        byte[] rbuf = new byte[4];
        ctrlMsg(CMD_FW_QUERYINFO, 0, 1, new byte[] { 1 }, 4, rbuf);

        if (rbuf[0] == 0 && rbuf[1] == 0 && rbuf[2] == 0 && rbuf[3] == 0) {
            throw new DvbException(IO_EXCEPTION, resources.getString(R.string.cannot_load_firmware));
        }

        Log.d(TAG, String.format("firmware version=%d.%d.%d.%d", rbuf[0], rbuf[1], rbuf[2], rbuf[3]));
    }

    private byte[] readFirmware(int resource) throws DvbException {
        InputStream inputStream = resources.openRawResource(resource);
        //noinspection TryFinallyCanBeTryWithResources
        try {
            byte[] fw = new byte[inputStream.available()];
            if (inputStream.read(fw) != fw.length) {
                throw new DvbException(IO_EXCEPTION, resources.getString(R.string.cannot_load_firmware));
            }
            return fw;
        } catch (IOException e) {
            throw new DvbException(IO_EXCEPTION, e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void readConfig() throws DvbException {
        boolean isWarm = identifyState();
        Log.d(TAG, "Device is " + (isWarm ? "WARM" : "COLD"));

        if (!isWarm) {
            byte[] fw_data = readFirmware(firmware);
            download_firmware(fw_data);

            isWarm = identifyState();
            Log.d(TAG, "Device is " + (isWarm ? "WARM" : "COLD"));
        }
    }

    @Override
    protected DvbFrontend frontendAttatch() throws DvbException {
        return null;
    }

    @Override
    protected DvbTuner tunerAttatch() throws DvbException {
        return null;
    }

    @Override
    protected void init() throws DvbException {

    }

    @Override
    protected AlternateUsbInterface getUsbInterface() {
        return AlternateUsbInterface.forUsbInterface(usbDeviceConnection, iface).get(0);
    }

    @Override
    protected UsbEndpoint getUsbEndpoint() {
        return endpoint;
    }

    // communication

    private final static int MAX_XFER_SIZE = 64;
    private final static int BUF_LEN = 64;
    private final static int REQ_HDR_LEN = 4;
    private final static int ACK_HDR_LEN = 3;
    private final static int CHECKSUM_LEN = 2;

    private final Object usbLock = new Object();
    private final byte[] sbuf = new byte[BUF_LEN];

    private int seq = 0;

    private int checksum(byte[] buf, int len) {
        int checksum = 0;

        for (int i = 1; i < len; i++) {
            if ((i % 2) != 0) {
                checksum += (buf[i] & 0xFF) << 8;
            } else {
                checksum += (buf[i] & 0xFF);
            }

            // simulate 16-bit overflow
            if (checksum > 0xFFFF) {
                checksum -= 0x10000;
            }
        }
        checksum = ~checksum;

        return checksum & 0xFFFF;
    }

    private void dvb_usb_generic_rw(@NonNull byte[] wbuf, int wlen, @Nullable byte[] rbuf, int rlen) throws DvbException {
        synchronized (usbLock) {
            int actlen = usbDeviceConnection.bulkTransfer(controlEndpointOut, wbuf, wlen, 5_000);

            if (actlen < wlen) {
                if (actlen >= 0) actlen = -1;
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_send_control_message, actlen));
            }

            // put delay here if needed

            if (rlen > 0) {
                actlen = usbDeviceConnection.bulkTransfer(controlEndpointIn, rbuf, rlen, 5_000);
                if (actlen < rlen) {
                    if (actlen >= 0) actlen = -1;
                    throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_send_control_message, actlen));
                }
            }
        }
    }

    private void ctrlMsg(int cmd, int mbox, int o_wlen, byte[] wbuf, int o_rlen, byte[] rbuf) throws DvbException {
	    /* buffer overflow check */
        if (o_wlen > (BUF_LEN - REQ_HDR_LEN - CHECKSUM_LEN) || o_rlen > (BUF_LEN - ACK_HDR_LEN - CHECKSUM_LEN)) {
            Log.e(TAG, "too much data wlen="+o_wlen+" rlen="+o_rlen);
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
        }

        synchronized (sbuf) {
            sbuf[0] = (byte) (REQ_HDR_LEN + o_wlen + CHECKSUM_LEN - 1);
            sbuf[1] = (byte) mbox;
            sbuf[2] = (byte) cmd;
            sbuf[3] = (byte) seq++;

            // simulate 8-bit overflow
            if (seq > 0xFF) seq -= 0x100;

            if (o_wlen > 0) {
                System.arraycopy(wbuf, 0, sbuf, REQ_HDR_LEN, o_wlen);
            }

            int wlen = REQ_HDR_LEN + o_wlen + CHECKSUM_LEN;
            int rlen = ACK_HDR_LEN + o_rlen + CHECKSUM_LEN;

    	    /* calc and add checksum */
            int checksum = checksum(sbuf, (sbuf[0] & 0xFF) - 1);
            sbuf[(sbuf[0] & 0xFF) - 1] = (byte) (checksum >> 8);
            sbuf[sbuf[0] & 0xFF] = (byte) (checksum & 0xFF);

	        /* no ack for these packets */
            if (cmd == CMD_FW_DL) {
                rlen = 0;
            }

            dvb_usb_generic_rw(sbuf, wlen, sbuf, rlen);

	        /* no ack for those packets */
            if (cmd == CMD_FW_DL) return;

	        /* verify checksum */
            checksum = checksum(sbuf, rlen - 2);
            int tmp_checksum = ((sbuf[rlen - 2] & 0xFF) << 8) | (sbuf[rlen - 1] & 0xFF);
            if (tmp_checksum != checksum) {
                Log.e(TAG, "command=0x"+Integer.toHexString(cmd)+" checksum mismatch (0x"+Integer.toHexString(tmp_checksum)+" != 0x"+Integer.toHexString(checksum)+")");
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_send_control_message_checksum));
            }

	        /* check status */
            if (sbuf[2] != 0) {
                Log.e(TAG, "command=0x"+Integer.toHexString(cmd)+" failed fw error=" + (sbuf[2] & 0xFF));
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_send_control_message, sbuf[2] & 0xFF));
            }

	        /* read request, copy returned data to return buf */
            if (o_rlen > 0) {
                System.arraycopy(sbuf, ACK_HDR_LEN, rbuf, 0, o_rlen);
            }
        }
    }

    /* write multiple registers */
    private void wr_regs(int reg, byte[] val, int len) throws DvbException {
        if (6 + len > MAX_XFER_SIZE) {
            Log.e(TAG, "i2c wr: len="+len+" is too big!");
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
        }

        byte[] wbuf = new byte[6+len];

        wbuf[0] = (byte) len;
        wbuf[1] = 2;
        wbuf[2] = 0;
        wbuf[3] = 0;
        wbuf[4] = (byte) (reg >> 8);
        wbuf[5] = (byte) reg;

        System.arraycopy(val, 0, wbuf, 6, len);

        int mbox = (reg >> 16) & 0xff;
        ctrlMsg(CMD_MEM_WR, mbox, 6 + len, wbuf, 0, null);
    }

    /* read multiple registers */
    private void rd_regs(int reg, byte[] val, int len) throws DvbException {
        byte[] wbuf = new byte[]{ (byte) len, 2, 0, 0, (byte) (reg >> 8), (byte) reg };
        int mbox = (reg >> 16) & 0xff;

        ctrlMsg(CMD_MEM_RD, mbox, wbuf.length, wbuf, len, val);
    }

    /* write single register */
    private void wr_reg(int reg, int val) throws DvbException {
        wr_regs(reg, new byte[] {(byte) val}, 1);
    }

    /* read single register */
    private int rd_reg(int reg) throws DvbException {
        byte[] res = new byte[1];
        rd_regs(reg, res, 1);
        return res[0] & 0xFF;
    }

    /* write single register with mask */
    private void wr_reg_mask(int reg, int val, int mask) throws DvbException {
        /* no need for read if whole reg is written */
        if (mask != 0xff) {
            int tmp = rd_reg(reg);

            val &= mask;
            tmp &= ~mask;
            val |= tmp;
        }

        wr_reg(reg, val);
    }
}
