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

package info.martinmarinov.dvbservice;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import info.martinmarinov.drivers.DvbDevice;

class TransferThread extends Thread {
    private final DvbDevice dvbDevice;
    private final ServerSocket serverSocket;
    private final OnClosedCallback callback;

    private IOException lastException = null;
    private InputStream transportStream;

    TransferThread(DvbDevice dvbDevice, ServerSocket serverSocket, OnClosedCallback callback) {
        this.dvbDevice = dvbDevice;
        this.serverSocket = serverSocket;
        this.callback = callback;
    }

    @Override
    public void interrupt() {
        super.interrupt();
        quietClose(serverSocket);
        quietClose(transportStream);
    }

    @Override
    public void run() {
        setName(TransferThread.class.getSimpleName());
        setPriority(NORM_PRIORITY);

        OutputStream os = null;
        Socket socket = null;
        try {
            socket = serverSocket.accept();

            byte[] buf = new byte[Math.min(socket.getSendBufferSize(), 10  * 188)];

            os = socket.getOutputStream();

            transportStream = dvbDevice.getTransportStream(new DvbDevice.StreamCallback() {
                @Override
                public void onStreamException(IOException exception) {
                    lastException = exception;
                    interrupt();
                }

                @Override
                public void onStoppedStreaming() {
                    interrupt();
                }
            });
            while (!isInterrupted()) {
                int inlength = transportStream.read(buf);

                if (inlength > 0) {
                    os.write(buf, 0, inlength);
                } else {
                    // No data, sleep for a bit until available
                    quietSleep(10);
                }

            }
        } catch (IOException e) {
            lastException = e;
        } finally {
            quietClose(os);
            quietClose(socket);
            quietClose(transportStream);
            callback.onClosed();
        }
    }

    private void quietSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            interrupt();
        }
    }

    private void quietClose(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                if (lastException == null) lastException = e;
            }
        }
    }

    private void quietClose(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                if (lastException == null) lastException = e;
            }
        }
    }

    private void quietClose(ServerSocket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                if (lastException == null) lastException = e;
            }
        }
    }

    IOException signalAndWaitToDie() {
        if (isAlive()) {
            interrupt();
            try {
                join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return lastException;
    }

    interface OnClosedCallback {
        void onClosed();
    }
}
