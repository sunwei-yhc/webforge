package io.github.sunwei.webforge.download

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.DownloadListener
import android.webkit.WebView
import com.download.library.DownloadImpl
import com.download.library.DownloadListenerAdapter
import com.download.library.Extra
import com.download.library.ResourceRequest
import io.github.sunwei.webforge.R
import io.github.sunwei.webforge.config.WebForgeConfig.Companion.getCookiesByUrl
import io.github.sunwei.webforge.permission.PermissionInterceptor
import io.github.sunwei.webforge.ui.PageUiController
import io.github.sunwei.webforge.util.LogUtils
import io.github.sunwei.webforge.util.WebForgeUtils
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 */
open class PageDownloadListener protected constructor(activity: Activity, webView: WebView, permissionInterceptor: PermissionInterceptor?) : DownloadListener {
    /**
     * Application Context
     */
    protected var mContext: Context
    protected var mDownloadTasks: ConcurrentHashMap<String?, ResourceRequest<*>?> = ConcurrentHashMap<String?, ResourceRequest<*>?>()

    /**
     * Activity
     */
    protected var mActivityWeakReference: WeakReference<Activity?>? = null

    /**
     * 权限拦截
     */
    protected var mPermissionListener: PermissionInterceptor? = null

    /**
     * PageUiController
     */
    protected var mWebForgeUIController: WeakReference<PageUiController?>

    private var isInstallDownloader = false

    init {
        this.mContext = activity.getApplicationContext()
        this.mActivityWeakReference = WeakReference<Activity?>(activity)
        this.mPermissionListener = permissionInterceptor
        this.mWebForgeUIController = WeakReference<PageUiController?>(WebForgeUtils.getWebForgeUIControllerByWebView(webView))
        try {
            DownloadImpl.getInstance(this.mContext)
            isInstallDownloader = true
        } catch (throwable: Throwable) {
            LogUtils.e(TAG, "implementation 'com.download.library:Downloader:x.x.x'")
            if (LogUtils.isDebug()) {
                throwable.printStackTrace()
            }
            isInstallDownloader = false
        }
    }


    override fun onDownloadStart(url: String, userAgent: String?, contentDisposition: String?, mimetype: String?, contentLength: Long) {
        if (!isInstallDownloader) {
            LogUtils.e(TAG, "unable start download " + url + "; implementation 'com.download.library:Downloader:x.x.x'")
            return
        }
        mHandler.post(object : Runnable {
            override fun run() {
                onDownloadStartInternal(url, userAgent, contentDisposition, mimetype, contentLength)
            }
        })
    }

    protected fun onDownloadStartInternal(url: String, userAgent: String?, contentDisposition: String?, mimetype: String?, contentLength: Long) {
        if (null == mActivityWeakReference!!.get() || mActivityWeakReference!!.get()!!.isFinishing()) {
            return
        }
        if (null != this.mPermissionListener) {
            if (this.mPermissionListener!!.intercept(url, emptyArray(), "download")) {
                return
            }
        }
        val resourceRequest = createResourceRequest(url)
        this.mDownloadTasks.put(url, resourceRequest)
        preDownload(url)
    }

    protected fun createResourceRequest(url: String): ResourceRequest<*> {
        return DownloadImpl.getInstance(this.mContext).with(url).setEnableIndicator(true).autoOpenIgnoreMD5()
    }


    protected fun preDownload(url: String) {
        // 移动数据
        if (!isForceRequest(url) &&
            WebForgeUtils.checkNetworkType(mContext) > 1
        ) {
            showDialog(url)
            return
        }
        performDownload(url)
    }

    protected fun isForceRequest(url: String): Boolean {
        val resourceRequest = mDownloadTasks.get(url)
        if (null != resourceRequest) {
            return resourceRequest.getDownloadTask().isForceDownload()
        }
        return false
    }

    protected fun forceDownload(url: String) {
        val resourceRequest = mDownloadTasks.get(url)
        resourceRequest!!.setForceDownload(true)
        performDownload(url)
    }

    protected fun showDialog(url: String) {
        val mActivity: Activity?
        if (null == (mActivityWeakReference!!.get().also { mActivity = it }) || mActivity!!.isFinishing()) {
            return
        }
        val mWebForgeUIController: PageUiController?
        if (null != (this.mWebForgeUIController.get().also { mWebForgeUIController = it })) {
            mWebForgeUIController!!.onForceDownloadAlert(url, createCallback(url))
        }
    }

    protected fun createCallback(url: String): Handler.Callback {
        return object : Handler.Callback {
            override fun handleMessage(msg: Message): Boolean {
                forceDownload(url)
                return true
            }
        }
    }

    protected fun performDownload(url: String) {
        try {
            LogUtils.e(TAG, "performDownload:" + url + " exist:" + DownloadImpl.getInstance(this.mContext).exist(url))
            // 该链接是否正在下载
            if (DownloadImpl.getInstance(mContext).exist(url)) {
                if (null != mWebForgeUIController.get()) {
                    mWebForgeUIController.get()!!.onShowMessage(
                        mActivityWeakReference!!.get()!!
                            .getString(R.string.webforge_download_task_has_been_exist), "preDownload"
                    )
                }
                return
            }
            val resourceRequest = mDownloadTasks.get(url)
            resourceRequest!!.addHeader("Cookie", getCookiesByUrl(url))
            taskEnqueue(resourceRequest)
        } catch (ignore: Throwable) {
            if (LogUtils.isDebug()) {
                ignore.printStackTrace()
            }
        }
    }

    protected fun taskEnqueue(resourceRequest: ResourceRequest<*>) {
        resourceRequest.enqueue(object : DownloadListenerAdapter() {
            override fun onResult(throwable: Throwable?, path: Uri?, url: String, extra: Extra?): Boolean {
                mDownloadTasks.remove(url)
                return super.onResult(throwable, path, url, extra)
            }
        })
    }

    companion object {
        /**
         * TAG 用于打印，标识
         */
        private val TAG: String = PageDownloadListener::class.java.getSimpleName()
        private val mHandler = Handler(Looper.getMainLooper())

        @JvmStatic
        fun create(
            activity: Activity,
            webView: WebView,
            permissionInterceptor: PermissionInterceptor?
        ): PageDownloadListener {
            return PageDownloadListener(activity, webView, permissionInterceptor)
        }
    }
}
