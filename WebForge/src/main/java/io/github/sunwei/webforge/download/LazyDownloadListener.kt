package io.github.sunwei.webforge.download

import android.app.Activity
import android.webkit.DownloadListener
import android.webkit.WebView
import androidx.annotation.RestrictTo
import io.github.sunwei.webforge.permission.PermissionInterceptor
import java.lang.ref.WeakReference

/**
 * Initializes the download SDK only when a page actually starts a download.
 * Local pages usually never use it, so they should not pay its startup cost.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class LazyDownloadListener(activity: Activity?, webView: WebView?, private val permissionInterceptor: PermissionInterceptor?) : DownloadListener {
    private val activityReference: WeakReference<Activity?>
    private val webViewReference: WeakReference<WebView?>

    @Volatile
    private var delegate: PageDownloadListener? = null
        get() {
            var current = field
            if (current != null) {
                return current
            }
            synchronized(this) {
                current = field
                if (current == null) {
                    val activity = activityReference.get()
                    val webView = webViewReference.get()
                    if (activity == null || webView == null) {
                        return null
                    }
                    current = PageDownloadListener.create(activity, webView, permissionInterceptor)
                    field = current
                }
            }
            return current
        }

    init {
        this.activityReference = WeakReference<Activity?>(activity)
        this.webViewReference = WeakReference<WebView?>(webView)
    }

    override fun onDownloadStart(
        url: String?, userAgent: String?, contentDisposition: String?,
        mimetype: String?, contentLength: Long
    ) {
        val current = this.delegate
        if (current != null && url != null) {
            current.onDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength)
        }
    }
}
