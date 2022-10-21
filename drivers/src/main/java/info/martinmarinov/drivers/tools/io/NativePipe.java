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

package info.martinmarinov.drivers.tools.io;

import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class NativePipe implements Closeable {
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public NativePipe() {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            inputStream = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]) {
                private boolean closed = false;

                @Override
                public void close() throws IOException {
                    if (closed) return;
                    closed = true;
                    super.close();
                    outputStream.close();
                }
            };
            outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]) {
                private boolean closed = false;

                @Override
                public void close() throws IOException {
                    if (closed) return;
                    closed = true;
                    super.close();
                    inputStream.close();
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
        outputStream.close();
    }
}
