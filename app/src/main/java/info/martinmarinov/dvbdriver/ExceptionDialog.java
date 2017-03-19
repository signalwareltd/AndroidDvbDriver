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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.widget.Toast;

import info.martinmarinov.dvbservice.dialogs.ShowOneInstanceFragmentDialog;
import info.martinmarinov.dvbservice.tools.StackTraceSerializer;

public class ExceptionDialog extends ShowOneInstanceFragmentDialog {
    private static final String DIALOG_TAG = ExceptionDialog.class.getSimpleName();

    private static final String ARGS_EXC = "argsExc";

    public static void showOneInstanceOnly(FragmentManager fragmentManager, Exception e) {
        ExceptionDialog dialog = new ExceptionDialog();

        Bundle args = new Bundle();
        args.putSerializable(ARGS_EXC, e);

        dialog.showOneInstanceOnly(fragmentManager, DIALOG_TAG, args);
    }

    private Exception e;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        e = (Exception) args.getSerializable(ARGS_EXC);
    }

    @Override
    public @NonNull Dialog onCreateDialog(Bundle savedInstanceState) {
        final String msg = getMessage(e);
        return new AlertDialog.Builder(getActivity())
                .setCancelable(true)
                .setMessage(msg)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(android.R.string.dialog_alert_title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                    }
                })
                .setNeutralButton(R.string.send_email, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendEmail(
                                new String[] { getString(R.string.email_feedback_address) },
                                getString(R.string.email_subject),
                                getString(R.string.email_body, msg, getConstants())
                        );
                    }
                })
                .create();
    }

    private String getConstants() {
        StringBuilder res = new StringBuilder();

        res.append("Build.MANUFACTURER: ").append(Build.MANUFACTURER).append('\n');
        res.append("Build.MODEL: ").append(Build.MODEL).append('\n');
        res.append("Build.PRODUCT: ").append(Build.PRODUCT).append('\n');
        res.append("Build.VERSION.SDK_INT: ").append(Build.VERSION.SDK_INT).append('\n');
        res.append("Build.VERSION.RELEASE: ").append(Build.VERSION.RELEASE).append('\n');

        try {
            PackageInfo packageInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            res.append("Driver versionName: ").append(packageInfo.versionName).append('\n');
            res.append("Driver versionCode: ").append(packageInfo.versionCode).append('\n');
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return res.toString();
    }

    private static String getMessage(Exception e) {
        return e.getLocalizedMessage() + "\n\n" + StackTraceSerializer.serialize(e);
    }

    private void sendEmail(String[] addresses, String subject, String message) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_EMAIL, addresses);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(getContext(), R.string.cannot_send_email, Toast.LENGTH_LONG).show();
        }
    }
}
