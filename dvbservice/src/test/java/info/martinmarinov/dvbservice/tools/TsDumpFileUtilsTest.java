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

package info.martinmarinov.dvbservice.tools;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class TsDumpFileUtilsTest {
    private final static File ROOT = new File("/imaginary/root");

    @Mock
    private Context context;

    @Before
    public void setUp() {
        initMocks(this);
        when(context.getExternalFilesDir(anyString())).thenReturn(ROOT);
    }

    @Test
    public void testNameCreation() {
        File actual = TsDumpFileUtils.getFor(context, 506_000_000L, 8_000_000L, date(2017, 1, 10, 13, 4, 24));
        assertThat(actual, equalTo(new File(ROOT, "mux_506000000_8000000_20170110130424.ts")));
    }

    @SuppressWarnings("ConstantConditions") // yeah I know res could be know
    @Test
    public void testParsing() {
        TsDumpFileUtils.FreqBandwidth res =
                TsDumpFileUtils.getFreqAndBandwidth(new File(ROOT, "mux_506000000_8000000_20170110130424.ts"));

        assertThat(res.freq, is(506_000_000L));
        assertThat(res.bandwidth, is(8_000_000L));
    }

    @SuppressWarnings("ConstantConditions") // yeah I know res could be know
    @Test
    public void testParsing_withoutDate() {
        TsDumpFileUtils.FreqBandwidth res =
                TsDumpFileUtils.getFreqAndBandwidth(new File(ROOT, "mux_506000000_8000000_randomSuffix.ts"));

        assertThat(res.freq, is(506_000_000L));
        assertThat(res.bandwidth, is(8_000_000L));
    }

    @Test
    public void testUnparseable_RandomString() {
        TsDumpFileUtils.FreqBandwidth res =
                TsDumpFileUtils.getFreqAndBandwidth(new File(ROOT, "fsdjfmadjk3312"));

        assertThat(res, nullValue());
    }

    @Test
    public void testUnparseable_noExt() {
        TsDumpFileUtils.FreqBandwidth res =
                TsDumpFileUtils.getFreqAndBandwidth(new File(ROOT, "mux_506000000_8000000_20170110130424"));

        assertThat(res, nullValue());
    }

    @Test
    public void testUnparseable_noPrefix() {
        TsDumpFileUtils.FreqBandwidth res =
                TsDumpFileUtils.getFreqAndBandwidth(new File(ROOT, "506000000_8000000_20170110130424.ts"));

        assertThat(res, nullValue());
    }

    private static Date date(int year, int month, int day, int hour, int minute, int second) {
        Calendar c = new GregorianCalendar();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, second);
        return c.getTime();
    }
}