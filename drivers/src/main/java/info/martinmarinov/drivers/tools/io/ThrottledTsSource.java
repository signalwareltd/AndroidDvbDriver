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

package info.martinmarinov.drivers.tools.io;

import android.util.SparseArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ThrottledTsSource implements ByteSource {
    private final File file;
    private final byte[] frame = new byte[188];

    private SparseArray<PrevInfo> timeKeeping = new SparseArray<>();
    private double streamPacketsPerMicroS = -1.0;
    private double packetsAvailable = 0.0;
    private long lastRead;
    private long packetCount;

    private FileInputStream in;

    public ThrottledTsSource(File file) {
        this.file = file;
    }

    @Override
    public void open() throws IOException {
        in = new FileInputStream(file);
    }

    @Override
    public void readNext(ByteSink sink) throws IOException, InterruptedException {
        boolean packetAvailable = false;

        if (streamPacketsPerMicroS < 0.0) {
            packetAvailable = true;
            lastRead = System.nanoTime();
        } else {
            long now = System.nanoTime();
            packetsAvailable += ((now - lastRead) * streamPacketsPerMicroS) / 1_000.0;
            lastRead = now;

            if (packetsAvailable >= 1.0) {
                packetAvailable = true;
                packetsAvailable -= 1.0;
            }
        }

        if (packetAvailable) {
            readWithRewind();
            sink.consume(frame, frame.length);
        } else {
            // Packet not available, wait for a second
            Thread.sleep(10);
        }

        packetCount++;
        Long tsTimestamp = readTimestampMicroSec();
        if (tsTimestamp != null) {
            int pid = getPid();
            PrevInfo prevInfo = timeKeeping.get(pid);

            if (prevInfo == null) {
                timeKeeping.put(pid, new PrevInfo(tsTimestamp, packetCount));
            } else {
                long elapsedTsMicroS = tsTimestamp - prevInfo.prevTsTimestamp;
                if (elapsedTsMicroS != 0) {
                    double currentPacketsPerMicroS = (packetCount - prevInfo.packetCount) / (double) elapsedTsMicroS;

                    if (streamPacketsPerMicroS < 0.0) {
                        streamPacketsPerMicroS = currentPacketsPerMicroS;
                    } else {
                        // Low pass
                        streamPacketsPerMicroS = streamPacketsPerMicroS * 0.5 + 0.5 * currentPacketsPerMicroS;
                    }
                }

                prevInfo.prevTsTimestamp = tsTimestamp;
                prevInfo.packetCount = packetCount;
            }
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private int getPid() {
        return ((frame[1] & 0x1F) << 8) | (frame[2] & 0xFF);
    }

    private Long readTimestampMicroSec() throws IOException {
        if (frame[0] != 0x47) throw new IOException("Broken packet");

        boolean adaptationFieldPresent = (frame[3] & 0x20) != 0;
        if (!adaptationFieldPresent) return null;

        byte adaptationFieldLength = frame[4];
        if (adaptationFieldLength == 0) return null;

        boolean pcrPresent = (frame[5] & 0x10) != 0;
        if (!pcrPresent) return null;

        long programClockReferenceBase = ((long) (frame[6] & 0xFF) << 25) |
                ((long) (frame[7] & 0xFF) << 17) |
                ((long) (frame[8] & 0xFF) << 9) |
                ((long) (frame[9] & 0xFF) << 1) |
                ((long) (frame[10] & 0x80) >> 7);
        long programClockExtensionReference = ((frame[10] & 0x01) << 8) | (frame[11] & 0xFF);

        long pcr = programClockReferenceBase * 300L + programClockExtensionReference;
        // Convert to ms 1 tick = 1 / 27_000_000 s
        return pcr / 27L;
    }

    private void reset() throws IOException {
        timeKeeping.clear();
        in.close();
        in = new FileInputStream(file);
    }

    private void readWithRewind() throws IOException {
        if (!readFrame()) {
            reset();
            if (!readFrame()) throw new IOException("File too short");
        }
    }

    private boolean readFrame() throws IOException {
        int header;
        while ((header = in.read()) != 0x47) {
            if (header == -1) return false; // EOF
        }

        frame[0] = 0x47;

        int rem = frame.length - 1;
        int pos = 1;
        while (rem > 0) {
            int read = in.read(frame, pos, rem);
            pos += read;
            rem -= read;

            if (read <= 0) {
                return false;
            }
        }

        return true;
    }

    private static class PrevInfo {
        private long prevTsTimestamp;
        private long packetCount;

        private PrevInfo(long prevTsTimestamp, long packetCount) {
            this.prevTsTimestamp = prevTsTimestamp;
            this.packetCount = packetCount;
        }
    }
}
