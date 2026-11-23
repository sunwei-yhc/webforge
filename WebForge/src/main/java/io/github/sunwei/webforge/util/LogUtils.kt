package io.github.sunwei.webforge.util

import android.util.Log
import androidx.annotation.RestrictTo
import io.github.sunwei.webforge.config.WebForgeConfig

/**
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
open class LogUtils {
    companion object {
    private const val PREFIX = "webforge-"

    @JvmStatic
    fun isDebug(): Boolean {
        return WebForgeConfig.isDebug || WebForgeConfig.DEBUG
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        if (isDebug()) {
            Log.i(PREFIX + tag, message)
        }
    }

    fun v(tag: String, message: String) {
        if (isDebug()) {
            Log.v(PREFIX + tag, message)
        }
    }

    @JvmStatic
    fun safeCheckCrash(tag: String, msg: String?, tr: Throwable?) {
        if (isDebug()) {
            throw RuntimeException(PREFIX + tag + " " + msg, tr)
        } else {
            Log.e(PREFIX + tag, msg, tr)
        }
    }

    @JvmStatic
    fun e(tag: String?, msg: String?, tr: Throwable?) {
        Log.e(tag, msg, tr)
    }

    @JvmStatic
    fun e(tag: String, message: String) {
        if (isDebug()) {
            Log.e(PREFIX + tag, message)
        }
    }
    }
}
