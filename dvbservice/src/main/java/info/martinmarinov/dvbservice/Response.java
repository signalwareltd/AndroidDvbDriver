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

package info.martinmarinov.dvbservice;

import java.io.IOException;
import java.io.OutputStream;

/**
 * This is send to the client as a result of every Request received
 *
 * byte 0 will be the Request.ordinal of the Request
 * byte 1 will be N the number of longs in the payload
 * byte 3 to byte 6 will be the success flag (0 or 1). This indicates whether the request was succesful.
 * byte 7 till the end the rest of the longs in the payload follow
 *
 * Basically the success flag is always part of the payload, so the payload
 * always consists of at least one value.
 */
class Response {
    static Response ERROR = error();
    static Response SUCCESS = success();

    private final boolean success;
    private final long[] payload;

    static Response success(long... payload) {
        return new Response(true, payload);
    }

    private static Response error(long... payload) {
        return new Response(false, payload);
    }

    private Response(boolean success, long ... payload) {
        this.success = success;
        this.payload = payload;
    }

    private static void writeLong(byte[] buff, int off, long v) {
        buff[off] = (byte)(v >>> 56);
        buff[off+1] = (byte)(v >>> 48);
        buff[off+2] = (byte)(v >>> 40);
        buff[off+3] = (byte)(v >>> 32);
        buff[off+4] = (byte)(v >>> 24);
        buff[off+5] = (byte)(v >>> 16);
        buff[off+6] = (byte)(v >>>  8);
        buff[off+7] = (byte)(v);
    }

    void serialize(Request request, OutputStream dataOutputStream) throws IOException {
        byte[] payload_raw = new byte[2 + 8 + payload.length * 8];

        payload_raw[0] = (byte) request.ordinal(); // what request called it
        payload_raw[1] = (byte) (payload.length + 1); // the success flag is part of the payload
        writeLong(payload_raw, 2, success ? 1 : 0); // success flag
        for (int i = 0; i < payload.length; i++) {
            writeLong(payload_raw, 2 + 8 + i * 8, payload[i]); // write actual payload if any
        }

        dataOutputStream.write(payload_raw); // raw payload in one chunk to avoid any TCP partitioning
        dataOutputStream.flush(); // force send over the pipe, don't wait
    }
}
