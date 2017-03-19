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

package info.martinmarinov.drivers.tools;

import android.util.Log;

public class Retry {
    private final static String TAG = Retry.class.getSimpleName();

    public static <T extends Throwable> void retry(int times, final ThrowingRunnable<T> throwingRunnable) throws T {
        retry(times, new ThrowingCallable<Void, T>() {
            @Override
            public Void call() throws T {
                throwingRunnable.run();
                return null;
            }
        });
    }

    public static <R, T extends Throwable> R retry(int times, ThrowingCallable<R, T> throwingCallable) throws T {
        long sleep = 100;
        while (true) {
            try {
                return throwingCallable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                if (times-- <= 0) {
                    throw e;
                } else {
                    Log.d(TAG, "Retries left: "+times+", exception "+e);
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e1) {
                        return throwingCallable.call();
                    }
                    sleep *= 3;
                }
            }
        }
    }
}
