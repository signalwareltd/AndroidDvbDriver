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

import android.content.res.Resources;

import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.usb.DvbFrontend;

enum Rtl28xxSlaveType {
    SLAVE_DEMOD_NONE(new FrontendCreator() {
        @Override
        public DvbFrontend createFrontend(Rtl28xxDvbDevice rtl28xxDvbDevice, Rtl28xxTunerType tuner, Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2CAdapter, Resources resources) {
            return new Rtl2832Frontend(tuner, i2CAdapter, resources);
        }
    }),
    SLAVE_DEMOD_MN88472(new FrontendCreator() {
        @Override
        public DvbFrontend createFrontend(Rtl28xxDvbDevice rtl28xxDvbDevice, Rtl28xxTunerType tuner, Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2CAdapter, Resources resources) throws DvbException {
            if (tuner != Rtl28xxTunerType.RTL2832_R828D) throw new DvbException(DvbException.ErrorCode.BAD_API_USAGE, resources.getString(R.string.unsupported_slave_on_tuner));

            Rtl2832Frontend master = new Rtl2832Frontend(tuner, i2CAdapter, resources);
            Mn88472 slave = new Mn88472(i2CAdapter, resources);
            return new Rtl2832pFrontend(master, rtl28xxDvbDevice, slave);
        }
    }),
    SLAVE_DEMOD_MN88473(new FrontendCreator() {
        @Override
        public DvbFrontend createFrontend(Rtl28xxDvbDevice rtl28xxDvbDevice, Rtl28xxTunerType tuner, Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2CAdapter, Resources resources) throws DvbException {
            if (tuner != Rtl28xxTunerType.RTL2832_R828D) throw new DvbException(DvbException.ErrorCode.BAD_API_USAGE, resources.getString(R.string.unsupported_slave_on_tuner));

            Rtl2832Frontend master = new Rtl2832Frontend(tuner, i2CAdapter, resources);
            Mn88473 slave = new Mn88473(i2CAdapter, resources);
            return new Rtl2832pFrontend(master, rtl28xxDvbDevice, slave);
        }
    });

    private final FrontendCreator frontendCreator;

    Rtl28xxSlaveType(FrontendCreator frontendCreator) {
        this.frontendCreator = frontendCreator;
    }

    DvbFrontend createFrontend(Rtl28xxDvbDevice rtl28xxDvbDevice, Rtl28xxTunerType tunerType, Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter, Resources resources) throws DvbException {
        return frontendCreator.createFrontend(rtl28xxDvbDevice, tunerType, i2cAdapter, resources);
    }

    private interface FrontendCreator {
        DvbFrontend createFrontend(Rtl28xxDvbDevice rtl28xxDvbDevice, Rtl28xxTunerType tunerType, Rtl28xxDvbDevice.Rtl28xxI2cAdapter i2cAdapter, Resources resources) throws DvbException;
    }
}
