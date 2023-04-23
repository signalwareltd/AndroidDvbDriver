package info.martinmarinov.drivers.usb.rtl28xx;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;
import static info.martinmarinov.drivers.DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED;
import static info.martinmarinov.drivers.tools.I2cAdapter.I2cMessage.I2C_M_RD;
import static info.martinmarinov.drivers.tools.SetUtils.setOf;

import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.tools.Check;
import info.martinmarinov.drivers.usb.DvbFrontend;
import info.martinmarinov.drivers.usb.DvbTuner;
import info.martinmarinov.drivers.usb.rtl28xx.Rtl28xxDvbDevice.Rtl28xxI2cAdapter;

public class Cxd2841er implements DvbFrontend {
    private final static String TAG = Cxd2841er.class.getSimpleName();
    private ChipId chipId;

    enum Xtal {
        SONY_XTAL_20500, /* 20.5 MHz */
        SONY_XTAL_24000, /* 24 MHz */
        SONY_XTAL_41000 /* 41 MHz */
    }

    private enum ChipId {
        CXD2837ER(0xb1, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2, DeliverySystem.DVBC)
        )),
        CXD2841ER(0xa7, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2, DeliverySystem.DVBC)
        )),
        CXD2843ER(0xa4, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2, DeliverySystem.DVBC)
        )),
        CXD2854ER(0xc1, new DvbCapabilities(
                42_000_000L,
                1002_000_000L,
                166667L,
                setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2) // also ISDBT
        ));

        private final int chip_id;
        final @NonNull
        DvbCapabilities dvbCapabilities;

        ChipId(int chip_id, DvbCapabilities dvbCapabilities) {
            this.chip_id = chip_id;
            this.dvbCapabilities = dvbCapabilities;
        }
    }

    private enum I2C {
        SLVX,
        SLVT
    }

    private final static int MAX_WRITE_REGSIZE = 16;

    private final Rtl28xxI2cAdapter i2cAdapter;
    private final Resources resources;
    private final Xtal xtal;
    private final int i2c_addr_slvx, i2c_addr_slvt;

    public Cxd2841er(Rtl28xxI2cAdapter i2cAdapter, Resources resources, Xtal xtal, int ic2_addr) {
        this.i2cAdapter = i2cAdapter;
        this.resources = resources;
        this.xtal = xtal;
        this.i2c_addr_slvx = (ic2_addr + 4) >> 1;
        this.i2c_addr_slvt = ic2_addr >> 1;
    }

    @Override
    public DvbCapabilities getCapabilities() {
        Check.notNull(chipId, "Capabilities check before tuner is attached");
        return chipId.dvbCapabilities;
    }

    @Override
    public void attach() throws DvbException {
        this.chipId = cxd2841erChipId();
        Log.d(TAG, "Chip found: " + chipId);
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public void release() {
        chipId = null;
        throw new IllegalStateException();
    }

    @Override
    public void init(DvbTuner tuner) throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public void setParams(long frequency, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public int readSnr() throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public int readRfStrengthPercentage() throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public int readBer() throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public Set<DvbStatus> getStatus() throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public void setPids(int... pids) throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    @Override
    public void disablePidFilter() throws DvbException {
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unsupported_slave_on_tuner) + ": CXD2837ER");
    }

    private ChipId cxd2841erChipId() throws DvbException {
        int chip_id;
        try {
            writeReg(I2C.SLVT, 0, 0);
            chip_id = readReg(I2C.SLVT, 0xfd);
        } catch (Throwable t) {
            writeReg(I2C.SLVX, 0, 0);
            chip_id = readReg(I2C.SLVX, 0xfd);
        }
        for (ChipId c : ChipId.values()) {
            if (c.chip_id == chip_id) {
                return c;
            }
        }
        throw new DvbException(DVB_DEVICE_UNSUPPORTED, resources.getString(R.string.unexpected_chip_id));
    }

    synchronized void write(I2C i2C_addr, int reg, byte[] bytes) throws DvbException {
        write(i2C_addr, reg, bytes, bytes.length);
    }

    synchronized void write(I2C i2C_addr, int reg, byte[] value, int len) throws DvbException {
        if (len + 1 > MAX_WRITE_REGSIZE)
            throw new DvbException(BAD_API_USAGE, resources.getString(R.string.i2c_communication_failure));

        int i2c_addr = (i2C_addr == I2C.SLVX ? i2c_addr_slvx : i2c_addr_slvt);

        byte[] buf = new byte[len + 1];

        buf[0] = (byte) reg;
        System.arraycopy(value, 0, buf, 1, len);

        i2cAdapter.transfer(i2c_addr, 0, buf, len + 1);
    }

    synchronized void writeReg(I2C i2C_addr, int reg, int val) throws DvbException {
        write(i2C_addr, reg, new byte[]{(byte) val});
    }

    synchronized void read(I2C i2C_addr, int reg, byte[] val, int len) throws DvbException {
        int i2c_addr = (i2C_addr == I2C.SLVX ? i2c_addr_slvx : i2c_addr_slvt);
        i2cAdapter.transfer(
                i2c_addr, 0, new byte[]{(byte) reg}, 1,
                i2c_addr, I2C_M_RD, val, len
        );
    }

    synchronized int readReg(I2C i2C_addr, int reg) throws DvbException {
        byte[] ans = new byte[1];
        read(i2C_addr, reg, ans, ans.length);
        return ans[0] & 0xFF;
    }
}
