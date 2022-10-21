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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;

public class WelcomeActivity extends Activity {
    private final static Uri SOURCE_URL = Uri.parse("https://github.com/signalwareltd/AndroidDvbDriver");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        findViewById(R.id.btn_advanced_mode).setOnClickListener(v -> startActivity(new Intent(WelcomeActivity.this, DvbFrontendActivity.class)));

        findViewById(R.id.btn_source_code).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, SOURCE_URL)));

        findViewById(R.id.btn_license).setOnClickListener(v -> startActivity(new Intent(WelcomeActivity.this, GplLicenseActivity.class)));
    }

}
