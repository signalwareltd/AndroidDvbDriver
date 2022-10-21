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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import info.martinmarinov.drivers.tools.FastIntFilter;
import info.martinmarinov.drivers.tools.io.ByteSink;
import info.martinmarinov.drivers.tools.io.NativePipe;

public class DvbDemux implements ByteSink,Closeable {
    private static final boolean DVB_DEMUX_FEED_ERR_PKTS = true;
    private static final boolean CHECK_PACKET_INTEGRITY = true;

    private final int pktSize;
    private final byte[] tsBuf = new byte[204];
    private final NativePipe pipe;
    private final OutputStream out;
    private final FastIntFilter filter = new FastIntFilter(0x1fff);

    @SuppressWarnings("ConstantConditions")
    private final byte[] cntStorage = CHECK_PACKET_INTEGRITY ? new byte[(0x1fff / 2) + 1] : null;

    private int tsBufP = 0;
    private int droppedUsbFps;
    private long lastUpdated;
    private boolean passFullTsStream = false;

    public static DvbDemux DvbDmxSwfilter() {
        return new DvbDemux(188);
    }

    private DvbDemux(int pktSize) {
        this.pktSize = pktSize;
        this.pipe = new NativePipe();
        this.out = pipe.getOutputStream();
        reset();
    }

    void setPidFilter(int ... pids) {
        passFullTsStream = false;
        filter.setFilter(pids);
    }

    void disablePidFilter() {
        passFullTsStream = true;
    }

    @Override
    public void consume(byte[] buf, int count) throws IOException {
        int p = 0;

        if (tsBufP != 0) { /* tsbuf[0] is now 0x47. */
            int i = tsBufP;
            int j = pktSize - i;
            if (count < j) {
                System.arraycopy(buf, 0, tsBuf, i, count);
                tsBufP += count;
                return;
            }
            System.arraycopy(buf, 0, tsBuf, i, j);
            if ((tsBuf[0] & 0xFF) == 0x47) { /* double check */
                swfilterPacket(tsBuf, 0);
            }
            tsBufP = 0;
            p += j;
        }

        while (true) {
            p = findNextPacket(buf, p, count);
            if (p >= count) {
                break;
            }
            if (count - p < pktSize) {
                break;
            }

            if (pktSize == 204 && (buf[p] & 0xFF) == 0xB8) {
                System.arraycopy(buf, p, tsBuf, 0, 188);
                tsBuf[0] = (byte) 0x47;
                swfilterPacket(tsBuf, 0);
            } else {
                swfilterPacket(buf, p);
            }

            p += pktSize;
        }

        int i = count - p;
        if (i != 0) {
            System.arraycopy(buf, p, tsBuf, 0, i);
            tsBufP = i;
            if (pktSize == 204 && (tsBuf[0] & 0xFF) == 0xB8) {
                tsBuf[0] = (byte) 0x47;
            }
        }
    }

    int getDroppedUsbFps() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastUpdated;
        lastUpdated = now;
        double fps = droppedUsbFps * 1000.0 / elapsed;
        droppedUsbFps = 0;
        return (int) Math.abs(fps);
    }

    private int findNextPacket(byte[] buf, int pos, int count) {
        int start = pos, lost;

        while (pos < count) {
            if ((buf[pos] & 0xFF) == 0x47 ||
                    (pktSize == 204 && (buf[pos] & 0xFF) == 0xB8)) {
                break;
            }
            pos++;
        }

        lost = pos - start;
        if (lost != 0) {
		    /* This garbage is part of a valid packet? */
            int backtrack = pos - pktSize;
            if (backtrack >= 0 && ((buf[backtrack] & 0xFF) == 0x47 ||
                    (pktSize == 204 && (buf[backtrack] & 0xFF) == 0xB8))) {
                return backtrack;
            }
        }

        return pos;
    }

    private void swfilterPacket(byte[] buf, int offset) throws IOException {
        int pid = tsPid(buf, offset);

        if ((buf[offset+1] & 0x80) != 0) {
            droppedUsbFps++; // count this as dropped frame
		    /* data in this packet cant be trusted - drop it unless
		     * constant DVB_DEMUX_FEED_ERR_PKTS is set */
            if (!DVB_DEMUX_FEED_ERR_PKTS) return;
        } else {
            if (CHECK_PACKET_INTEGRITY) {
                if (!checkSequenceIntegrity(pid, buf, offset)) droppedUsbFps++;
            }
        }

        if (passFullTsStream || filter.isFiltered(pid)) out.write(buf, offset, 188);
    }

    private boolean checkSequenceIntegrity(int pid, byte[] buf, int offset) {
        if (pid == 0x1FFF) return true; // This PID is garbage that should be ignored always

        int pidLoc = pid >> 1;

        if ((pid & 1) == 0) {
            // even pids are stored on left
            if ((buf[offset + 3] & 0x10) != 0) {
                int val = ((cntStorage[pidLoc] & 0xF0) + 0x10) & 0xF0;
                cntStorage[pidLoc] = (byte) ((cntStorage[pidLoc] & 0x0F) | val);
            }

            if ((buf[offset + 3] & 0x0F) != ((cntStorage[pidLoc] & 0xF0) >> 4)) {
                int val = (buf[offset + 3] & 0x0F) << 4;
                cntStorage[pidLoc] = (byte) ((cntStorage[pidLoc] & 0x0F) | val);
                return false;
            } else {
                return true;
            }
        } else {
            // odd pids are stored on right
            if ((buf[offset + 3] & 0x10) != 0) {
                int val = ((cntStorage[pidLoc] & 0x0F) + 0x01) & 0x0F;
                cntStorage[pidLoc] = (byte) ((cntStorage[pidLoc] & 0xF0) | val);
            }

            if ((buf[offset + 3] & 0x0F) != (cntStorage[pidLoc] & 0x0F)) {
                int val = buf[offset + 3] & 0x0F;
                cntStorage[pidLoc] = (byte) ((cntStorage[pidLoc] & 0xF0) | val);
                return false;
            } else {
                return true;
            }
        }
    }

    private static int tsPid(byte[] buf, int offset) {
        return ((buf[offset+1] & 0x1F) << 8) + (buf[offset+2] & 0xFF);
    }

    void reset() {
        droppedUsbFps = 0;
        lastUpdated = System.currentTimeMillis();

        if (!passFullTsStream) setPidFilter(0); // by default we let through only pid 0
    }

    @Override
    public void close() throws IOException {
        pipe.close();
    }

    InputStream getInputStream() {
        return pipe.getInputStream();
    }
}
