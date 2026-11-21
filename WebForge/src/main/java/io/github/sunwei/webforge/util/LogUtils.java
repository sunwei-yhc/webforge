
package io.github.sunwei.webforge.util;

import android.util.Log;

import io.github.sunwei.webforge.config.WebForgeConfig;

/**
 */
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY)
public class LogUtils {

    private static final String PREFIX = "webforge-";

    public static boolean isDebug() {
        return WebForgeConfig.DEBUG;
    }

    public static void i(String tag, String message) {

        if (isDebug()) {
            Log.i(PREFIX.concat(tag), message);
        }
    }

    static void v(String tag, String message) {

        if (isDebug()) {
            Log.v(PREFIX.concat(tag), message);
        }

    }

    public static void safeCheckCrash(String tag, String msg, Throwable tr) {
        if (isDebug()) {
            throw new RuntimeException(PREFIX.concat(tag) + " " + msg, tr);
        } else {
            Log.e(PREFIX.concat(tag), msg, tr);
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
    }

    public static void e(String tag, String message) {

        if (isDebug()) {
            Log.e(PREFIX.concat(tag), message);
        }
    }
}
