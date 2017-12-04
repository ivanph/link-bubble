/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.linkbubble.util;

import android.util.Log;

import com.linkbubble.BuildConfig;


public class CrashTracking {

    public static void logHandledException(Throwable throwable) {
        throwable.printStackTrace();
    }

    public static void log(String message) {
        if (BuildConfig.DEBUG) {
            Log.d("CrashTracking", message);
        }
    }
}
