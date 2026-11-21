
package io.github.sunwei.webforge.config;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import io.github.sunwei.webforge.BuildConfig;
import io.github.sunwei.webforge.util.LogUtils;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.sunwei.webforge.util.WebForgeUtils.getWebForgeFilePath;

/**
 */
public class WebForgeConfig {

    public static final String FILE_CACHE_PATH = "webforge-cache";
    static final String WEBFORGE_CACHE_PATCH = File.separator + "webforge-cache";
    /**
     * 缓存路径
     */
    public static String WEBFORGE_FILE_PATH;
    /**
     * DEBUG 模式 ， 如果需要查看日志请设置为 true
     */
    public static boolean DEBUG = false;
    /**
     * Never enable this in production. It exists only for controlled debug
     * environments that use self-signed certificates.
     */
    private static volatile boolean ACCEPT_SSL_CERTIFICATE_ERRORS = false;
    private static volatile boolean IS_INITIALIZED = false;
    private static final String TAG = WebForgeConfig.class.getSimpleName();
    public static final String WEBFORGE_NAME = "WebForge";
    /**
     * WebForge 的版本
     */
    public static final String WEBFORGE_VERSION = WEBFORGE_NAME + "/" + BuildConfig.WEBFORGE_VERSION;
    /**
     * 通过JS获取的文件大小， 这里限制最大为5MB ，太大会抛出 OutOfMemoryError
     */
    public static int MAX_FILE_LENGTH = 1024 * 1024 * 5;
    private static final AtomicBoolean sWarmedUp = new AtomicBoolean(false);

    public static void setAcceptSslCertificateErrors(boolean accept) {
        ACCEPT_SSL_CERTIFICATE_ERRORS = accept;
    }

    public static boolean isAcceptSslCertificateErrors() {
        return ACCEPT_SSL_CERTIFICATE_ERRORS;
    }

    /**
     * Initializes the WebView provider ahead of time. Call this from an idle
     * launch path, not on every page creation.
     */
    public static void warmUp(final Context context) {
        if (context == null || !sWarmedUp.compareAndSet(false, true)) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        Runnable warmUpTask = new Runnable() {
            @Override
            public void run() {
                WebView webView = null;
                try {
                    webView = new WebView(appContext);
                    webView.loadUrl("about:blank");
                } catch (Throwable ignored) {
                    sWarmedUp.set(false);
                    if (DEBUG) {
                        ignored.printStackTrace();
                    }
                } finally {
                    if (webView != null) {
                        webView.destroy();
                    }
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            warmUpTask.run();
        } else {
            new Handler(Looper.getMainLooper()).post(warmUpTask);
        }
    }

    //获取Cookie
    public static String getCookiesByUrl(String url) {
        return CookieManager.getInstance() == null ? null : CookieManager.getInstance().getCookie(url);
    }

    public static void debug() {
        DEBUG = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    /**
     * 删除所有已经过期的 Cookies
     */
    public static void removeExpiredCookies() {
        CookieManager mCookieManager = null;
        if ((mCookieManager = CookieManager.getInstance()) != null) { //同步清除
            mCookieManager.removeExpiredCookie();
            toSyncCookies();
        }
    }

    /**
     * 删除所有 Cookies
     */
    public static void removeAllCookies() {
        removeAllCookies(null);
    }

    // 解决兼容 Android 4.4 java.lang.NoSuchMethodError: android.webkit.CookieManager.removeSessionCookies
    public static void removeSessionCookies() {
        removeSessionCookies(null);
    }

    /**
     * 同步cookie
     *
     * @param url
     * @param cookies
     */
    public static void syncCookie(String url, String cookies) {
        CookieManager mCookieManager = CookieManager.getInstance();
        if (mCookieManager != null) {
            mCookieManager.setCookie(url, cookies);
            toSyncCookies();
        }
    }

    public static void removeSessionCookies(ValueCallback<Boolean> callback) {
        if (callback == null) {
            callback = getDefaultIgnoreCallback();
        }
        if (CookieManager.getInstance() == null) {
            callback.onReceiveValue(new Boolean(false));
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeSessionCookie();
            toSyncCookies();
            callback.onReceiveValue(new Boolean(true));
            return;
        }
        CookieManager.getInstance().removeSessionCookies(callback);
        toSyncCookies();
    }

    /**
     * @param context
     * @return WebView 的缓存路径
     */
    public static String getCachePath(Context context) {
        return context.getCacheDir().getAbsolutePath() + WEBFORGE_CACHE_PATCH;
    }

    /**
     * @param context
     * @return WebForge 缓存路径
     */
    public static String getExternalCachePath(Context context) {
        return getWebForgeFilePath(context);
    }


    //Android  4.4  NoSuchMethodError: android.webkit.CookieManager.removeAllCookies
    public static void removeAllCookies(@Nullable ValueCallback<Boolean> callback) {
        if (callback == null) {
            callback = getDefaultIgnoreCallback();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookie();
            toSyncCookies();
            callback.onReceiveValue(!CookieManager.getInstance().hasCookies());
            return;
        }
        CookieManager.getInstance().removeAllCookies(callback);
        toSyncCookies();
    }

    /**
     * 清空缓存
     *
     * @param context
     */
    public static synchronized void clearDiskCache(Context context) {
        try {
            WebForgeUtils.clearCacheFolder(new File(getCachePath(context)), 0);
            String path = getExternalCachePath(context);
            if (!TextUtils.isEmpty(path)) {
                File mFile = new File(path);
                WebForgeUtils.clearCacheFolder(mFile, 0);
            }
        } catch (Throwable throwable) {
            if (LogUtils.isDebug()) {
                throwable.printStackTrace();
            }
        }
    }


    public static synchronized void initCookiesManager(Context context) {
        if (!IS_INITIALIZED) {
            createCookiesSyncInstance(context);
            IS_INITIALIZED = true;
        }
    }

    private static void createCookiesSyncInstance(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.createInstance(context);
        }
    }

    @android.annotation.SuppressLint("NewApi")
    private static void toSyncCookies() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.getInstance().sync();
            return;
        }
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                CookieManager.getInstance().flush();
            }
        });
    }

    private static ValueCallback<Boolean> getDefaultIgnoreCallback() {
        return new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean ignore) {
                LogUtils.i(TAG, "removeExpiredCookies:" + ignore);
            }
        };
    }
}
