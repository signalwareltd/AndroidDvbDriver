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

import java.io.IOException;

public class DvbException extends IOException {
    public enum ErrorCode {
        BAD_API_USAGE,
        IO_EXCEPTION,
        NO_DVB_DEVICES_FOUND,
        UNSUPPORTED_PLATFORM,
        USB_PERMISSION_DENIED,
        CANNOT_OPEN_USB,
        HARDWARE_EXCEPTION,
        CANNOT_TUNE_TO_FREQ,
        UNSUPPORTED_BANDWIDTH,
        DVB_DEVICE_UNSUPPORTED
    }

    private final ErrorCode errorCode;

    public DvbException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DvbException(ErrorCode errorCode, String message, Exception e) {
        super(message, e);
        this.errorCode = errorCode;
    }

    public DvbException(ErrorCode errorCode, Exception e) {
        super(e);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
