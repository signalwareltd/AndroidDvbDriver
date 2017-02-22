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

package info.martinmarinov.dvbservice.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ListPickerFragmentDialog<T extends Serializable> extends ShowOneInstanceFragmentDialog {
    private static final String DIALOG_TAG = ListPickerFragmentDialog.class.getSimpleName();

    private static final String ARGS_SIZE = "argsSize";
    private static final String ARG_ID = "arg";

    private static String getArgKey(int id) {
        return ARG_ID + id;
    }

    public static <T extends Serializable> void showOneInstanceOnly(FragmentManager fragmentManager, List<T> options) {
        ListPickerFragmentDialog<T> dialog = new ListPickerFragmentDialog<>();

        Bundle args = new Bundle();
        args.putInt(ARGS_SIZE, options.size());
        for (int i = 0; i < options.size(); i++) {
            args.putSerializable(getArgKey(i), options.get(i));
        }

        dialog.showOneInstanceOnly(fragmentManager, DIALOG_TAG, args);
    }

    private OnSelected<T> callback;
    private List<T> options;

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        int size = args.getInt(ARGS_SIZE);
        options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            options.add((T) args.getSerializable(getArgKey(i)));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        callback = (OnSelected<T>) getActivity();
    }

    @Override
    public @NonNull Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] strings = new String[options.size()];
        for (int i = 0; i < strings.length; i++) strings[i] = options.get(i).toString();
        return new AlertDialog.Builder(getActivity())
                .setCancelable(true)
                .setItems(strings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onListPickerDialogItemSelected(options.get(which));
                    }
                })
                .create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        callback.onListPickerDialogCanceled();
    }

    public interface OnSelected<T> {
        void onListPickerDialogItemSelected(T item);
        void onListPickerDialogCanceled();
    }
}
