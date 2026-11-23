package io.github.sunwei.webforge.client

import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import io.github.sunwei.webforge.permission.Action
import io.github.sunwei.webforge.permission.PermissionInterceptor
import io.github.sunwei.webforge.permission.WebForgeActionFragment
import io.github.sunwei.webforge.permission.WebForgePermissions
import io.github.sunwei.webforge.ui.FullscreenVideo
import io.github.sunwei.webforge.ui.PageUiController
import io.github.sunwei.webforge.ui.indicator.BaseIndicatorView
import io.github.sunwei.webforge.util.LogUtils
import io.github.sunwei.webforge.util.WebForgeUtils
import java.lang.ref.WeakReference
import java.util.Arrays

/**
 */
open class WebInteractionClient(
    activity: Activity?,
    private val progress: BaseIndicatorView?,
    video: FullscreenVideo,
    permissionInterceptor: PermissionInterceptor?, webView: WebView
) : ChromeCallbacks() {
    /**
     * Activity
     */
    private var mActivityWeakReference: WeakReference<Activity?>? = null

    /**
     * WebInteractionClient 's TAG
     */
    private val TAG: String = WebInteractionClient::class.java.getSimpleName()
    private val video: FullscreenVideo

    /**
     * PermissionInterceptor 权限拦截器
     */
    private val mPermissionInterceptor: PermissionInterceptor?

    /**
     * 当前 WebView
     */
    private val mWebView: WebView

    /**
     * Web端触发的定位 mOrigin
     */
    private var mOrigin: String? = null

    /**
     * Web 端触发的定位 Callback 回调成功，或者失败
     */
    private var mCallback: GeolocationPermissions.Callback? = null

    /**
     * PageUiController
     */
    private var mWebForgeUIController: WeakReference<PageUiController?>? = null

    /**
     * 文件选择器
     */
    private val mPendingRequests: MutableSet<PendingPermissionRequest> = HashSet<PendingPermissionRequest>()
    private var mDestroyed = false
    private var mLastProgress = -1
    private var selection: PendingSelection<*>? = null
    private var pendingPopup: PopupWindow? = null

    init {
        mActivityWeakReference = WeakReference<Activity?>(activity)
        this.video = video
        this.mPermissionInterceptor = permissionInterceptor
        this.mWebView = webView
        mWebForgeUIController = WeakReference<PageUiController?>(WebForgeUtils.getWebForgeUIControllerByWebView(webView))
    }


    public override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        if (progress == null || newProgress == mLastProgress) {
            return
        }
        mLastProgress = newProgress
        if (newProgress == 0) {
            progress.reset()
        } else if (newProgress < 100) {
            progress.show()
        }
        progress.setProgress(newProgress)
        if (newProgress == 100) {
            progress.hide()
        }
    }

    public override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (mWebForgeUIController!!.get() != null) {
            mWebForgeUIController!!.get()!!.onJsAlert(view, url, message)
        }
        result?.confirm()
        return true
    }


    public override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
    }

    public override fun onGeolocationPermissionsHidePrompt() {
        mCallback = null
        mOrigin = null
        super.onGeolocationPermissionsHidePrompt()
    }

    //location
    public override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
        if (callback == null) {
            return
        }
        onGeolocationPermissionsShowPromptInternal(origin, callback)
    }

    private fun onGeolocationPermissionsShowPromptInternal(origin: String?, callback: GeolocationPermissions.Callback) {
        if (mDestroyed) {
            callback.invoke(origin, false, false)
            return
        }
        if (mCallback != null) {
            mCallback!!.invoke(mOrigin, false, false)
            mCallback = null
            mOrigin = null
        }
        if (mPermissionInterceptor != null) {
            if (mPermissionInterceptor.intercept(this.mWebView.getUrl(), WebForgePermissions.LOCATION, "location")) {
                callback.invoke(origin, false, false)
                return
            }
        }
        val mActivity = mActivityWeakReference!!.get()
        if (mActivity == null) {
            callback.invoke(origin, false, false)
            return
        }
        val deniedPermissions = WebForgeUtils.getDeniedPermissions(mActivity, WebForgePermissions.LOCATION)
        if (deniedPermissions.isEmpty()) {
            LogUtils.i(TAG, "onGeolocationPermissionsShowPromptInternal:" + true)
            callback.invoke(origin, true, false)
        } else {
            val mAction = Action.createPermissionsAction(deniedPermissions.toTypedArray())
            mAction.fromIntention = FROM_CODE_INTENTION_LOCATION
            mAction.permissionListener = WebForgeActionFragment.PermissionListener { permissions: Array<String>, grantResults: IntArray, extras: Bundle? ->
                if (mDestroyed || mCallback !== callback) {
                    return@PermissionListener
                }
                mCallback = null
                mOrigin = null
                val activity = mActivityWeakReference!!.get()
                val granted = activity != null && WebForgeUtils.hasPermission(activity, permissions.asList())
                callback.invoke(origin, granted, false)
                val controller = mWebForgeUIController!!.get()
                if (!granted && controller != null) {
                    controller.onPermissionsDeny(
                        WebForgePermissions.LOCATION,
                        WebForgePermissions.ACTION_LOCATION, "Location"
                    )
                }
            }
            this.mCallback = callback
            this.mOrigin = origin
            WebForgeActionFragment.start(mActivity, mAction)
        }
    }

    public override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
        if (result == null) {
            return false
        }
        try {
            if (this.mWebForgeUIController!!.get() != null) {
                this.mWebForgeUIController!!.get()!!.onJsPrompt(mWebView, url, message, defaultValue, result)
            }
        } catch (e: Exception) {
            if (LogUtils.isDebug()) {
                e.printStackTrace()
            }
        }
        return true
    }

    public override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (result == null) {
            return false
        }
        if (mWebForgeUIController!!.get() != null) {
            mWebForgeUIController!!.get()!!.onJsConfirm(view, url, message, result)
        }
        return true
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) {
            return
        }
        if (mDestroyed) {
            request.deny()
            return
        }
        val pending = PendingPermissionRequest(request)
        mPendingRequests.add(pending)
        onPermissionRequestInternal(pending)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun onPermissionRequestInternal(request: PermissionRequest) {
        val resources = request.getResources()
        if (resources == null || resources.size <= 0) {
            request.deny()
            return
        }
        val resourcesSet: MutableSet<String> = HashSet(resources.asList())
        val allowedResources = ArrayList<String>(2)
        val permissions = ArrayList<String>(2)
        //录音、录像（H5 使用 getUserMedia / MediaRecorder 时触发）
        if (resourcesSet.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
            allowedResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            permissions.add(Manifest.permission.CAMERA)
        }
        if (resourcesSet.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            allowedResources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (allowedResources.isEmpty()) {
            request.deny()
            return
        }
        val grantedResources = allowedResources.toTypedArray()
        if (mPermissionInterceptor != null) {
            val intercept = mPermissionInterceptor.intercept(mWebView.getUrl(), permissions.toTypedArray(), "onPermissionRequest")
            if (intercept) {
                request.deny()
                return
            }
        }
        if (mWebForgeUIController!!.get() != null) {
            mWebForgeUIController!!.get()!!.onPermissionRequest(request)
        } else {
            //没有 UI 控制器时兜底，主动申请录音/录像权限
            requestPermission(request, grantedResources, permissions)
        }
    }

    /**
     * 兜底处理 H5 的权限申请，没有 WebForgeUIController 时也能主动申请录音/通话等权限。
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun requestPermission(request: PermissionRequest, resources: Array<String>, permissions: MutableList<String>) {
        val activity = mActivityWeakReference!!.get()
        if (activity == null) {
            request.deny()
            return
        }
        if (permissions.isEmpty()) {
            request.grant(resources)
            return
        }
        val requestPermissions = permissions.toTypedArray()
        val requesting = WebForgeUtils.requestPermissions(
            activity, requestPermissions,
            object : WebForgeActionFragment.PermissionListener {
                override fun onRequestPermissionsResult(permissions: Array<String>, grantResults: IntArray, extras: Bundle?) {
                    if (WebForgeUtils.getDeniedPermissions(activity, permissions).isEmpty()) {
                        request.grant(resources)
                    } else {
                        request.deny()
                    }
                }
            })
        if (requesting) {
            return
        }
        if (WebForgeUtils.getDeniedPermissions(activity, requestPermissions).isEmpty()) {
            request.grant(resources)
        } else {
            request.deny()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public override fun onPermissionRequestCanceled(request: PermissionRequest?) {
        for (pending in ArrayList<PendingPermissionRequest>(mPendingRequests)) {
            if (pending.original === request) {
                pending.cancel()
            }
        }
        super.onPermissionRequestCanceled(request)
    }

    /** 页面销毁时结束待授权请求，系统权限弹窗的晚到结果不再触碰旧页面。  */
    open fun release(rendererAvailable: Boolean) {
        if (mDestroyed) {
            return
        }
        mDestroyed = true
        if (pendingPopup != null) {
            pendingPopup!!.close()
        }
        try {
            if (selection != null) {
                if (rendererAvailable) {
                    selection!!.onReceiveValue(null)
                } else {
                    selection!!.takeResult()
                }
            }
        } finally {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !mPendingRequests.isEmpty()) {
                    for (pending in ArrayList<PendingPermissionRequest>(mPendingRequests)) {
                        if (rendererAvailable) {
                            pending.deny()
                        } else {
                            pending.cancel()
                        }
                    }
                }
            } finally {
                val callback = mCallback
                val origin = mOrigin
                mCallback = null
                mOrigin = null
                if (rendererAvailable && callback != null) {
                    callback.invoke(origin, false, false)
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private inner class PendingPermissionRequest(internal val original: PermissionRequest) : PermissionRequest() {
        private var completed = false

        override fun getOrigin(): Uri? {
            return original.getOrigin()
        }

        override fun getResources(): Array<String>? {
            return original.getResources()
        }

        override fun grant(resources: Array<String>?) {
            if (!completed) {
                cancel()
                original.grant(resources)
            }
        }

        override fun deny() {
            if (!completed) {
                cancel()
                original.deny()
            }
        }

        fun cancel() {
            completed = true
            mPendingRequests.remove(this)
        }
    }

    /** 把新窗口的 URL 交回当前页面，临时 WebView 随页面释放。  */
    public override fun onCreateWindow(
        view: WebView?, isDialog: Boolean, isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        if (mDestroyed || view == null || resultMsg == null || (resultMsg.obj !is WebView.WebViewTransport)) {
            return false
        }
        if (pendingPopup != null) {
            pendingPopup!!.close()
        }
        val popup = PopupWindow(view)
        pendingPopup = popup
        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.setWebView(popup.view)
        resultMsg.sendToTarget()
        popup.handler.postDelayed(popup.timeout, 15000)
        return true
    }

    private inner class PopupWindow(private val target: WebView) {
        internal val handler = Handler(Looper.getMainLooper())
        internal val timeout = Runnable { this.close() }
        internal val view: WebView
        private var transferred = false

        init {
            view = WebView(target.getContext())
            view.setWebViewClient(object : WebViewClient() {
                @RequiresApi(Build.VERSION_CODES.N)
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    if (request.isForMainFrame()) {
                        transfer(request.getUrl().toString())
                    }
                    return true
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    transfer(url)
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    transfer(url)
                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    close()
                    return true
                }
            })
        }

        fun transfer(url: String?) {
            if (transferred || TextUtils.isEmpty(url) || "about:blank" == url) {
                return
            }
            transferred = true
            handler.post(Runnable {
                if (pendingPopup != this) {
                    return@Runnable
                }
                close()
                if (!mDestroyed) {
                    target.loadUrl(url!!)
                }
            })
        }

        fun close() {
            if (pendingPopup != this) {
                return
            }
            pendingPopup = null
            handler.removeCallbacksAndMessages(null)
            view.destroy()
        }
    }

    public override fun onExceededDatabaseQuota(
        url: String?,
        databaseIdentifier: String?,
        quota: Long,
        estimatedDatabaseSize: Long,
        totalQuota: Long,
        quotaUpdater: WebStorage.QuotaUpdater?
    ) {
        quotaUpdater?.updateQuota(totalQuota * 2)
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
        LogUtils.i(TAG, "openFileChooser>=5.0")
        return openFileChooserAboveL(webView, filePathCallback, fileChooserParams)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun openFileChooserAboveL(webView: WebView?, valueCallbacks: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
        if (mDestroyed || valueCallbacks == null) {
            return false
        }
        val mActivity = this.mActivityWeakReference!!.get()
        if (mActivity == null || mActivity.isFinishing()) {
            return false
        }
        val pending = startSelection<Array<Uri>>(valueCallbacks)
        val handled = WebForgeUtils.showFileChooserCompat(
            mActivity,
            mWebView,
            pending,
            fileChooserParams,
            this.mPermissionInterceptor,
            null,
            null,
            null
        )
        if (!handled) {
            pending.takeResult()
        }
        return handled
    }

    /**
     * Android  >= 4.1
     *
     * @param uploadFile ValueCallback ,  File URI callback
     * @param acceptType
     * @param capture
     */
    public override fun openFileChooser(uploadFile: ValueCallback<Uri?>?, acceptType: String?, capture: String?) {
        /*believe me , i never want to do this */
        LogUtils.i(TAG, "openFileChooser>=4.1")
        createAndOpenCommonFileChooser(uploadFile, acceptType)
    }

    //  Android < 3.0
    public override fun openFileChooser(valueCallback: ValueCallback<Uri?>?) {
        Log.i(TAG, "openFileChooser<3.0")
        createAndOpenCommonFileChooser(valueCallback, "*/*")
    }

    //  Android  >= 3.0
    public override fun openFileChooser(valueCallback: ValueCallback<*>?, acceptType: String?) {
        Log.i(TAG, "openFileChooser>3.0")
        createAndOpenCommonFileChooser(valueCallback, acceptType)
    }


    private fun createAndOpenCommonFileChooser(valueCallback: ValueCallback<*>?, mimeType: String?) {
        if (valueCallback == null) {
            return
        }
        val mActivity = this.mActivityWeakReference!!.get()
        if (mDestroyed || mActivity == null || mActivity.isFinishing()) {
            valueCallback.onReceiveValue(null)
            return
        }
        WebForgeUtils.showFileChooserCompat(
            mActivity,
            mWebView,
            null,
            null,
            this.mPermissionInterceptor,
            @Suppress("UNCHECKED_CAST")
            startSelection<Any?>(valueCallback as ValueCallback<Any?>),
            mimeType,
            null
        )
    }

    private fun <T> startSelection(result: ValueCallback<T>?): PendingSelection<T> {
        if (selection != null) {
            selection!!.onReceiveValue(null)
        }
        val pending = PendingSelection(result)
        selection = pending
        return pending
    }

    private inner class PendingSelection<T>(private var result: ValueCallback<T>?) : ValueCallback<T> {
        override fun onReceiveValue(value: T?) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                deliver(value)
            } else {
                WebForgeUtils.runInUiThread(Runnable { deliver(value) })
            }
        }

        fun deliver(value: T?) {
            val callback = takeResult()
            if (callback != null) {
                callback.onReceiveValue(value)
            }
        }

        fun takeResult(): ValueCallback<T>? {
            val callback = result
            result = null
            if (selection == this) {
                selection = null
            }
            return callback
        }
    }

    public override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        super.onConsoleMessage(consoleMessage)
        return true
    }

    public override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (!mDestroyed) {
            video.onShowCustomView(view, callback)
        }
    }

    public override fun onHideCustomView() {
        video.onHideCustomView()
    }

    companion object {
        /**
         * Android WebChromeClient path ，用于反射，用户是否重写来该方法
         */
        const val ANDROID_WEBCHROMECLIENT_PATH: String = "android.webkit.WebChromeClient"

        /**
         * 标志位
         */
        const val FROM_CODE_INTENTION: Int = 0x18

        /**
         * 标识当前是获取定位权限
         */
        @JvmField
        val FROM_CODE_INTENTION_LOCATION: Int = FROM_CODE_INTENTION shl 2
    }
}
