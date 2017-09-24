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
import info.martinmarinov.drivers.tools.I2cAdapter;
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
import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;
import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_PID_AVERMEDIA_A867;
import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_PID_AVERMEDIA_TWINSTAR;
import static info.martinmarinov.drivers.usb.DvbUsbIds.USB_VID_AVERMEDIA;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_ADC_MULTIPLIER_2X;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TS_MODE_SERIAL;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TS_MODE_USB;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_FC0011;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_FC0012;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_FC2580;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_38;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_51;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_52;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_60;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_61;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_IT9135_62;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_MXL5007T;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_TDA18218;
import static info.martinmarinov.drivers.usb.af9035.Af9033Config.AF9033_TUNER_TUA9001;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CLOCK_LUT_AF9035;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CLOCK_LUT_IT9135;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_BOOT;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_DL;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_DL_BEGIN;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_DL_END;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_QUERYINFO;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_FW_SCATTER_WR;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_GENERIC_I2C_RD;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_GENERIC_I2C_WR;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_I2C_RD;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_I2C_WR;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_MEM_RD;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.CMD_MEM_WR;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.EEPROM_1_IF_H;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.EEPROM_1_IF_L;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.EEPROM_1_TUNER_ID;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.EEPROM_2ND_DEMOD_ADDR;
import static info.martinmarinov.drivers.usb.af9035.Af9035Data.EEPROM_TS_MODE;
import static info.martinmarinov.drivers.usb.af9035.It913x.IT9133AX_TUNER;
import static info.martinmarinov.drivers.usb.af9035.It913x.IT9133BX_TUNER;
import static info.martinmarinov.drivers.usb.af9035.It913x.IT913X_ROLE_DUAL_MASTER;
import static info.martinmarinov.drivers.usb.af9035.It913x.IT913X_ROLE_SINGLE;

class Af9035DvbDevice extends DvbUsbDevice {
    private final static String TAG = Af9035DvbDevice.class.getSimpleName();

    private final static boolean USB_SPEED_FULL = false; // this is tue only for USB 1.1 which is not on Android

    private final UsbInterface iface;
    private final UsbEndpoint endpoint;
    private final UsbEndpoint controlEndpointIn;
    private final UsbEndpoint controlEndpointOut;

    private final I2cAdapter i2CAdapter = new Af9035I2cAdapter();

    private int chip_version;
    private int chip_type;
    private int firmware;
    private boolean dual_mode;
    private boolean no_eeprom;
    private boolean no_read;
    private byte[] eeprom = new byte[256];
    private int[] af9033_i2c_addr = new int[2];
    private Af9033Config[] af9033_config;

    Af9035DvbDevice(UsbDevice usbDevice, Context context, DeviceFilter filter) throws DvbException {
        super(usbDevice, context, filter, DvbDemux.DvbDmxSwfilter());
        iface = usbDevice.getInterface(0);

        controlEndpointIn = iface.getEndpoint(0);
        controlEndpointOut = iface.getEndpoint(1);
        endpoint = iface.getEndpoint(2);
        // Endpoint 3 is a TS USB_DIR_IN endpoint with address 0x85
        // but I don't know what it is used for

        if (controlEndpointIn.getAddress() != 0x81 || controlEndpointIn.getDirection() != USB_DIR_IN)
            throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_usb_endpoint));
        if (controlEndpointOut.getAddress() != 0x02 || controlEndpointOut.getDirection() != USB_DIR_OUT)
            throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_usb_endpoint));
        if (endpoint.getAddress() != 0x84 || endpoint.getDirection() != USB_DIR_IN)
            throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_usb_endpoint));
    }

    @Override
    public String getDebugString() {
        return "AF9035 device " + getDeviceFilter().getName();
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

        int prechip_version = rd_reg(0x384f);

        Log.d(TAG, String.format("prechip_version=%02x chip_version=%02x chip_type=%04x", prechip_version, chip_version, chip_type));

        int utmp;
        no_eeprom = false;
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
            boolean ts_mode_invalid = false;
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

            Log.d(TAG, "ts mode=" + tmp + " dual mode=" + dual_mode);

            if (ts_mode_invalid) {
                Log.d(TAG, "ts mode=" + tmp + " not supported, defaulting to single tuner mode!");
            }
        }

        ctrlMsg(CMD_FW_QUERYINFO, 0, 1, new byte[]{1}, rbuf.length, rbuf);

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
        for (i = fw_data.length; i > HDR_SIZE; ) {
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
        ctrlMsg(CMD_FW_QUERYINFO, 0, 1, new byte[]{1}, 4, rbuf);

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
    protected synchronized void readConfig() throws DvbException {
        boolean isWarm = identifyState();
        Log.d(TAG, "Device is " + (isWarm ? "WARM" : "COLD"));

        if (!isWarm) {
            byte[] fw_data = readFirmware(firmware);
            download_firmware(fw_data);

            isWarm = identifyState();
            if (!isWarm) throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_load_firmware));
            Log.d(TAG, "Device is WARM");
        }

        // actual read_config

        /* Demod I2C address */
        af9033_i2c_addr[0] = 0x1c;
        af9033_i2c_addr[1] = 0x1d;
        int[] af9033_configadc_multiplier = new int[] { AF9033_ADC_MULTIPLIER_2X, AF9033_ADC_MULTIPLIER_2X };
        int[] af9033_configts_mode = new int[] { AF9033_TS_MODE_USB, AF9033_TS_MODE_SERIAL };

        boolean[] af9033_configdyn0_clk = new boolean[2];
        boolean[] af9033_configspec_inv = new boolean[2];
        int[] af9033_configtuner = new int[2];
        int[] af9033_configclock = new int[2];

        boolean skip_eeprom = false;
        if (chip_type == 0x9135) {
		    /* feed clock for integrated RF tuner */
            af9033_configdyn0_clk[0] = true;
            af9033_configdyn0_clk[1] = true;

            if (chip_version == 0x02) {
                af9033_configtuner[0] = AF9033_TUNER_IT9135_60;
                af9033_configtuner[1] = AF9033_TUNER_IT9135_60;
            } else {
                af9033_configtuner[0] = AF9033_TUNER_IT9135_38;
                af9033_configtuner[1] = AF9033_TUNER_IT9135_38;
            }

            if (no_eeprom) {
			    /* skip rc stuff */

                skip_eeprom = true;
            }
        } else if (chip_type == 0x9306) {
            /*
             * IT930x is an USB bridge, only single demod-single tuner
             * configurations seen so far.
             */
            return;
        }

        if (!skip_eeprom) {
	        /* Skip remote controller */

            if (dual_mode) {
		        /* Read 2nd demodulator I2C address. 8-bit format on eeprom */
                int tmp = eeprom[EEPROM_2ND_DEMOD_ADDR] & 0xFF;
                if (tmp != 0) {
                    af9033_i2c_addr[1] = tmp >> 1;
                }
            }

            int eeprom_offset = 0;
            for (int i = 0; i < (dual_mode ? 2 : 1); i++) {
		        /* tuner */
                int tmp = eeprom[EEPROM_1_TUNER_ID + eeprom_offset] & 0xFF;

		        /* tuner sanity check */
                if (chip_type == 0x9135) {
                    if (chip_version == 0x02) {
				        /* IT9135 BX (v2) */
                        switch (tmp) {
                            case AF9033_TUNER_IT9135_60:
                            case AF9033_TUNER_IT9135_61:
                            case AF9033_TUNER_IT9135_62:
                                af9033_configtuner[i] = tmp;
                                break;
                        }
                    } else {
				    /* IT9135 AX (v1) */
                        switch (tmp) {
                            case AF9033_TUNER_IT9135_38:
                            case AF9033_TUNER_IT9135_51:
                            case AF9033_TUNER_IT9135_52:
                                af9033_configtuner[i] = tmp;
                                break;
                        }
                    }
                } else {
			    /* AF9035 */
                    af9033_configtuner[i] = tmp;
                }

                if (af9033_configtuner[i] != tmp) {
                    Log.d(TAG, String.format("[%d] overriding tuner from %02x to %02x", i, tmp, af9033_configtuner[i]));
                }

                switch (af9033_configtuner[i]) {
                    case AF9033_TUNER_TUA9001:
                    case AF9033_TUNER_FC0011:
                    case AF9033_TUNER_MXL5007T:
                    case AF9033_TUNER_TDA18218:
                    case AF9033_TUNER_FC2580:
                    case AF9033_TUNER_FC0012:
                        af9033_configspec_inv[i] = true;
                        break;
                    case AF9033_TUNER_IT9135_38:
                    case AF9033_TUNER_IT9135_51:
                    case AF9033_TUNER_IT9135_52:
                    case AF9033_TUNER_IT9135_60:
                    case AF9033_TUNER_IT9135_61:
                    case AF9033_TUNER_IT9135_62:
                        break;
                    default:
                        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_tuner_on_device));
                }

		        /* disable dual mode if driver does not support it */
                if (i == 1) {
                    switch (af9033_configtuner[i]) {
                        case AF9033_TUNER_FC0012:
                        case AF9033_TUNER_IT9135_38:
                        case AF9033_TUNER_IT9135_51:
                        case AF9033_TUNER_IT9135_52:
                        case AF9033_TUNER_IT9135_60:
                        case AF9033_TUNER_IT9135_61:
                        case AF9033_TUNER_IT9135_62:
                        case AF9033_TUNER_MXL5007T:
                            break;
                        default:
                            dual_mode = false;
                            Log.w(TAG, "driver does not support 2nd tuner and will disable it");
                    }
                }

		        /* tuner IF frequency */
                tmp = eeprom[EEPROM_1_IF_L + eeprom_offset] & 0xFF;
                int tmp16 = tmp;
                tmp = eeprom[EEPROM_1_IF_H + eeprom_offset] & 0xFF;
                tmp16 |= tmp << 8;

                Log.d(TAG, String.format("[%d]IF=%d", i, tmp16));

                eeprom_offset += 0x10; /* shift for the 2nd tuner params */
            }
        }

	    /* get demod clock */
        int tmp = rd_reg(0x00d800) & 0x0f;

        for (int i = 0; i < 2; i++) {
            if (chip_type == 0x9135) {
                af9033_configclock[i] = CLOCK_LUT_IT9135[tmp];
            } else {
                af9033_configclock[i] = CLOCK_LUT_AF9035[tmp];
            }
        }

        no_read = false;
	    /* Some MXL5007T devices cannot properly handle tuner I2C read ops. */
        if (af9033_configtuner[0] == AF9033_TUNER_MXL5007T && getDeviceFilter().getVendorId() == USB_VID_AVERMEDIA)

            switch (getDeviceFilter().getProductId()) {
                case USB_PID_AVERMEDIA_A867:
                case USB_PID_AVERMEDIA_TWINSTAR:
                    Log.w(TAG, "Device may have issues with I2C read operations. Enabling fix.");
                    no_read = true;
                    break;
            }

        af9033_config = new Af9033Config[] {
                new Af9033Config(af9033_configdyn0_clk[0], af9033_configadc_multiplier[0], af9033_configtuner[0], af9033_configts_mode[0], af9033_configclock[0], af9033_configspec_inv[0]),
                new Af9033Config(af9033_configdyn0_clk[1], af9033_configadc_multiplier[1], af9033_configtuner[1], af9033_configts_mode[1], af9033_configclock[1], af9033_configspec_inv[1])
        };
    }

    @Override
    protected DvbFrontend frontendAttatch() throws DvbException {
        return new Af9033Frontend(resources, af9033_config[0], af9033_i2c_addr[0], i2CAdapter);
    }

    @Override
    protected DvbTuner tunerAttatch() throws DvbException {
        int role = dual_mode ? IT913X_ROLE_DUAL_MASTER : IT913X_ROLE_SINGLE;
        switch (af9033_config[0].tuner) {
            case AF9033_TUNER_IT9135_38:
            case AF9033_TUNER_IT9135_51:
            case AF9033_TUNER_IT9135_52:
                return new It913x(resources, ((Af9033Frontend) frontend).regMap, IT9133AX_TUNER, role);
            case AF9033_TUNER_IT9135_60:
            case AF9033_TUNER_IT9135_61:
            case AF9033_TUNER_IT9135_62:
                return new It913x(resources, ((Af9033Frontend) frontend).regMap, IT9133BX_TUNER, role);
            default:
                throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_tuner_on_device));
        }
    }

    @Override
    protected synchronized void init() throws DvbException {
        int frame_size = (USB_SPEED_FULL ? 5 : 87) * 188 / 4;
        int packet_size = (USB_SPEED_FULL ? 64 : 512) / 4;

        int[][] tab = Af9035Data.reg_val_mask_tab(frame_size, packet_size, dual_mode);

        /* init endpoints */
        for (int[] aTab : tab) {
            wr_reg_mask(aTab[0], aTab[1], aTab[2]);
        }
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
            Log.e(TAG, "too much data wlen=" + o_wlen + " rlen=" + o_rlen);
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
                Log.e(TAG, "command=0x" + Integer.toHexString(cmd) + " checksum mismatch (0x" + Integer.toHexString(tmp_checksum) + " != 0x" + Integer.toHexString(checksum) + ")");
                throw new DvbException(HARDWARE_EXCEPTION, resources.getString(R.string.cannot_send_control_message_checksum));
            }

	        /* check status */
            if (sbuf[2] != 0) {
                Log.e(TAG, "command=0x" + Integer.toHexString(cmd) + " failed fw error=" + (sbuf[2] & 0xFF));
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
            Log.e(TAG, "i2c wr: len=" + len + " is too big!");
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.bad_api_usage));
        }

        byte[] wbuf = new byte[6 + len];

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
        byte[] wbuf = new byte[]{(byte) len, 2, 0, 0, (byte) (reg >> 8), (byte) reg};
        int mbox = (reg >> 16) & 0xff;

        ctrlMsg(CMD_MEM_RD, mbox, wbuf.length, wbuf, len, val);
    }

    /* write single register */
    private void wr_reg(int reg, int val) throws DvbException {
        wr_regs(reg, new byte[]{(byte) val}, 1);
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

    // I2C

    private static boolean AF9035_IS_I2C_XFER_WRITE_READ(I2cAdapter.I2cMessage[] _msg) {
        return _msg.length == 2 && ((_msg[0].flags & I2C_M_RD) == 0) && ((_msg[1].flags & I2C_M_RD) != 0);
    }

    private static boolean AF9035_IS_I2C_XFER_WRITE(I2cAdapter.I2cMessage[] _msg) {
        return _msg.length == 1 && ((_msg[0].flags & I2C_M_RD) == 0);
    }

    private static boolean AF9035_IS_I2C_XFER_READ(I2cAdapter.I2cMessage[] _msg) {
        return _msg.length == 1 && ((_msg[0].flags & I2C_M_RD) != 0);
    }

    private class Af9035I2cAdapter extends I2cAdapter {
        @Override
        protected int masterXfer(I2cMessage[] msg) throws DvbException {
            /*
             * AF9035 I2C sub header is 5 bytes long. Meaning of those bytes are:
             * 0: data len
             * 1: I2C addr << 1
             * 2: reg addr len
             *    byte 3 and 4 can be used as reg addr
             * 3: reg addr MSB
             *    used when reg addr len is set to 2
             * 4: reg addr LSB
             *    used when reg addr len is set to 1 or 2
             *
             * For the simplify we do not use register addr at all.
             * NOTE: As a firmware knows tuner type there is very small possibility
             * there could be some tuner I2C hacks done by firmware and this may
             * lead problems if firmware expects those bytes are used.
             *
             * TODO: Here is few hacks. AF9035 chip integrates AF9033 demodulator.
             * IT9135 chip integrates AF9033 demodulator and RF tuner. For dual
             * tuner devices, there is also external AF9033 demodulator connected
             * via external I2C bus. All AF9033 demod I2C traffic, both single and
             * dual tuner configuration, is covered by firmware - actual USB IO
             * looks just like a memory access.
             * In case of IT913x chip, there is own tuner driver. It is implemented
             * currently as a I2C driver, even tuner IP block is likely build
             * directly into the demodulator memory space and there is no own I2C
             * bus. I2C subsystem does not allow register multiple devices to same
             * bus, having same slave address. Due to that we reuse demod address,
             * shifted by one bit, on that case.
             *
             * For IT930x we use a different command and the sub header is
             * different as well:
             * 0: data len
             * 1: I2C bus (0x03 seems to be only value used)
             * 2: I2C addr << 1
             */


            if (AF9035_IS_I2C_XFER_WRITE_READ(msg)) {
                if (msg[0].len > 40 || msg[1].len > 40) {
			        /* TODO: correct limits > 40 */
                    throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
                } else if ((msg[0].addr == af9033_i2c_addr[0]) || (msg[0].addr == af9033_i2c_addr[1])){
			        /* demod access via firmware interface */
                    int reg = ((msg[0].buf[0] & 0xFF) << 16) | ((msg[0].buf[1] & 0xFF) << 8) | (msg[0].buf[2] & 0xFF);

                    if (msg[0].addr == af9033_i2c_addr[1]) {
                        reg |= 0x100000;
                    }

                    rd_regs(reg, msg[1].buf, msg[1].len);
                } else if (no_read) {
                    for (int i = 0; i < msg[1].len; i++) msg[1].buf[i] = 0;
                    return 0;
                } else {
			        /* I2C write + read */
                    byte[] buf = new byte[ MAX_XFER_SIZE];

                    int cmd = CMD_I2C_RD;
                    int wlen = 5 + msg[0].len;

                    if (chip_type == 0x9306) {
                        cmd = CMD_GENERIC_I2C_RD;
                        wlen = 3 + msg[0].len;
                    }
                    int mbox = ((msg[0].addr & 0x80) >> 3);

                    buf[0] = (byte) msg[1].len;
                    if (chip_type == 0x9306) {
                        buf[1] = 0x03; /* I2C bus */
                        buf[2] = (byte) (msg[0].addr << 1);

                        System.arraycopy(msg[0].buf, 0, buf, 3, msg[0].len);
                    } else {
                        buf[1] = (byte) (msg[0].addr << 1);
                        buf[3] = 0x00; /* reg addr MSB */
                        buf[4] = 0x00; /* reg addr LSB */

				        /* Keep prev behavior for write req len > 2*/
                        if (msg[0].len > 2) {
                            buf[2] = 0x00; /* reg addr len */
                            System.arraycopy(msg[0].buf, 0, buf, 5, msg[0].len);
				            /* Use reg addr fields if write req len <= 2 */
                        } else {
                            wlen = 5;

                            buf[2] = (byte) msg[0].len;
                            if (msg[0].len == 2) {
                                buf[3] = msg[0].buf[0];
                                buf[4] = msg[0].buf[1];
                            } else if (msg[0].len == 1) {
                                buf[4] = msg[0].buf[0];
                            }
                        }
                    }

                    ctrlMsg(cmd, mbox, wlen, buf, msg[1].len, msg[1].buf);
                }
            } else if (AF9035_IS_I2C_XFER_WRITE(msg)) {
                if (msg[0].len > 40) {
			        /* TODO: correct limits > 40 */
                    throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
                } else if ((msg[0].addr == af9033_i2c_addr[0]) || (msg[0].addr == af9033_i2c_addr[1])){
			        /* demod access via firmware interface */
                    int reg = ((msg[0].buf[0] & 0xFF) << 16) | ((msg[0].buf[1] & 0xFF) << 8) | (msg[0].buf[2] & 0xFF);

                    if (msg[0].addr == af9033_i2c_addr[1]) {
                        reg |= 0x100000;
                    }

                    byte[] tmp2 = new byte[msg[0].len - 3];
                    System.arraycopy(msg[0].buf, 3, tmp2, 0, tmp2.length);
                    wr_regs(reg, tmp2, tmp2.length);
                } else {
			        /* I2C write */
                    byte[] buf = new byte[ MAX_XFER_SIZE];

                    int cmd = CMD_I2C_WR;
                    int wlen = 5 + msg[0].len;
                    if (chip_type == 0x9306) {
                        cmd = CMD_GENERIC_I2C_WR;
                        wlen = 3 + msg[0].len;
                    }

                    int mbox = ((msg[0].addr & 0x80) >> 3);
                    buf[0] = (byte) msg[0].len;
                    if (chip_type == 0x9306) {
                        buf[1] = 0x03; /* I2C bus */
                        buf[2] = (byte) (msg[0].addr << 1);

                        System.arraycopy(msg[0].buf, 0, buf, 3, msg[0].len);
                    } else {
                        buf[1] = (byte) (msg[0].addr << 1);
                        buf[2] = 0x00; /* reg addr len */
                        buf[3] = 0x00; /* reg addr MSB */
                        buf[4] = 0x00; /* reg addr LSB */

                        System.arraycopy(msg[0].buf, 0, buf, 5, msg[0].len);
                    }

                    ctrlMsg(cmd, mbox, wlen, buf, 0, null);
                }
            } else if (AF9035_IS_I2C_XFER_READ(msg)) {
                if (msg[0].len > 40) {
			        /* TODO: correct limits > 40 */
                    throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
                } else if (no_read) {
                    for (int i = 0; i < msg[0].len; i++) msg[0].buf[i] = 0;
                    return 0;
                } else {
			        /* I2C read */
                    byte[] buf = new byte[5];

                    int cmd = CMD_I2C_RD;
                    int wlen = buf.length;
                    if (chip_type == 0x9306) {
                        cmd = CMD_GENERIC_I2C_RD;
                        wlen = 3;
                    }
                    int mbox = ((msg[0].addr & 0x80) >> 3);

                    buf[0] = (byte) msg[0].len;
                    if (chip_type == 0x9306) {
                        buf[1] = 0x03; /* I2C bus */
                        buf[2] = (byte) (msg[0].addr << 1);
                    } else {
                        buf[1] = (byte) (msg[0].addr << 1);
                        buf[2] = 0x00; /* reg addr len */
                        buf[3] = 0x00; /* reg addr MSB */
                        buf[4] = 0x00; /* reg addr LSB */
                    }
                    ctrlMsg(cmd, mbox, wlen, buf, msg[0].len, msg[0].buf);
                    return msg.length;
                }
            } else {
                /*
                 * We support only three kind of I2C transactions:
                 * 1) 1 x write + 1 x read (repeated start)
                 * 2) 1 x write
                 * 3) 1 x read
                 */
                throw new DvbException(BAD_API_USAGE, resources.getString(R.string.unsuported_i2c_operation));
            }
            return msg.length;
        }
    }
}
