package io.github.sunwei.webforge.download;

import android.app.Activity;
import android.webkit.DownloadListener;
import android.webkit.WebView;

import io.github.sunwei.webforge.permission.PermissionInterceptor;

import java.lang.ref.WeakReference;

/**
 * Initializes the download SDK only when a page actually starts a download.
 * Local pages usually never use it, so they should not pay its startup cost.
 */
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY)
public final class LazyDownloadImpl implements DownloadListener {
    private final WeakReference<Activity> activityReference;
    private final WeakReference<WebView> webViewReference;
    private final PermissionInterceptor permissionInterceptor;
    private volatile DefaultDownloadImpl delegate;

    public LazyDownloadImpl(Activity activity, WebView webView, PermissionInterceptor permissionInterceptor) {
        this.activityReference = new WeakReference<>(activity);
        this.webViewReference = new WeakReference<>(webView);
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                String mimetype, long contentLength) {
        DefaultDownloadImpl current = getDelegate();
        if (current != null) {
            current.onDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength);
        }
    }

    private DefaultDownloadImpl getDelegate() {
        DefaultDownloadImpl current = delegate;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = delegate;
            if (current == null) {
                Activity activity = activityReference.get();
                WebView webView = webViewReference.get();
                if (activity == null || webView == null) {
                    return null;
                }
                current = DefaultDownloadImpl.create(activity, webView, permissionInterceptor);
                delegate = current;
            }
        }
        return current;
    }
}
