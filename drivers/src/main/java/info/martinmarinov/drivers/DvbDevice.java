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

package info.martinmarinov.drivers;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import info.martinmarinov.drivers.tools.io.ByteSource;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;

public abstract class DvbDevice implements Closeable {
    private final DvbDemux dvbDemux;

    private DataPump dataPump;

    protected DvbDevice(DvbDemux dvbDemux) {
        this.dvbDemux = dvbDemux;
    }

    public abstract void open() throws DvbException;

    public abstract DeviceFilter getDeviceFilter();

    public abstract DvbCapabilities readCapabilities() throws DvbException;

    public abstract int readSnr() throws DvbException;

    public abstract int readRfStrengthPercentage() throws DvbException;

    public abstract int readBitErrorRate() throws DvbException;

    public abstract Set<DvbStatus> getStatus() throws DvbException;

    protected abstract void tuneTo(long freqHz, long bandwidthHz) throws DvbException;

    public final void tune(long freqHz, long bandwidthHz) throws DvbException {
        tuneTo(freqHz, bandwidthHz);
        dvbDemux.reset();
    }

    public int readDroppedUsbFps() throws DvbException {
        return dvbDemux.getDroppedUsbFps();
    }

    public void setPidFilter(int... pids) {
        dvbDemux.setPidFilter(pids);
    }

    public void disablePidFilter() {
        dvbDemux.disablePidFilter();
    }

    @Override
    public void close() throws IOException {
        while (dataPump != null && dataPump.isAlive()) {
            dataPump.interrupt();
            try {
                dataPump.join();
            } catch (InterruptedException ignored) {}
        }
        dvbDemux.close();
    }

    public InputStream getTransportStream(StreamCallback streamCallback) throws DvbException {
        if (dataPump != null && dataPump.isAlive()) throw new DvbException(BAD_API_USAGE, "Data stream is still running. Please close the input stream first to start a new one");
        dataPump = new DataPump(streamCallback);
        dataPump.start();
        return dvbDemux.getInputStream();
    }

    public interface StreamCallback {
        void onStreamException(IOException exception);
        void onStoppedStreaming();
    }

    protected abstract ByteSource createTsSource();

    /** This thread reads from the USB device as quickly as possible and puts it into the circular buffer.
     * This thread also does pid filtering. **/
    private class DataPump extends Thread {
        private final StreamCallback callback;

        private DataPump(StreamCallback callback) {
            this.callback = callback;
        }

        @Override
        public void interrupt() {
            super.interrupt();
            try {
                dvbDemux.close();
            } catch (IOException e) {
                e.printStackTrace();
                // Close the pipes
            }
        }

        @Override
        public void run() {
            setName(DataPump.class.getSimpleName());
            setPriority(MAX_PRIORITY);

            ByteSource tsSource = createTsSource();

            try {
                tsSource.open();

                dvbDemux.reset();
                while (!isInterrupted()) {
                    try {
                        tsSource.readNext(dvbDemux);
                    } catch (IOException e) {
                        // Pipe is closed from other end
                        interrupt();
                    }
                }
            } catch (InterruptedException ignored) {
                // interrupted is ok
            } catch (IOException e) {
                callback.onStreamException(e);
            } finally {
                try {
                    tsSource.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                callback.onStoppedStreaming();
            }
        }
    }
}
