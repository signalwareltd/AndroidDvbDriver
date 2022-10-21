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

import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.tools.SetUtils;
import info.martinmarinov.drivers.DeliverySystem;

import static info.martinmarinov.drivers.tools.SetUtils.setOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Log.class})
@PowerMockIgnore("jdk.internal.reflect.*")
public class RequestTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DvbDevice dvbDevice;

    @Before
    public void setUp() {
        initMocks(this);
        mockStatic(Log.class);
    }

    @Test
    public void testProtocolVersion() {
        long[] response = getRawResponse(0);

        assertThat(response.length, is(3));
        assertThat(response[0], is(1L)); // success
        assertThat(response[1], is(0L)); // version of protocol
        assertThat(response[2], is((long) Request.values().length)); // number of available requests
    }

    @Test
    public void testExit() {
        long[] response = getRawResponse(1);

        assertThat(response.length, is(1));
        assertThat(response[0], is(1L)); // success
    }

    @Test
    public void testTune() throws Exception {
        long[] response = getRawResponse(2, 506_000_000L, 8_000_000L, 1L);

        assertThat(response.length, is(1));
        assertThat(response[0], is(1L)); // success

        // verify hardware was called
        verify(dvbDevice).tune(506_000_000L, 8_000_000L, DeliverySystem.DVBT2);
    }

    @Test
    public void testGetStatus() throws Exception {
        when(dvbDevice.getStatus()).thenReturn(setOf(DvbStatus.FE_HAS_SIGNAL, DvbStatus.FE_HAS_CARRIER, DvbStatus.FE_HAS_VITERBI));
        when(dvbDevice.readBitErrorRate()).thenReturn(123);
        when(dvbDevice.readDroppedUsbFps()).thenReturn(456);
        when(dvbDevice.readRfStrengthPercentage()).thenReturn(10);
        when(dvbDevice.readSnr()).thenReturn(300);

        long[] response = getRawResponse(3);

        assertThat(response.length, is(9));
        assertThat(response[0], is(1L)); // success
        assertThat(response[1], is(300L)); // SNR
        assertThat(response[2], is(123L)); // BER
        assertThat(response[3], is(456L)); // Dropped FPS
        assertThat(response[4], is(10L)); // RF strength as percentage
        assertThat(response[5], is(1L)); // has signal
        assertThat(response[6], is(1L)); // has carrier
        assertThat(response[7], is(0L)); // no sync
        assertThat(response[8], is(0L)); // no lock
    }

    @Test
    public void setPids() throws Exception {
        long[] response = getRawResponse(4, 0x1FF0L, 0x1277L, 0x0010L);

        assertThat(response.length, is(1));
        assertThat(response[0], is(1L)); // success

        // verify hardware was called
        verify(dvbDevice).setPidFilter(0x1FF0, 0x1277, 0x0010);
    }

    @Test
    public void testGetCapabilities() throws Exception {
        when(dvbDevice.readCapabilities().getFrequencyMin()).thenReturn(174000000L);
        when(dvbDevice.readCapabilities().getFrequencyMax()).thenReturn(862000000L);
        when(dvbDevice.readCapabilities().getFrequencyStepSize()).thenReturn(166667L);
        when(dvbDevice.readCapabilities().getSupportedDeliverySystems()).thenReturn(SetUtils.setOf(DeliverySystem.DVBT, DeliverySystem.DVBT2, DeliverySystem.DVBC));
        when(dvbDevice.getDeviceFilter().getVendorId()).thenReturn(0x0BDA);
        when(dvbDevice.getDeviceFilter().getProductId()).thenReturn(0x2838);

        long[] response = getRawResponse(5);

        assertThat(response.length, is(7));
        assertThat(response[0], is(1L)); // success
        assertThat(response[1], is(0x7L)); // Capabilities flags
        assertThat(response[2], is(174000000L)); // freq min
        assertThat(response[3], is(862000000L)); // freq max
        assertThat(response[4], is(166667L)); // step
        assertThat(response[5], is(0x0BDAL)); // USB vendor id
        assertThat(response[6], is(0x2838L)); // USB product id
    }

    /** Helper to do serialization/deserialization to bytes */
    private long[] getRawResponse(int requestOrdinal, long ... reqArgs) {
        try {
            // Serialize request into bytes
            ByteArrayOutputStream reqArgStream = new ByteArrayOutputStream();
            DataOutputStream reqArgsWriter = new DataOutputStream(reqArgStream);
            reqArgsWriter.write(requestOrdinal);
            reqArgsWriter.write(reqArgs.length);
            for (Long arg : reqArgs) reqArgsWriter.writeLong(arg);
            byte[] requestBytes = reqArgStream.toByteArray();

            // Run request
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); // to store output
            DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(requestBytes));
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

            Request.parseAndExecute(dataInputStream, dataOutputStream, dvbDevice);

            // De-serialize response
            DataInputStream payloadStream = new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray()));
            assertThat(payloadStream.read(), is(requestOrdinal));
            int size = payloadStream.readByte();
            long[] rawPayload = new long[size];
            for (int i = 0; i < size; i++) rawPayload[i] = payloadStream.readLong();
            assertThat(payloadStream.available(), is(0));

            // Some cleanup
            dataInputStream.close();
            dataOutputStream.close();
            payloadStream.close();
            reqArgStream.close();
            return rawPayload;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}