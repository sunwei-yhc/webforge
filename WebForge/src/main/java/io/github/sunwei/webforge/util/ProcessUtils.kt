package io.github.sunwei.webforge.util

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Process
import android.text.TextUtils
import androidx.annotation.RestrictTo
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
open class ProcessUtils {
    companion object {
    @JvmStatic
    fun getCurrentProcessName(context: Context): String? {
        var name: String? = currentProcessNameByFile
        if (!TextUtils.isEmpty(name)) return name
        name = getCurrentProcessNameByAms(context)
        if (!TextUtils.isEmpty(name)) return name
        name = getCurrentProcessNameByReflect(context)
        return name
    }

    private val currentProcessNameByFile: String
        get() {
            try {
                val file = File("/proc/" + Process.myPid() + "/" + "cmdline")
                val mBufferedReader = BufferedReader(FileReader(file))
                val processName = mBufferedReader.readLine().trim { it <= ' ' }
                mBufferedReader.close()
                return processName
            } catch (e: Exception) {
                e.printStackTrace()
                return ""
            }
        }

    private fun getCurrentProcessNameByAms(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        if (am == null) return ""
        val info = am.getRunningAppProcesses()
        if (info == null || info.size == 0) return ""
        val pid = Process.myPid()
        for (aInfo in info) {
            if (aInfo.pid == pid) {
                if (aInfo.processName != null) {
                    return aInfo.processName
                }
            }
        }
        return ""
    }

    private fun getCurrentProcessNameByReflect(context: Context): String? {
        var processName: String? = ""
        try {
            val app = context.getApplicationContext() as Application
            val loadedApkField = app.javaClass.getField("mLoadedApk")
            loadedApkField.setAccessible(true)
            val loadedApk = loadedApkField.get(app)

            val activityThreadField = loadedApk!!.javaClass.getDeclaredField("mActivityThread")
            activityThreadField.setAccessible(true)
            val activityThread = activityThreadField.get(loadedApk)

            val getProcessName = activityThread!!.javaClass.getDeclaredMethod("getProcessName")
            processName = getProcessName.invoke(activityThread) as String?
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return processName
    }
    }
}
