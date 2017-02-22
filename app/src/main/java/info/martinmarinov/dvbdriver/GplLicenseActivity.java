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

package info.martinmarinov.dvbdriver;

import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class GplLicenseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpl_license);

        findViewById(R.id.license_btnOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ((TextView) findViewById(R.id.license_Txt)).setText(readToText(getResources().openRawResource(R.raw.copying)));
    }

    private String readToText(InputStream inputStream) {
        try {
            Scanner s = new Scanner(inputStream, "UTF-8").useDelimiter("\\A");
            return s.next();
        } finally {
            closeOrDie(inputStream);
        }
    }

    private static void closeOrDie(Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
