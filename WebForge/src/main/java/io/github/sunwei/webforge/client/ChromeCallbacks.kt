package io.github.sunwei.webforge.client

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import io.github.sunwei.webforge.util.LogUtils

/** 自定义标题、进度和网页交互回调。调用 super 可继续执行默认行为。  */
open class ChromeCallbacks : WebChromeClient() {
    protected var delegate: WebChromeClient? = null
        private set

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun connect(delegate: WebChromeClient) {
        check(this.delegate == null) { "ChromeCallbacks already belongs to a page" }
        this.delegate = delegate
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun disconnect() {
        this.delegate = null
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        if (this.delegate != null) {
            this.delegate!!.onProgressChanged(view, newProgress)
            return
        }
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        if (this.delegate != null) {
            this.delegate!!.onReceivedTitle(view, title)
            return
        }
        super.onReceivedTitle(view, title)
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        if (this.delegate != null) {
            this.delegate!!.onReceivedIcon(view, icon)
            return
        }
        super.onReceivedIcon(view, icon)
    }

    override fun onReceivedTouchIconUrl(
        view: WebView?, url: String?,
        precomposed: Boolean
    ) {
        if (this.delegate != null) {
            this.delegate!!.onReceivedTouchIconUrl(view, url, precomposed)
            return
        }
        super.onReceivedTouchIconUrl(view, url, precomposed)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (this.delegate != null) {
            this.delegate!!.onShowCustomView(view, callback)
            return
        }
        super.onShowCustomView(view, callback)
    }


    override fun onShowCustomView(
        view: View?, requestedOrientation: Int,
        callback: CustomViewCallback?
    ) {
        if (this.delegate != null) {
            this.delegate!!.onShowCustomView(view, requestedOrientation, callback)
            return
        }
        super.onShowCustomView(view, requestedOrientation, callback)
    }


    override fun onHideCustomView() {
        if (this.delegate != null) {
            this.delegate!!.onHideCustomView()
            return
        }
        super.onHideCustomView()
    }

    override fun onCreateWindow(
        view: WebView?, isDialog: Boolean,
        isUserGesture: Boolean, resultMsg: Message?
    ): Boolean {
        if (this.delegate != null) {
            return this.delegate!!.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
        }
        return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
    }

    override fun onRequestFocus(view: WebView?) {
        if (this.delegate != null) {
            this.delegate!!.onRequestFocus(view)
            return
        }
        super.onRequestFocus(view)
    }

    override fun onCloseWindow(window: WebView?) {
        if (this.delegate != null) {
            this.delegate!!.onCloseWindow(window)
            return
        }
        super.onCloseWindow(window)
    }

    override fun onJsAlert(
        view: WebView?, url: String?, message: String?,
        result: JsResult?
    ): Boolean {
        if (this.delegate != null) {
            return this.delegate!!.onJsAlert(view, url, message, result)
        }
        return super.onJsAlert(view, url, message, result)
    }

    override fun onJsConfirm(
        view: WebView?, url: String?, message: String?,
        result: JsResult?
    ): Boolean {
        if (this.delegate != null) {
            return this.delegate!!.onJsConfirm(view, url, message, result)
        }
        return super.onJsConfirm(view, url, message, result)
    }

    override fun onJsPrompt(
        view: WebView?, url: String?, message: String?,
        defaultValue: String?, result: JsPromptResult?
    ): Boolean {
        if (this.delegate != null) {
            return this.delegate!!.onJsPrompt(view, url, message, defaultValue, result)
        }
        return super.onJsPrompt(view, url, message, defaultValue, result)
    }

    override fun onJsBeforeUnload(
        view: WebView?, url: String?, message: String?,
        result: JsResult?
    ): Boolean {
        if (this.delegate != null) {
            return this.delegate!!.onJsBeforeUnload(view, url, message, result)
        }
        return super.onJsBeforeUnload(view, url, message, result)
    }

    @Deprecated("")
    override fun onExceededDatabaseQuota(
        url: String?, databaseIdentifier: String?,
        quota: Long, estimatedDatabaseSize: Long, totalQuota: Long,
        quotaUpdater: WebStorage.QuotaUpdater?
    ) {
        // This default implementation passes the current quota back to WebCore.
        // WebCore will interpret this that new quota was declined.
        if (this.delegate != null) {
            this.delegate!!.onExceededDatabaseQuota(url, databaseIdentifier, quota, estimatedDatabaseSize, totalQuota, quotaUpdater)
            return
        }
        super.onExceededDatabaseQuota(url, databaseIdentifier, quota, estimatedDatabaseSize, totalQuota, quotaUpdater)
    }


    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (this.delegate != null) {
            this.delegate!!.onGeolocationPermissionsShowPrompt(origin, callback)
            return
        }
        super.onGeolocationPermissionsShowPrompt(origin, callback)
    }

    /**
     * notify the host application that a request for Geolocation permissions,
     * made with a previous call to
     * [onGeolocationPermissionsShowPrompt()][.onGeolocationPermissionsShowPrompt]
     * has been canceled. Any related UI should therefore be hidden.
     */
    override fun onGeolocationPermissionsHidePrompt() {
        if (this.delegate != null) {
            this.delegate!!.onGeolocationPermissionsHidePrompt()
            return
        }

        super.onGeolocationPermissionsHidePrompt()
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onPermissionRequest(request: PermissionRequest?) {
        if (this.delegate != null) {
            this.delegate!!.onPermissionRequest(request)
            return
        }
        super.onPermissionRequest(request)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
        if (this.delegate != null) {
            this.delegate!!.onPermissionRequestCanceled(request)
            return
        }
        super.onPermissionRequestCanceled(request)
    }

    override fun onJsTimeout(): Boolean {
        if (this.delegate != null) {
            return this.delegate!!.onJsTimeout()
        }
        return super.onJsTimeout()
    }

    @Deprecated("")
    override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
        if (this.delegate != null) {
            this.delegate!!.onConsoleMessage(message, lineNumber, sourceID)
            return
        }
        super.onConsoleMessage(message, lineNumber, sourceID)
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        /*onConsoleMessage(consoleMessage.message(), consoleMessage.lineNumber(),
                consoleMessage.sourceId());*/

        if (this.delegate != null) {
            return this.delegate!!.onConsoleMessage(consoleMessage)
        }
        return super.onConsoleMessage(consoleMessage)
    }

    override fun getDefaultVideoPoster(): Bitmap? {
        if (this.delegate != null) {
            return this.delegate!!.getDefaultVideoPoster()
        }
        return super.getDefaultVideoPoster()
    }

    override fun getVideoLoadingProgressView(): View? {
        if (this.delegate != null) {
            return this.delegate!!.getVideoLoadingProgressView()
        }
        return super.getVideoLoadingProgressView()
    }

    override fun getVisitedHistory(callback: ValueCallback<Array<String?>?>?) {
        if (this.delegate != null) {
            this.delegate!!.getVisitedHistory(callback)
            return
        }
        super.getVisitedHistory(callback)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onShowFileChooser(
        webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        if (this.delegate != null) {
            return this.delegate!!.onShowFileChooser(webView, filePathCallback, fileChooserParams)
        }
        return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
    }


    /**
     * Android  >= 4.1
     *
     * @param uploadFile
     * @param acceptType
     * @param capture
     */
    open fun openFileChooser(uploadFile: ValueCallback<Uri?>?, acceptType: String?, capture: String?) {
        invokeLegacyFileChooser(arrayOf<Any?>(uploadFile, acceptType, capture), ValueCallback::class.java, String::class.java, String::class.java)
    }

    /**
     * Android < 3.0
     *
     * @param valueCallback
     */
    open fun openFileChooser(valueCallback: ValueCallback<Uri?>?) {
        invokeLegacyFileChooser(arrayOf<Any?>(valueCallback), ValueCallback::class.java)
    }

    /**
     * Android  >= 3.0
     *
     * @param valueCallback
     * @param acceptType
     */
    open fun openFileChooser(valueCallback: ValueCallback<*>?, acceptType: String?) {
        invokeLegacyFileChooser(arrayOf<Any?>(valueCallback, acceptType), ValueCallback::class.java, String::class.java)
    }


    private fun invokeLegacyFileChooser(arguments: Array<Any?>, vararg parameterTypes: Class<*>?) {
        val current = this.delegate
        if (current == null) {
            return
        }
        try {
            val method = current.javaClass.getMethod("openFileChooser", *parameterTypes)
            method.invoke(current, *arguments)
        } catch (failure: Exception) {
            if (LogUtils.isDebug()) {
                failure.printStackTrace()
            }
        }
    }
}
