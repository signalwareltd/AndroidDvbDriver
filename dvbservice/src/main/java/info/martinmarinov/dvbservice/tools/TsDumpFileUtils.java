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

package info.martinmarinov.dvbservice.tools;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.file.DvbFileDevice;

public class TsDumpFileUtils {
    private final static String TAG = TsDumpFileUtils.class.getSimpleName();

    private final static Locale DEFAULT_LOCALE = Locale.US;
    private final static DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss", DEFAULT_LOCALE);

    public static File getFor(Context ctx, long freq, long bandwidth, Date date) {
        File root = ctx.getExternalFilesDir(null);
        String timestamp = DATE_FORMAT.format(date);
        String filename = String.format(DEFAULT_LOCALE, "mux_%d_%d_%s.ts", freq, bandwidth, timestamp);
        return new File(root, filename);
    }

    public static List<DvbDevice> getDevicesForAllRecordings(Context ctx) {
        LinkedList<DvbDevice> devices = new LinkedList<>();
        Resources resources = ctx.getResources();
        File root = ctx.getExternalFilesDir(null);
        if (root == null) return devices;
        Log.d(TAG, "You can plase ts files in "+root.getPath());

        File[] files = root.listFiles();
        for (File file : files) {
            FreqBandwidth freqAndBandwidth = getFreqAndBandwidth(file);
            if (freqAndBandwidth != null) {
                devices.add(new DvbFileDevice(resources, file, freqAndBandwidth.freq, freqAndBandwidth.bandwidth));
            }
        }
        return devices;
    }

    @VisibleForTesting
    static FreqBandwidth getFreqAndBandwidth(File file) {
        if (!"ts".equals(getExtension(file))) return null;
        String[] parts = file.getName().toLowerCase().split("_");
        if (parts.length != 4) return null;
        if (!"mux".equals(parts[0])) return null;
        try {
            long freq = Long.parseLong(parts[1]);
            long bandwidth = Long.parseLong(parts[2]);
            return new FreqBandwidth(freq, bandwidth);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String getExtension(File file) {
        String[] parts = file.getName().toLowerCase().split("\\.");
        if (parts.length == 0) return null;
        return parts[parts.length-1];
    }

    @VisibleForTesting
    static class FreqBandwidth {
        @VisibleForTesting
        final long freq, bandwidth;

        private FreqBandwidth(long freq, long bandwidth) {
            this.freq = freq;
            this.bandwidth = bandwidth;
        }
    }
}
