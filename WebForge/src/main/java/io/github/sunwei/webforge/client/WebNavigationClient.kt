package io.github.sunwei.webforge.client

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.TextUtils
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import io.github.sunwei.webforge.config.ExternalLinks
import io.github.sunwei.webforge.config.WebForgeConfig
import io.github.sunwei.webforge.permission.WebForgeActionFragment
import io.github.sunwei.webforge.permission.WebForgePermissions
import io.github.sunwei.webforge.ui.PageUiController
import io.github.sunwei.webforge.util.LogUtils
import io.github.sunwei.webforge.util.WebForgeUtils
import java.lang.ref.WeakReference
import java.net.URISyntaxException

/** 处理系统链接、主框架错误和渲染器退出。  */
class WebNavigationClient(
    activity: Activity?, view: WebView, private val externalLinks: ExternalLinks?,
    private val blockUnknownSchemes: Boolean, private val rendererGone: Runnable
) : WebViewClient() {
    private val mWeakReference: WeakReference<Activity?>
    private val ui: PageUiController?
    private var activeUrl: String? = null
    private var failed = false

    init {
        mWeakReference = WeakReference<Activity?>(activity)
        ui = WebForgeUtils.getWebForgeUIControllerByWebView(view)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        rendererGone.run()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return request.isForMainFrame() && handleUrl(view, request.getUrl().toString())
    }

    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return handleUrl(view, url)
    }

    private fun handleUrl(view: WebView, url: String): Boolean {
        val scheme = Uri.parse(url).getScheme()
        if (scheme == null || "https".equals(scheme, ignoreCase = true) || "http".equals(scheme, ignoreCase = true)
            || "file".equals(scheme, ignoreCase = true) || "content".equals(scheme, ignoreCase = true)
            || "about".equals(scheme, ignoreCase = true) || "data".equals(scheme, ignoreCase = true)
            || "blob".equals(scheme, ignoreCase = true) || "javascript".equals(scheme, ignoreCase = true)
        ) {
            return false
        }
        if (externalLinks == ExternalLinks.BLOCK) {
            return true
        }
        if (handleCommonLink(url)) {
            return true
        }
        if (url.startsWith("intent://")) {
            lookup(url)
            return true
        }
        if (url.startsWith("weixin://wap/pay?")) {
            return openUri(url, Intent.ACTION_VIEW) || blockUnknownSchemes
        }
        if (url.startsWith("alipays://") && lookup(url)) {
            return true
        }
        if (url.startsWith("market://")) {
            if (externalLinks == ExternalLinks.OPEN) {
                return lookup(url) || blockUnknownSchemes
            }
            val info = lookupResolveInfo(url)
            if (info != null && ui != null) {
                val activity = mWeakReference.get()
                val target = info.activityInfo
                if (activity != null && target != null && activity.getPackageName() == target.packageName) {
                    return lookup(url)
                }
                ui.onOpenPagePrompt(view, view.getUrl(), getOpenPageCallback(url))
                return true
            }
        }
        return blockUnknownSchemes
    }

    private fun lookupResolveInfo(url: String?): ResolveInfo? {
        try {
            val intent: Intent?
            var mActivity: Activity? = null
            if ((mWeakReference.get().also { mActivity = it }) == null) {
                return null
            }
            val packageManager = mActivity!!.getPackageManager()
            intent = externalIntent(url)
            val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return info
        } catch (ignore: RuntimeException) {
            if (LogUtils.isDebug()) {
                ignore.printStackTrace()
            }
        } catch (ignore: URISyntaxException) {
            if (LogUtils.isDebug()) {
                ignore.printStackTrace()
            }
        }
        return null
    }

    private fun lookup(url: String?): Boolean {
        try {
            val intent: Intent?
            val mActivity = mWeakReference.get()
            if (mActivity == null) {
                return true
            }
            val packageManager = mActivity.getPackageManager()
            intent = externalIntent(url)
            val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            // 跳到该应用
            if (info != null) {
                mActivity.startActivity(intent)
                return true
            }
        } catch (ignore: RuntimeException) {
            if (LogUtils.isDebug()) {
                ignore.printStackTrace()
            }
        } catch (ignore: URISyntaxException) {
            if (LogUtils.isDebug()) {
                ignore.printStackTrace()
            }
        }
        return false
    }

    private fun handleCommonLink(url: String): Boolean {
        //拨打电话 ， 需要通话权限 ， 未授权时主动申请
        if (url.startsWith(WebView.SCHEME_TEL)) {
            return handleTelLink(url)
        }
        //邮件 ， 短信 ， 地图
        if (url.startsWith("sms:")
            || url.startsWith(WebView.SCHEME_MAILTO)
            || url.startsWith(WebView.SCHEME_GEO)
        ) {
            openUri(url, Intent.ACTION_VIEW)
            return true
        }
        return false
    }

    /**
     * H5 使用 tel: 链接拨打电话时，先检查通话权限，没有授权则主动申请。
     */
    private fun handleTelLink(url: String?): Boolean {
        val activity = mWeakReference.get()
        if (activity == null) {
            return false
        }
        if (!WebForgeUtils.hasPermission(activity, WebForgePermissions.CALL.asList())) {
            val requesting = WebForgeUtils.requestPermissions(
                activity, WebForgePermissions.CALL,
                object : WebForgeActionFragment.PermissionListener {
                    override fun onRequestPermissionsResult(permissions: Array<String>, grantResults: IntArray, extras: Bundle?) {
                        call(url)
                    }
                })
            if (!requesting) {
                //无法申请权限（例如不是 FragmentActivity），退化为打开系统拨号盘
                openUri(url, Intent.ACTION_DIAL)
            }
            return true
        }
        call(url)
        return true
    }

    /**
     * 拨号，授权成功后默认直接拨出，可通过 [.setCallNeedConfirm] 改为只打开系统拨号盘。
     */
    private fun call(url: String?) {
        val activity = mWeakReference.get()
        if (activity == null) {
            return
        }
        if (!sCallNeedConfirm && WebForgeUtils.hasPermission(activity, WebForgePermissions.CALL.asList())
            && openUri(url, Intent.ACTION_CALL)
        ) {
            return
        }
        openUri(url, Intent.ACTION_DIAL)
    }

    private fun openUri(url: String?, action: String?): Boolean {
        try {
            val mActivity = mWeakReference.get()
            if (mActivity == null) {
                return false
            }
            val intent = Intent(action)
            intent.setData(Uri.parse(url))
            mActivity!!.startActivity(intent)
            return true
        } catch (ignored: RuntimeException) {
            if (WebForgeConfig.DEBUG) {
                ignored.printStackTrace()
            }
            return false
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        activeUrl = url
        failed = false
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) {
        if (WebForgeConfig.isAcceptSslCertificateErrors) {
            handler.proceed()
        } else {
            handler.cancel()
        }
    }

    override fun onReceivedError(view: WebView, code: Int, description: String?, url: String?) {
        showError(view, code, description, url)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame()) {
            showError(
                view, error.getErrorCode(), error.getDescription().toString(),
                request.getUrl().toString()
            )
        }
    }

    private fun showError(view: WebView, code: Int, description: String?, url: String?) {
        if (activeUrl != null && !TextUtils.equals(activeUrl, url) && !TextUtils.equals(view.getUrl(), url)) {
            return
        }
        failed = true
        if (ui != null) {
            ui.onMainFrameError(view, code, description, url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (!failed && TextUtils.equals(activeUrl, url) && ui != null) {
            ui.onShowMainFrame()
        }
    }


    private fun getOpenPageCallback(url: String?): Handler.Callback {
        return Handler.Callback { message: Message? ->
            if (message!!.what == 1) {
                lookup(url)
            }
            true
        }
    }

    companion object {
        private const val TAG = "WebForgeNavigation"

        @Volatile
        private var sCallNeedConfirm = false

        @Throws(URISyntaxException::class)
        private fun externalIntent(url: String?): Intent {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            intent.setComponent(null)
            intent.setSelector(null)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            return intent
        }

        /**
         * 设置 H5 使用 tel: 拨打电话时是否需要用户二次确认。
         *
         * @param callNeedConfirm true 授权后只打开系统拨号盘（用户手动点击拨打）；false（默认）授权后直接拨出
         */
        @JvmStatic
        fun setCallNeedConfirm(callNeedConfirm: Boolean) {
            sCallNeedConfirm = callNeedConfirm
        }
    }
}
