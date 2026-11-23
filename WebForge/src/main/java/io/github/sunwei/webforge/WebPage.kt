package io.github.sunwei.webforge

import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.github.sunwei.webforge.PageHeaders.copy
import io.github.sunwei.webforge.PageHeaders.origin
import io.github.sunwei.webforge.PageJavaScript.register
import io.github.sunwei.webforge.PageLayout.create
import io.github.sunwei.webforge.WebViewSettings.apply
import io.github.sunwei.webforge.bridge.AndroidBridge
import io.github.sunwei.webforge.bridge.FileUploadBridge
import io.github.sunwei.webforge.client.ChromeCallbacks
import io.github.sunwei.webforge.client.NavigationCallbacks
import io.github.sunwei.webforge.client.WebInteractionClient
import io.github.sunwei.webforge.client.WebNavigationClient
import io.github.sunwei.webforge.config.ExternalLinks
import io.github.sunwei.webforge.config.WebForgeConfig
import io.github.sunwei.webforge.config.WebOptions
import io.github.sunwei.webforge.download.LazyDownloadListener
import io.github.sunwei.webforge.permission.PermissionInterceptor
import io.github.sunwei.webforge.security.LegacyWebViewSecurity.removeBuiltInJsInterfaces
import io.github.sunwei.webforge.ui.FullscreenVideo
import io.github.sunwei.webforge.ui.PageUiController
import io.github.sunwei.webforge.ui.WebParentLayout
import io.github.sunwei.webforge.ui.indicator.BaseIndicatorView
import io.github.sunwei.webforge.widget.CompatibleWebView
import io.github.sunwei.webforge.widget.LegacyBridgeWebView

/** 宿主管理的单个 WebView 页面。Fragment 页面随当前视图销毁。  */
class WebPage private constructor(builder: Builder) {
    private val queue = PageTaskQueue()
    private var session: Session?

    init {
        var view = builder.view
        if (view == null) {
            view = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT)
                LegacyBridgeWebView(builder.activity)
            else
                CompatibleWebView(builder.activity)
        }
        session = Session(view)
        try {
            initialize(builder)
        } catch (failure: RuntimeException) {
            closeAfterFailure(failure)
            throw failure
        } catch (failure: Error) {
            closeAfterFailure(failure)
            throw failure
        }
    }

    private fun initialize(builder: Builder) {
        val current = session
        val view = current!!.view
        current.root = create(builder.activity)
        PageLayout.setContent(current.root!!, builder.content, view)
        current.root!!.setErrorLayoutRes(builder.errorLayout, builder.retryView)
        current.root!!.setErrorView(builder.errorView)
        current.root!!.bindController(
            if (builder.uiController == null)
                PageUiController()
            else
                builder.uiController
        )
        val progress = if (builder.progressEnabled) PageLayout.addProgress(
            current.root!!, builder.progressView, builder.progressColor, builder.progressHeight
        ) else null

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            WebForgeConfig.initCookiesManager(builder.activity.getApplicationContext())
        }
        removeBuiltInJsInterfaces(view)
        apply(view, builder.options)
        if (builder.settings != null) {
            builder.settings!!.apply(view.getSettings())
        }

        current.video = FullscreenVideo(builder.activity, view, builder.landscapeVideo)
        current.chrome = WebInteractionClient(
            builder.activity, progress,
            current.video!!, builder.permissionInterceptor, view
        )
        current.headers = if (builder.headers.isEmpty()) mutableMapOf<String?, MutableMap<String?, String?>?>() else
            HashMap<String?, MutableMap<String?, String?>?>(builder.headers)
        view.setDownloadListener(
            LazyDownloadListener(
                builder.activity, view,
                builder.permissionInterceptor
            )
        )
        installClients(current, builder)
        if (builder.builtInBridges) {
            if (!builder.bridges.containsKey("Android")) {
                register(view, "Android", AndroidBridge(this, builder.activity))
            }
            if (!builder.bridges.containsKey("webForge")) {
                register(
                    view, "webForge", FileUploadBridge(
                        this, builder.activity, builder.permissionInterceptor
                    )
                )
            }
        }
        for (bridge in builder.bridges.entries) {
            PageJavaScript.register(view, bridge.key!!, bridge.value!!)
        }

        builder.container.addView(current.root, builder.index, builder.layoutParams)
        current.host = PageHost(this, builder.activity, builder.owner, builder.handleBack)
        current.host!!.bind()
    }

    private fun installClients(current: Session, builder: Builder) {
        val listener = builder.rendererGone
        val rendererGone = Runnable {
            if (session != current) {
                return@Runnable
            }
            current.rendererGone = true
            val wasOpen = !this.isClosed
            close()
            releaseSession()
            if (wasOpen && listener != null) {
                listener.run()
            }
        }
        var navigation: WebViewClient = WebNavigationClient(
            builder.activity,
            current.view, builder.externalLinks, builder.blockUnknownSchemes, rendererGone
        )
        if (!builder.navigationCallbacks.isEmpty()) {
            current.navigationCallbacks = ArrayList<NavigationCallbacks>(builder.navigationCallbacks.size)
            for (i in builder.navigationCallbacks.indices.reversed()) {
                val next = builder.navigationCallbacks.get(i)
                next.connect(navigation)
                current.navigationCallbacks.add(next)
                navigation = next
            }
        }
        if (!current.navigationCallbacks.isEmpty()) {
            val renderGuard: NavigationCallbacks = object : NavigationCallbacks() {
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    rendererGone.run()
                    return true
                }
            }
            renderGuard.connect(navigation)
            navigation = renderGuard
        }
        var chrome: WebChromeClient? = current.chrome
        if (!builder.chromeCallbacks.isEmpty()) {
            current.chromeCallbacks = ArrayList<ChromeCallbacks>(builder.chromeCallbacks.size)
            for (i in builder.chromeCallbacks.indices.reversed()) {
                val next = builder.chromeCallbacks.get(i)
                next.connect(chrome!!)
                current.chromeCallbacks.add(next)
                chrome = next
            }
        }
        current.view.setWebViewClient(navigation)
        current.view.setWebChromeClient(chrome)
    }

    /** 加载 URL。可从后台线程调用。  */
    fun load(url: String) {
        val target: String = requireUrl(url)
        run(WebPage.PageAction { current: Session? ->
            val headers = if (current!!.headers!!.isEmpty())
                null
            else
                current.headers!!.get(origin(target))
            current.load(target, headers)
        })
    }

    /** 使用本次导航的请求头。  */
    fun load(url: String, headers: MutableMap<String?, String?>) {
        val target: String = requireUrl(url)
        val snapshot = copy(headers)
        run(WebPage.PageAction { current: Session? -> current!!.load(target, snapshot) })
    }

    fun loadHtml(html: String, baseUrl: String?) {
        run(WebPage.PageAction { current: Session? -> current!!.view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null) })
    }

    fun post(url: String, body: ByteArray) {
        val target: String = requireUrl(url)
        val snapshot = body.clone()
        run(WebPage.PageAction { current: Session? -> current!!.view.postUrl(target, snapshot) })
    }

    fun reload() {
        run(WebPage.PageAction { current: Session? -> current!!.view.reload() })
    }

    fun stop() {
        run(WebPage.PageAction { current: Session? -> current!!.view.stopLoading() })
    }

    /** API 19 起可获得 JSON 格式的结果。回调在主线程运行。  */
    @JvmOverloads
    fun evaluateJavaScript(script: String, result: ValueCallback<String?>? = null) {
        run(WebPage.PageAction { current: Session? ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                current!!.view.evaluateJavascript(script, if (result == null) null else ValueCallback { value: String? ->
                    if (!this.isClosed) {
                        result.onReceiveValue(value)
                    }
                })
            } else {
                current!!.view.loadUrl(if (script.startsWith("javascript:")) script else "javascript:" + script)
            }
        })
    }

    fun callJavaScript(function: String, vararg arguments: Any?) {
        if (!this.isClosed) {
            evaluateJavaScript(PageJavaScript.call(function, arguments))
        }
    }

    /** 立即注册桥接对象。请在加载可信网页前调用。  */
    @MainThread
    fun addJavaScriptBridge(name: String, bridge: Any) {
        requireMainThread()
        run(WebPage.PageAction { current: Session? -> register(current!!.view, name, bridge) })
    }

    /** 先退出全屏视频，再尝试导航到上一页。  */
    @MainThread
    fun goBack(): Boolean {
        requireMainThread()
        if (this.isClosed) {
            return false
        }
        val current = session
        if (current!!.video!!.exitFullscreen()) {
            return true
        }
        if (!current.view.canGoBack()) {
            return false
        }
        current.view.goBack()
        return true
    }

    @get:MainThread
    val view: WebView?
        /** 访问底层 WebView。页面关闭后返回 null。  */
        get() {
            requireMainThread()
            return if (this.isClosed) null else session!!.view
        }

    /** 清理应用 WebView 的缓存和 Cookie。  */
    fun clearBrowserData() {
        run(WebPage.PageAction { current: Session? ->
            WebForgeConfig.removeAllCookies()
            current!!.view.clearCache(true)
            current.view.clearHistory()
            current.view.clearFormData()
        })
    }

    val isClosed: Boolean
        get() = queue.isClosed

    /** 可从任意线程调用。重复关闭安全。  */
    fun close() {
        queue.close(Runnable { this.releaseSession() })
    }

    private fun releaseSession() {
        val current = session
        session = null
        if (current != null) {
            current.release()
        }
    }

    fun pause() {
        run(WebPage.PageAction { current: Session? -> current!!.view.onPause() })
    }

    fun resume() {
        run(WebPage.PageAction { current: Session? -> current!!.view.onResume() })
    }

    private fun interface PageAction {
        fun run(current: Session?)
    }

    private fun run(action: PageAction) {
        queue.run(Runnable { action.run(session) })
    }

    private fun closeAfterFailure(failure: Throwable) {
        try {
            close()
        } catch (cleanupFailure: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                failure.addSuppressed(cleanupFailure)
            }
        } catch (cleanupFailure: Error) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                failure.addSuppressed(cleanupFailure)
            }
        }
    }

    private class Session(val view: WebView) {
        var root: WebParentLayout? = null
        var video: FullscreenVideo? = null
        var chrome: WebInteractionClient? = null
        var host: PageHost? = null
        var headers: MutableMap<String?, MutableMap<String?, String?>?>? = null
        var navigationCallbacks: MutableList<NavigationCallbacks> = mutableListOf()
        var chromeCallbacks: MutableList<ChromeCallbacks> = mutableListOf()
        var rendererGone: Boolean = false

        fun load(url: String, headers: MutableMap<String?, String?>?) {
            if (headers == null || headers.isEmpty()) {
                view.loadUrl(url)
            } else {
                view.loadUrl(url, headers)
            }
        }

        fun release() {
            if (host != null) {
                host!!.unbind()
            }
            try {
                if (chrome != null) {
                    chrome!!.release(!rendererGone)
                }
            } finally {
                try {
                    if (video != null) {
                        video!!.release(!rendererGone)
                    }
                } finally {
                    for (callback in navigationCallbacks) {
                        callback.disconnect()
                    }
                    for (callback in chromeCallbacks) {
                        callback.disconnect()
                    }
                    try {
                        if (root != null) {
                            try {
                                root!!.release()
                            } finally {
                                if (root!!.getParent() is ViewGroup) {
                                    (root!!.getParent() as ViewGroup).removeView(root)
                                }
                            }
                        }
                    } finally {
                        releaseWebView()
                    }
                }
            }
        }

        fun releaseWebView() {
            try {
                if (view.getParent() is ViewGroup) {
                    (view.getParent() as ViewGroup).removeView(view)
                }
                if (!rendererGone) {
                    view.stopLoading()
                    view.setDownloadListener(null)
                    view.setWebChromeClient(WebChromeClient())
                    view.setWebViewClient(WebViewClient())
                }
            } finally {
                view.destroy()
            }
        }
    }

    fun interface SettingsCustomizer {
        fun apply(settings: WebSettings)
    }

    /** 可选配置。每个 Builder 创建一个页面。  */
    class Builder internal constructor(activity: ComponentActivity, owner: LifecycleOwner, container: ViewGroup) {
        internal val activity: ComponentActivity
        internal val owner: LifecycleOwner
        internal val container: ViewGroup
        internal val bridges: MutableMap<String?, Any?> = LinkedHashMap()
        internal val headers: MutableMap<String?, MutableMap<String?, String?>?> = HashMap()
        internal val navigationCallbacks: MutableList<NavigationCallbacks> = ArrayList()
        internal val chromeCallbacks: MutableList<ChromeCallbacks> = ArrayList()
        internal var options: WebOptions = WebOptions.defaults()
        internal var settings: SettingsCustomizer? = null
        internal var layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        internal var index = -1
        private var used = false
        internal var handleBack = true
        internal var builtInBridges = false
        internal var progressEnabled = true
        internal var landscapeVideo = true
        internal var blockUnknownSchemes = false
        internal var externalLinks = ExternalLinks.ASK
        internal var view: WebView? = null
        internal var content: ViewGroup? = null
        internal var progressView: BaseIndicatorView? = null
        internal var progressColor: Int? = null
        internal var progressHeight = 0
        internal var permissionInterceptor: PermissionInterceptor? = null
        internal var uiController: PageUiController? = null
        internal var errorView: View? = null
        internal var errorLayout = 0
        internal var retryView = 0
        internal var rendererGone: Runnable? = null

        init {
            requireMainThread()
            if (container == null) {
                throw NullPointerException("WebPage container is required")
            }
            this.activity = activity
            this.owner = owner
            this.container = container
        }

        fun options(options: WebOptions): Builder {
            this.options = options
            return this
        }

        fun configureSettings(settings: SettingsCustomizer): Builder {
            this.settings = settings
            return this
        }

        /** 使用调用方创建的 WebView。页面接管其生命周期。  */
        fun webView(view: WebView): Builder {
            this.view = view
            return this
        }

        /** 将提供的布局作为网页内容。自定义 WebView 可以已在该布局内。  */
        fun content(content: ViewGroup): Builder {
            this.content = content
            return this
        }

        fun placement(index: Int, params: ViewGroup.LayoutParams): Builder {
            this.index = index
            this.layoutParams = params
            return this
        }

        fun handleBack(enabled: Boolean): Builder {
            handleBack = enabled
            return this
        }

        fun progress(enabled: Boolean): Builder {
            progressEnabled = enabled
            return this
        }

        fun progress(@ColorInt color: Int, heightDp: Int): Builder {
            progressEnabled = true
            progressColor = color
            progressHeight = heightDp
            return this
        }

        fun progress(view: BaseIndicatorView): Builder {
            progressEnabled = true
            progressView = view
            return this
        }

        /** 为可信 H5 暴露 window.Android 和 window.webForge。  */
        fun enableBuiltInBridges(): Builder {
            builtInBridges = true
            return this
        }

        fun bridge(name: String, bridge: Any): Builder {
            bridges.put(name, bridge)
            return this
        }

        fun headers(url: String, values: MutableMap<String?, String?>): Builder {
            val origin = origin(url)
            requireNotNull(origin) { "Expected an HTTP(S) origin" }
            headers.put(origin, copy(values))
            return this
        }

        fun navigationCallbacks(callbacks: NavigationCallbacks): Builder {
            navigationCallbacks.add(callbacks)
            return this
        }

        fun chromeCallbacks(callbacks: ChromeCallbacks): Builder {
            chromeCallbacks.add(callbacks)
            return this
        }

        fun permissionInterceptor(interceptor: PermissionInterceptor?): Builder {
            permissionInterceptor = interceptor
            return this
        }

        fun externalLinks(policy: ExternalLinks): Builder {
            externalLinks = policy
            return this
        }

        fun blockUnknownSchemes(enabled: Boolean): Builder {
            blockUnknownSchemes = enabled
            return this
        }

        fun landscapeVideo(enabled: Boolean): Builder {
            landscapeVideo = enabled
            return this
        }

        fun uiController(controller: PageUiController): Builder {
            uiController = controller
            return this
        }

        fun errorView(view: View): Builder {
            errorView = view
            return this
        }

        fun errorLayout(@LayoutRes layout: Int, @IdRes retryId: Int): Builder {
            errorLayout = layout
            retryView = retryId
            return this
        }

        /** API 26 起在渲染器退出并清理原页面后，于主线程调用。  */
        fun onRendererGone(listener: Runnable): Builder {
            rendererGone = listener
            return this
        }

        @MainThread
        fun build(): WebPage {
            requireMainThread()
            check(!used) { "WebPage.Builder can only build once" }
            check(owner.getLifecycle().getCurrentState() != Lifecycle.State.DESTROYED) { "The WebPage host has been destroyed" }
            if (index < -1 || index > container.getChildCount()) {
                throw IndexOutOfBoundsException("Invalid WebPage position: " + index)
            }
            require(!(content != null && (content!!.getParent() != null || contains(content, container)))) { "WebPage content must be a detached layout outside the container" }
            require(!(view != null && view!!.getParent() != null && !contains(content, view))) { "WebView already belongs to another layout" }
            require(!(progressEnabled && progressView != null && progressView!!.getParent() != null)) { "Progress view already belongs to another layout" }
            require(!(errorView != null && errorView!!.getParent() != null)) { "Error view already belongs to another layout" }
            used = true
            return WebPage(this)
        }

        @MainThread
        fun open(url: String): WebPage {
            val target: String = requireUrl(url)
            val page = build()
            try {
                page.load(target)
                return page
            } catch (failure: RuntimeException) {
                page.closeAfterFailure(failure)
                throw failure
            } catch (failure: Error) {
                page.closeAfterFailure(failure)
                throw failure
            }
        }

        companion object {
            private fun contains(ancestor: ViewGroup?, view: View?): Boolean {
                var current = view
                while (current != null) {
                    if (current === ancestor) {
                        return true
                    }
                    current = if (current.getParent() is View) current.getParent() as View? else null
                }
                return false
            }
        }
    }

    companion object {
        @JvmStatic
        @MainThread
        fun open(
            activity: ComponentActivity, container: ViewGroup,
            url: String
        ): WebPage {
            return builder(activity, container).open(url)
        }

        /** 从 Fragment 的 onViewCreated 起调用。  */
        @JvmStatic
        @MainThread
        fun open(
            fragment: Fragment, container: ViewGroup,
            url: String
        ): WebPage {
            return builder(fragment, container).open(url)
        }

        @JvmStatic
        @MainThread
        fun builder(activity: ComponentActivity, container: ViewGroup): Builder {
            return WebPage.Builder(activity, activity, container)
        }

        @JvmStatic
        @MainThread
        fun builder(fragment: Fragment, container: ViewGroup): Builder {
            requireMainThread()
            return WebPage.Builder(fragment.requireActivity(), fragment.getViewLifecycleOwner(), container)
        }

        private fun requireMainThread() {
            check(Looper.myLooper() == Looper.getMainLooper()) { "Access WebPage on the main thread" }
        }

        private fun requireUrl(url: String): String {
            require(!(url == null || url.trim { it <= ' ' }.isEmpty())) { "WebPage requires a non-empty URL" }
            return url.trim { it <= ' ' }
        }
    }
}
