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

package info.martinmarinov.drivers.file;

import java.io.File;

import info.martinmarinov.drivers.DeviceFilter;

class FileDeviceFilter extends DeviceFilter {
    private final File file;

    FileDeviceFilter(String name, File file) {
        super(-1, -1, name);
        this.file = file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        FileDeviceFilter that = (FileDeviceFilter) o;

        return file != null ? file.equals(that.file) : that.file == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (file != null ? file.hashCode() : 0);
        return result;
    }
}
