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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;

import info.martinmarinov.dvbservice.dialogs.ShowOneInstanceFragmentDialog;

public class AlertFragmentDialog extends ShowOneInstanceFragmentDialog {
    private static final String DIALOG_TAG = AlertFragmentDialog.class.getSimpleName();

    private static final String ARGS_MSG = "argsMsg";

    public static void showOneInstanceOnly(FragmentManager fragmentManager, String msg) {
        AlertFragmentDialog dialog = new AlertFragmentDialog();

        Bundle args = new Bundle();
        args.putString(ARGS_MSG, msg);

        dialog.showOneInstanceOnly(fragmentManager, DIALOG_TAG, args);
    }

    private String msg;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        msg = args.getString(ARGS_MSG);
    }

    @Override
    public @NonNull Dialog onCreateDialog(Bundle savedInstanceState) {
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
                .create();
    }
}
