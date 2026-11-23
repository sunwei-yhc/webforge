package io.github.sunwei.webforge.client

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.KeyEvent
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo

/** 自定义导航回调。调用 super 可继续执行下一层和 WebForge 默认行为。  */
open class NavigationCallbacks : WebViewClient() {
    @Volatile
    protected var delegate: WebViewClient? = null
        private set

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun connect(delegate: WebViewClient) {
        check(this.delegate == null) { "NavigationCallbacks already belongs to a page" }
        this.delegate = delegate
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun disconnect() {
        this.delegate = null
    }

    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (this.delegate != null) {
            return delegate!!.shouldOverrideUrlLoading(view, url)
        }
        return super.shouldOverrideUrlLoading(view, url)
    }

    @RequiresApi(24)
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (this.delegate != null) {
            return delegate!!.shouldOverrideUrlLoading(view, request)
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (this.delegate != null) {
            delegate!!.onPageStarted(view, url, favicon)
            return
        }
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (this.delegate != null) {
            delegate!!.onPageFinished(view, url)
            return
        }
        super.onPageFinished(view, url)
    }

    override fun onLoadResource(view: WebView?, url: String?) {
        if (this.delegate != null) {
            delegate!!.onLoadResource(view, url)
            return
        }
        super.onLoadResource(view, url)
    }

    @RequiresApi(23)
    override fun onPageCommitVisible(view: WebView?, url: String?) {
        if (this.delegate != null) {
            delegate!!.onPageCommitVisible(view, url)
            return
        }
        super.onPageCommitVisible(view, url)
    }

    @Deprecated("")
    override fun shouldInterceptRequest(
        view: WebView?,
        url: String?
    ): WebResourceResponse? {
        val delegate = this.delegate
        if (delegate != null) {
            return delegate.shouldInterceptRequest(view, url)
        }
        return super.shouldInterceptRequest(view, url)
    }

    @RequiresApi(21)
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val delegate = this.delegate
        if (delegate != null) {
            return delegate.shouldInterceptRequest(view, request)
        }
        return super.shouldInterceptRequest(view, request)
    }

    @Deprecated("")
    override fun onTooManyRedirects(
        view: WebView?, cancelMsg: Message?,
        continueMsg: Message?
    ) {
        if (this.delegate != null) {
            delegate!!.onTooManyRedirects(view, cancelMsg, continueMsg)
            return
        }
        super.onTooManyRedirects(view, cancelMsg, continueMsg)
    }

    @Deprecated("")
    override fun onReceivedError(
        view: WebView?, errorCode: Int,
        description: String?, failingUrl: String?
    ) {
        if (this.delegate != null) {
            delegate!!.onReceivedError(view, errorCode, description, failingUrl)
            return
        }
        super.onReceivedError(view, errorCode, description, failingUrl)
    }

    @RequiresApi(23)
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (this.delegate != null) {
            delegate!!.onReceivedError(view, request, error)
            return
        }
        super.onReceivedError(view, request, error)
    }

    @RequiresApi(23)
    override fun onReceivedHttpError(
        view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?
    ) {
        if (this.delegate != null) {
            delegate!!.onReceivedHttpError(view, request, errorResponse)
            return
        }
        super.onReceivedHttpError(view, request, errorResponse)
    }

    override fun onFormResubmission(
        view: WebView?, dontResend: Message?,
        resend: Message?
    ) {
        if (this.delegate != null) {
            delegate!!.onFormResubmission(view, dontResend, resend)
            return
        }
        super.onFormResubmission(view, dontResend, resend)
    }


    override fun doUpdateVisitedHistory(
        view: WebView?, url: String?,
        isReload: Boolean
    ) {
        if (this.delegate != null) {
            delegate!!.doUpdateVisitedHistory(view, url, isReload)
            return
        }
        super.doUpdateVisitedHistory(view, url, isReload)
    }

    override fun onReceivedSslError(
        view: WebView?, handler: SslErrorHandler?,
        error: SslError?
    ) {
        if (this.delegate != null) {
            delegate!!.onReceivedSslError(view, handler, error)
            return
        }
        super.onReceivedSslError(view, handler, error)
    }

    @RequiresApi(21)
    override fun onReceivedClientCertRequest(view: WebView?, request: ClientCertRequest?) {
        if (this.delegate != null) {
            delegate!!.onReceivedClientCertRequest(view, request)
            return
        }
        super.onReceivedClientCertRequest(view, request)
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler?, host: String?, realm: String?
    ) {
        if (this.delegate != null) {
            delegate!!.onReceivedHttpAuthRequest(view, handler, host, realm)
            return
        }
        super.onReceivedHttpAuthRequest(view, handler, host, realm)
    }

    override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean {
        if (this.delegate != null) {
            return delegate!!.shouldOverrideKeyEvent(view, event)
        }
        return super.shouldOverrideKeyEvent(view, event)
    }

    override fun onUnhandledKeyEvent(view: WebView?, event: KeyEvent?) {
        if (this.delegate != null) {
            delegate!!.onUnhandledKeyEvent(view, event)
            return
        }
        super.onUnhandledKeyEvent(view, event)
    }


    override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
        if (this.delegate != null) {
            delegate!!.onScaleChanged(view, oldScale, newScale)
            return
        }
        super.onScaleChanged(view, oldScale, newScale)
    }

    override fun onReceivedLoginRequest(
        view: WebView?, realm: String?,
        account: String?, args: String?
    ) {
        if (this.delegate != null) {
            delegate!!.onReceivedLoginRequest(view, realm, account, args)
            return
        }
        super.onReceivedLoginRequest(view, realm, account, args)
    }
}
