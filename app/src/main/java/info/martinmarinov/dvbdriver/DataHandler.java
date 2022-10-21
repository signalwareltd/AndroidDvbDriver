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

package info.martinmarinov.dvbdriver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import info.martinmarinov.dvbservice.tools.TsDumpFileUtils;

class DataHandler extends Thread {
    private final DvbFrontendActivity dvbFrontendActivity;
    private final Object recordingLock = new Object();
    private final InputStream is;

    private boolean recording = false;
    private File file;
    private FileOutputStream fos;
    private int recordedSize = 0;

    private long readB = 0;
    private long currFreq;
    private long currBandwidth;

    DataHandler(DvbFrontendActivity dvbFrontendActivity, InputStream is) {
        this.dvbFrontendActivity = dvbFrontendActivity;
        this.is = is;
    }

    void reset() {
        readB = 0;
    }

    void setFreqAndBandwidth(long freq, long bandwidth) {
        this.currFreq = freq;
        this.currBandwidth = bandwidth;
    }

    @Override
    public void interrupt() {
        super.interrupt();
        try {
            is.close();
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        try {
            long nextUpdate = System.currentTimeMillis() + 1_000;
            byte[] b = new byte[1024];
            while (!isInterrupted()) {

                int read = is.read(b);
                if (read > 0) {
                    synchronized (recordingLock) {
                        if (recording) {
                            fos.write(b, 0, read);
                            recordedSize += read;
                        }
                    }

                    readB += b.length;
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        interrupt();
                    }
                    continue;
                }

                if (System.currentTimeMillis() > nextUpdate) {
                    nextUpdate = System.currentTimeMillis() + 1_000;
                    if (recording) {
                        dvbFrontendActivity.announceRecorded(file, recordedSize);
                    } else {
                        dvbFrontendActivity.announceBitRate(readB);
                    }
                    readB = 0;
                }
            }
        } catch (IOException e) {
            if (!isInterrupted()) {
                throw new RuntimeException(e);
            }
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (IOException ignored) {
            }
        }
    }

    boolean isRecording() {
        return recording;
    }

    void startRecording() throws FileNotFoundException {
        synchronized (recordingLock) {
            file = TsDumpFileUtils.getFor(dvbFrontendActivity.getApplicationContext(), currFreq, currBandwidth, new Date());
            fos = new FileOutputStream(file, false);
            recordedSize = 0;
            recording = true;
        }
    }

    void stopRecording() throws IOException {
        synchronized (recordingLock) {
            recording = false;
            fos.close();
            fos = null;
            file = null;
        }
    }
}
