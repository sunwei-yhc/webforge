package io.github.sunwei.webforge.config

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import android.webkit.ValueCallback
import android.webkit.WebView
import io.github.sunwei.webforge.BuildConfig
import io.github.sunwei.webforge.util.LogUtils
import io.github.sunwei.webforge.util.WebForgeUtils
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 */
open class WebForgeConfig {
    companion object {
    const val FILE_CACHE_PATH: String = "webforge-cache"
    val WEBFORGE_CACHE_PATCH: String = File.separator + "webforge-cache"

    /**
     * 缓存路径
     */
    @JvmField
    var WEBFORGE_FILE_PATH: String? = null

    /**
     * DEBUG 模式 ， 如果需要查看日志请设置为 true
     */
    @JvmField
    var isDebug: Boolean = false

    /**
     * Java 兼容入口，保留旧版本公开的 DEBUG 字段。
     */
    @JvmField
    var DEBUG: Boolean = false

    /**
     * Never enable this in production. It exists only for controlled debug
     * environments that use self-signed certificates.
     */
    @Volatile
    @get:JvmStatic
    @set:JvmStatic
    var isAcceptSslCertificateErrors: Boolean = false

    @Volatile
    private var IS_INITIALIZED = false
    private val TAG: String = WebForgeConfig::class.java.getSimpleName()
    const val WEBFORGE_NAME: String = "WebForge"

    /**
     * WebForge 的版本
     */
    @JvmField
    val WEBFORGE_VERSION: String = WEBFORGE_NAME + "/" + BuildConfig.WEBFORGE_VERSION

    /**
     * 通过JS获取的文件大小， 这里限制最大为5MB ，太大会抛出 OutOfMemoryError
     */
    @JvmField
    var MAX_FILE_LENGTH: Int = 1024 * 1024 * 5
    private val sWarmedUp = AtomicBoolean(false)

    /**
     * Initializes the WebView provider ahead of time. Call this from an idle
     * launch path, not on every page creation.
     */
    @JvmStatic
    fun warmUp(context: Context?) {
        if (context == null || !sWarmedUp.compareAndSet(false, true)) {
            return
        }
        val appContext = context.getApplicationContext()
        val warmUpTask: Runnable = object : Runnable {
            override fun run() {
                var webView: WebView? = null
                try {
                    webView = WebView(appContext)
                    webView.loadUrl("about:blank")
                } catch (ignored: Throwable) {
                    sWarmedUp.set(false)
                    if (isDebug) {
                        ignored.printStackTrace()
                    }
                } finally {
                    if (webView != null) {
                        webView.destroy()
                    }
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            warmUpTask.run()
        } else {
            Handler(Looper.getMainLooper()).post(warmUpTask)
        }
    }

    //获取Cookie
    @JvmStatic
    fun getCookiesByUrl(url: String?): String? {
        return if (CookieManager.getInstance() == null) null else CookieManager.getInstance().getCookie(url)
    }

    @JvmStatic
    fun debug() {
        isDebug = true
        DEBUG = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    /**
     * 删除所有已经过期的 Cookies
     */
    @JvmStatic
    fun removeExpiredCookies() {
        var mCookieManager: CookieManager? = null
        if ((CookieManager.getInstance().also { mCookieManager = it }) != null) { //同步清除
            mCookieManager!!.removeExpiredCookie()
            toSyncCookies()
        }
    }

    /**
     * 同步cookie
     *
     * @param url
     * @param cookies
     */
    @JvmStatic
    fun syncCookie(url: String?, cookies: String?) {
        val mCookieManager = CookieManager.getInstance()
        if (mCookieManager != null) {
            mCookieManager.setCookie(url, cookies)
            toSyncCookies()
        }
    }

    // 解决兼容 Android 4.4 java.lang.NoSuchMethodError: android.webkit.CookieManager.removeSessionCookies
    @JvmOverloads
    @JvmStatic
    fun removeSessionCookies(callback: ValueCallback<Boolean?>? = null) {
        var callback = callback
        if (callback == null) {
            callback = defaultIgnoreCallback
        }
        if (CookieManager.getInstance() == null) {
            callback.onReceiveValue(false)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeSessionCookie()
            toSyncCookies()
            callback.onReceiveValue(true)
            return
        }
        CookieManager.getInstance().removeSessionCookies(callback)
        toSyncCookies()
    }

    /**
     * @param context
     * @return WebView 的缓存路径
     */
    @JvmStatic
    fun getCachePath(context: Context): String {
        return context.getCacheDir().getAbsolutePath() + WEBFORGE_CACHE_PATCH
    }

    /**
     * @param context
     * @return WebForge 缓存路径
     */
    @JvmStatic
    fun getExternalCachePath(context: Context?): String? {
        return context?.let(WebForgeUtils::getWebForgeFilePath)
    }


    //Android  4.4  NoSuchMethodError: android.webkit.CookieManager.removeAllCookies
    /**
     * 删除所有 Cookies
     */
    @JvmOverloads
    @JvmStatic
    fun removeAllCookies(callback: ValueCallback<Boolean?>? = null) {
        var callback = callback
        if (callback == null) {
            callback = defaultIgnoreCallback
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookie()
            toSyncCookies()
            callback.onReceiveValue(!CookieManager.getInstance().hasCookies())
            return
        }
        CookieManager.getInstance().removeAllCookies(callback)
        toSyncCookies()
    }

    /**
     * 清空缓存
     *
     * @param context
     */
    @Synchronized
    @JvmStatic
    fun clearDiskCache(context: Context) {
        try {
            WebForgeUtils.clearCacheFolder(File(getCachePath(context)), 0)
            val path = getExternalCachePath(context)
            if (!TextUtils.isEmpty(path)) {
                val mFile = File(path)
                WebForgeUtils.clearCacheFolder(mFile, 0)
            }
        } catch (throwable: Throwable) {
            if (LogUtils.isDebug()) {
                throwable.printStackTrace()
            }
        }
    }


    @Synchronized
    @JvmStatic
    fun initCookiesManager(context: Context?) {
        if (!IS_INITIALIZED) {
            createCookiesSyncInstance(context)
            IS_INITIALIZED = true
        }
    }

    private fun createCookiesSyncInstance(context: Context?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.createInstance(context)
        }
    }

    @SuppressLint("NewApi")
    private fun toSyncCookies() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.getInstance().sync()
            return
        }
        AsyncTask.THREAD_POOL_EXECUTOR.execute(object : Runnable {
            override fun run() {
                CookieManager.getInstance().flush()
            }
        })
    }

    private val defaultIgnoreCallback: ValueCallback<Boolean?>
        get() = object : ValueCallback<Boolean?> {
            override fun onReceiveValue(ignore: Boolean?) {
                LogUtils.i(TAG, "removeExpiredCookies:" + ignore)
            }
        }
    }
}
