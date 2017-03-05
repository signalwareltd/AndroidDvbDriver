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

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

import info.martinmarinov.drivers.DeliverySystem;

public class DvbFrontendActivity extends AppCompatActivity {
    private Button btnStartStop;
    private Button btnDumpTs;
    private Button btnTune;


    private EditText editFreq;
    private Spinner spinBandwidth;
    private Spinner spinDeliverySystem;

    private CheckedTextView chHardwareReady;
    private CheckedTextView chHasSignal;
    private CheckedTextView chHasCarrier;
    private CheckedTextView chHasSync;
    private CheckedTextView chHasLock;

    private TextView txtSnr;
    private TextView txtDroppedFps;
    private TextView txtBitRate;
    private TextView txtDeviceName;

    private ProgressBar prgRfStrength;
    private ProgressBar prgQuality;

    private DeviceController deviceController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dvb_frontend);

        editFreq = (EditText) findViewById(R.id.editFreq);
        spinBandwidth = (Spinner) findViewById(R.id.spinBandwidth);
        spinDeliverySystem = (Spinner) findViewById(R.id.spinDeliverySystem);

        btnStartStop = (Button) findViewById(R.id.btnStartStop);
        btnDumpTs = (Button) findViewById(R.id.btnDump);
        btnTune = (Button) findViewById(R.id.btnTune);

        chHardwareReady = (CheckedTextView) findViewById(R.id.chHardwareReady);
        chHasSignal = (CheckedTextView) findViewById(R.id.chHasSignal);
        chHasCarrier = (CheckedTextView) findViewById(R.id.chHasCarrier);
        chHasSync = (CheckedTextView) findViewById(R.id.chHasSync);
        chHasLock = (CheckedTextView) findViewById(R.id.chHasLock);

        txtSnr = (TextView) findViewById(R.id.txtSnr);
        txtDroppedFps = (TextView) findViewById(R.id.txtDroppedFps);
        txtBitRate = (TextView) findViewById(R.id.txtBitrate);
        txtDeviceName = (TextView) findViewById(R.id.txtDeviceName);

        prgRfStrength = (ProgressBar) findViewById(R.id.progRf);
        prgQuality = (ProgressBar) findViewById(R.id.progQuality);

        btnStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    long desiredFreq = getUserFreqHz();
                    long desiredBand = getUserBandwidthHz();
                    DeliverySystem desiredDeliverySystem = getDeliveryStstem();

                    v.setEnabled(false);
                    new ControlStarter(desiredFreq, desiredBand, desiredDeliverySystem).start();
                } catch (NumberFormatException e) {
                    handleException(e);
                }
            }
        });

        btnTune.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    deviceController.tuneTo(getUserFreqHz(), getUserBandwidthHz(), getDeliveryStstem());
                } catch (NumberFormatException e) {
                    handleException(e);
                }
            }
        });

        btnDumpTs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataHandler dataHandler = deviceController.getDataHandler();
                try {
                    if (dataHandler.isRecording()) {
                        dataHandler.stopRecording();
                    } else {
                        dataHandler.startRecording();
                    }
                } catch (IOException e) {
                    handleException(e);
                }
                btnDumpTs.setText(dataHandler.isRecording() ? R.string.dump_stop : R.string.dump);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceController != null && deviceController.isAlive()) deviceController.interrupt();
    }

    // GUI helpers

    private long getUserFreqHz() {
        return Integer.valueOf(editFreq.getText().toString()) * 1_000_000L;
    }

    private long getUserBandwidthHz() {
        return Integer.valueOf(spinBandwidth.getSelectedItem().toString()) * 1_000_000L;
    }

    private DeliverySystem getDeliveryStstem() {
        return DeliverySystem.valueOf(spinDeliverySystem.getSelectedItem().toString());
    }

    // GUI helpers that could be called from a non-GUI thread

    void handleException(final Exception e) {
        e.printStackTrace();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ExceptionDialog.showOneInstanceOnly(getSupportFragmentManager(), e);
            }
        });
    }

    void announceMeasurements(final int snr, final int qualityPercertage, final int droppedFps,
                              final int rfStrengthPercentage,
                              final boolean hasSignal, final boolean hasCarrier,
                              final boolean hasSync, final boolean hasLock) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                chHasSignal.setChecked(hasSignal);
                chHasCarrier.setChecked(hasCarrier);
                chHasSync.setChecked(hasSync);
                chHasLock.setChecked(hasLock);

                Resources resources = getResources();
                txtSnr.setText(resources.getString(R.string.snr, snr));
                txtDroppedFps.setText(resources.getString(R.string.dropped_fps, droppedFps));

                prgRfStrength.setProgress(rfStrengthPercentage);
                prgQuality.setProgress(qualityPercertage);
            }
        });
    }

    void announceBitRate(final double bytesPerSecond) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtBitRate.setText(getResources().getString(R.string.mux_speed, bytesPerSecond / 1_000_000.0));
            }
        });
    }


    void announceRecorded(final File file, final int recordedSize) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtBitRate.setText(getResources().getString(R.string.rec_size, file.getPath(), recordedSize / 1_000_000.0));
            }
        });
    }

    void announceOpen(final boolean open, final String deviceName) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                chHardwareReady.setChecked(open);

                chHasSignal.setChecked(false);
                chHasCarrier.setChecked(false);
                chHasSync.setChecked(false);
                chHasLock.setChecked(false);

                if (deviceName != null) {
                    txtDeviceName.setText(deviceName);
                } else {
                    txtDeviceName.setText(R.string.no_device_open);
                }
                txtSnr.setText(R.string.more_info);
                if (open) txtDroppedFps.setText(R.string.more_info);
                if (open) txtBitRate.setText(R.string.more_info);

                if (open) {
                    btnTune.setEnabled(true);
                    btnDumpTs.setEnabled(true);
                    btnStartStop.setEnabled(true);
                    btnStartStop.setText(R.string.stop);
                } else {
                    btnTune.setEnabled(false);
                    btnDumpTs.setEnabled(false);
                    btnStartStop.setEnabled(true);
                    btnStartStop.setText(R.string.start);
                }

                prgRfStrength.setProgress(0);
                prgQuality.setProgress(0);
            }
        });
    }

    // Controlling the device

    private class ControlStarter extends Thread {
        private final long desiredFreq;
        private final long desiredBand;
        private final DeliverySystem desiredDeliverySystem;

        ControlStarter(long desiredFreq, long desiredBand, DeliverySystem desiredDeliverySystem) {
            this.desiredFreq = desiredFreq;
            this.desiredBand = desiredBand;
            this.desiredDeliverySystem = desiredDeliverySystem;
        }

        @Override
        public void run() {
            if (deviceController != null && deviceController.isAlive()) {
                // wait for device to close
                deviceController.interrupt();
                try {
                    deviceController.join();
                } catch (InterruptedException e) {
                    handleException(e);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnStartStop.setEnabled(true);
                            btnDumpTs.setEnabled(false);
                            btnTune.setEnabled(false);
                        }
                    });
                }
            } else {
                deviceController = new DeviceController(DvbFrontendActivity.this, desiredFreq, desiredBand, desiredDeliverySystem);
                deviceController.start();
            }
        }
    }

}
