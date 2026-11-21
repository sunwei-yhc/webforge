
package io.github.sunwei.webforge.download;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.download.library.DownloadImpl;
import com.download.library.DownloadListenerAdapter;
import com.download.library.Extra;
import com.download.library.ResourceRequest;

import io.github.sunwei.webforge.R;
import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.permission.PermissionInterceptor;
import io.github.sunwei.webforge.ui.PageUiController;
import io.github.sunwei.webforge.util.LogUtils;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 */
public class PageDownloadListener implements android.webkit.DownloadListener {
    /**
     * Application Context
     */
    protected Context mContext;
    protected ConcurrentHashMap<String, ResourceRequest> mDownloadTasks = new ConcurrentHashMap<>();
    /**
     * Activity
     */
    protected WeakReference<Activity> mActivityWeakReference = null;
    /**
     * TAG 用于打印，标识
     */
    private static final String TAG = PageDownloadListener.class.getSimpleName();
    /**
     * 权限拦截
     */
    protected PermissionInterceptor mPermissionListener = null;
    /**
     * PageUiController
     */
    protected WeakReference<PageUiController> mWebForgeUIController;

    private static Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean isInstallDownloader;

    protected PageDownloadListener(Activity activity, WebView webView, PermissionInterceptor permissionInterceptor) {
        this.mContext = activity.getApplicationContext();
        this.mActivityWeakReference = new WeakReference<Activity>(activity);
        this.mPermissionListener = permissionInterceptor;
        this.mWebForgeUIController = new WeakReference<PageUiController>(WebForgeUtils.getWebForgeUIControllerByWebView(webView));
        try {
            DownloadImpl.getInstance(this.mContext);
            isInstallDownloader = true;
        } catch (Throwable throwable) {
            LogUtils.e(TAG, "implementation 'com.download.library:Downloader:x.x.x'");
            if (LogUtils.isDebug()) {
                throwable.printStackTrace();
            }
            isInstallDownloader = false;
        }
    }


    @Override
    public void onDownloadStart(final String url, final String userAgent, final String contentDisposition, final String mimetype, final long contentLength) {
        if (!isInstallDownloader) {
            LogUtils.e(TAG, "unable start download " + url + "; implementation 'com.download.library:Downloader:x.x.x'");
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                onDownloadStartInternal(url, userAgent, contentDisposition, mimetype, contentLength);
            }
        });
    }

    protected void onDownloadStartInternal(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        if (null == mActivityWeakReference.get() || mActivityWeakReference.get().isFinishing()) {
            return;
        }
        if (null != this.mPermissionListener) {
            if (this.mPermissionListener.intercept(url, new String[]{}, "download")) {
                return;
            }
        }
        ResourceRequest resourceRequest = createResourceRequest(url);
        this.mDownloadTasks.put(url, resourceRequest);
        preDownload(url);
    }

    protected ResourceRequest createResourceRequest(String url) {
        return DownloadImpl.getInstance(this.mContext).with(url).setEnableIndicator(true).autoOpenIgnoreMD5();
    }


    protected void preDownload(String url) {
        // 移动数据
        if (!isForceRequest(url) &&
                WebForgeUtils.checkNetworkType(mContext) > 1) {
            showDialog(url);
            return;
        }
        performDownload(url);
    }

    protected boolean isForceRequest(String url) {
        ResourceRequest resourceRequest = mDownloadTasks.get(url);
        if (null != resourceRequest) {
            return resourceRequest.getDownloadTask().isForceDownload();
        }
        return false;
    }

    protected void forceDownload(final String url) {
        ResourceRequest resourceRequest = mDownloadTasks.get(url);
        resourceRequest.setForceDownload(true);
        performDownload(url);
    }

    protected void showDialog(final String url) {
        Activity mActivity;
        if (null == (mActivity = mActivityWeakReference.get()) || mActivity.isFinishing()) {
            return;
        }
        PageUiController mWebForgeUIController;
        if (null != (mWebForgeUIController = this.mWebForgeUIController.get())) {
            mWebForgeUIController.onForceDownloadAlert(url, createCallback(url));
        }
    }

    protected Handler.Callback createCallback(final String url) {
        return new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                forceDownload(url);
                return true;
            }
        };
    }

    protected void performDownload(String url) {
        try {
            LogUtils.e(TAG, "performDownload:" + url + " exist:" + DownloadImpl.getInstance(this.mContext).exist(url));
            // 该链接是否正在下载
            if (DownloadImpl.getInstance(mContext).exist(url)) {
                if (null != mWebForgeUIController.get()) {
                    mWebForgeUIController.get().onShowMessage(
                            mActivityWeakReference.get()
                                    .getString(R.string.webforge_download_task_has_been_exist), "preDownload");
                }
                return;
            }
            ResourceRequest resourceRequest = mDownloadTasks.get(url);
            resourceRequest.addHeader("Cookie", WebForgeConfig.getCookiesByUrl(url));
            taskEnqueue(resourceRequest);
        } catch (Throwable ignore) {
            if (LogUtils.isDebug()) {
                ignore.printStackTrace();
            }
        }
    }

    protected void taskEnqueue(ResourceRequest resourceRequest) {
        resourceRequest.enqueue(new DownloadListenerAdapter() {
            @Override
            public boolean onResult(Throwable throwable, Uri path, String url, Extra extra) {
                mDownloadTasks.remove(url);
                return super.onResult(throwable, path, url, extra);
            }
        });
    }

    public static PageDownloadListener create(@NonNull Activity activity,
                                             @NonNull WebView webView,
                                             @Nullable PermissionInterceptor permissionInterceptor) {
        return new PageDownloadListener(activity, webView, permissionInterceptor);
    }
}
