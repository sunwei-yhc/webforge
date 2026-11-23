package io.github.sunwei.webforge.widget

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.util.Pair
import android.view.ActionMode
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.webkit.JsPromptResult
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import io.github.sunwei.webforge.bridge.JsCallJava
import io.github.sunwei.webforge.bridge.JsCallJava.Companion.getInterfacedName
import io.github.sunwei.webforge.bridge.JsCallJava.Companion.getMsgJSONObject
import io.github.sunwei.webforge.bridge.JsCallJava.Companion.isSafeWebViewCallMsg
import io.github.sunwei.webforge.client.ChromeCallbacks
import io.github.sunwei.webforge.client.NavigationCallbacks
import io.github.sunwei.webforge.util.LogUtils
import io.github.sunwei.webforge.util.LogUtils.Companion.e
import io.github.sunwei.webforge.util.LogUtils.Companion.i
import io.github.sunwei.webforge.util.LogUtils.Companion.safeCheckCrash
import java.net.URI
import java.net.URLEncoder

/**
 */
open class LegacyBridgeWebView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : CompatibleWebView(context, attrs) {
    private var mJsCallJavas: MutableMap<String?, JsCallJava?>? = null
    private var mInjectJavaScripts: MutableMap<String?, String?>? = null
    private val mFixedOnReceivedTitle: FixedOnReceivedTitle
    private val mIsInited: Boolean
    private var mIsAccessibilityEnabledOriginal: Boolean? = null

    init {
        removeSearchBoxJavaBridge()
        mIsInited = true
        mFixedOnReceivedTitle = FixedOnReceivedTitle()
    }

    /**
     * 经过大量的测试，按照以下方式才能保证JS脚本100%注入成功：
     * 1、在第一次loadUrl之前注入JS（在addJavascriptInterface里面注入即可，setWebViewClient和setWebChromeClient要在addJavascriptInterface之前执行）；
     * 2、在webViewClient.onPageStarted中都注入JS；
     * 3、在webChromeClient.onProgressChanged中都注入JS，并且不能通过自检查（onJsPrompt里面判断）JS是否注入成功来减少注入JS的次数，因为网页中的JS可以同时打开多个url导致无法控制检查的准确性；
     *
     */
    @SuppressLint("JavascriptInterface")
    @Deprecated("Android 4.2.2及以上版本的 addJavascriptInterface 方法已经解决了安全问题，如果不使用“网页能将JS函数传到Java层”功能，不建议使用该类，毕竟系统的JS注入效率才是最高的；")
    override fun addJavascriptInterface(interfaceObj: Any, interfaceName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            super.addJavascriptInterface(interfaceObj, interfaceName)
            Log.i(TAG, "注入")
            return
        } else {
            Log.i(TAG, "use mJsCallJavas:" + interfaceName)
        }

        i(TAG, "addJavascriptInterface:" + interfaceObj + "   interfaceName:" + interfaceName)
        if (mJsCallJavas == null) {
            mJsCallJavas = HashMap<String?, JsCallJava?>()
        }
        mJsCallJavas!!.put(interfaceName, JsCallJava(interfaceObj, interfaceName))
        injectJavaScript()
        if (LogUtils.isDebug()) {
            Log.d(TAG, "injectJavaScript, addJavascriptInterface.interfaceObj = " + interfaceObj + ", interfaceName = " + interfaceName)
        }
        addJavascriptInterfaceSupport(interfaceObj, interfaceName)
    }

    protected fun addJavascriptInterfaceSupport(interfaceObj: Any?, interfaceName: String?) {
    }

    override fun setWebChromeClient(client: WebChromeClient?) {
        val mWebForgeChrome = WebForgeChrome(this)
        if (client != null) {
            mWebForgeChrome.connect(client)
        }
        mFixedOnReceivedTitle.setWebChromeClient(client)
        super.setWebChromeClient(mWebForgeChrome)
        setWebChromeClientSupport(mWebForgeChrome)
    }

    protected fun setWebChromeClientSupport(client: WebChromeClient?) {
    }

    override fun setWebViewClient(client: WebViewClient) {
        val mWebForgeClient = WebForgeClient(this)
        mWebForgeClient.connect(client)
        super.setWebViewClient(mWebForgeClient)
        setWebViewClientSupport(mWebForgeClient)
    }

    fun setWebViewClientSupport(client: WebViewClient?) {
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        return super.startActionMode(callback)
    }

    override fun destroy() {
        setVisibility(GONE)
        if (mJsCallJavas != null) {
            mJsCallJavas!!.clear()
        }
        if (mInjectJavaScripts != null) {
            mInjectJavaScripts!!.clear()
        }
        removeAllViewsInLayout()
        fixedStillAttached()
        releaseConfigCallback()
        if (mIsInited) {
            resetAccessibilityEnabled()
            i(TAG, "destroy web")
            super.destroy()
        }
    }

    override fun clearHistory() {
        if (mIsInited) {
            super.clearHistory()
        }
    }

    override fun setOverScrollMode(mode: Int) {
        try {
            super.setOverScrollMode(mode)
        } catch (e: Throwable) {
            val pair: Pair<Boolean?, String?> = isWebViewPackageException(e)
            if (pair.first == true) {
                Toast.makeText(getContext(), pair.second, Toast.LENGTH_SHORT).show()
                destroy()
            } else {
                throw e
            }
        }
    }

    override fun isPrivateBrowsingEnabled(): Boolean {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
            && getSettings() == null
        ) {
            return false // getSettings().isPrivateBrowsingEnabled()
        } else {
            return super.isPrivateBrowsingEnabled()
        }
    }

    /**
     * 添加并注入JavaScript脚本（和“addJavascriptInterface”注入对象的注入时机一致，100%能注入成功）；
     * 注意：为了做到能100%注入，需要在注入的js中自行判断对象是否已经存在（如：if (typeof(window.Android) = 'undefined')）；
     *
     * @param javaScript
     */
    open fun addInjectJavaScript(javaScript: String) {
        if (mInjectJavaScripts == null) {
            mInjectJavaScripts = HashMap<String?, String?>()
        }
        mInjectJavaScripts!!.put(javaScript.hashCode().toString(), javaScript)
        injectExtraJavaScript()
    }

    private fun injectJavaScript() {
        for (entry in mJsCallJavas!!.entries) {
            this.loadUrl(buildNotRepeatInjectJS(entry.key!!, entry.value!!.preloadInterfaceJs))
        }
    }

    private fun injectExtraJavaScript() {
        for (entry in mInjectJavaScripts!!.entries) {
            this.loadUrl(buildNotRepeatInjectJS(entry.key!!, entry.value))
        }
    }

    /**
     * 构建一个“不会重复注入”的js脚本；
     *
     * @param key
     * @param js
     * @return
     */
    open fun buildNotRepeatInjectJS(key: String, js: String?): String {
        val obj = String.format("__injectFlag_%1\$s__", key)
        val sb = StringBuilder()
        sb.append("javascript:try{(function(){if(window.")
        sb.append(obj)
        sb.append("){console.log('")
        sb.append(obj)
        sb.append(" has been injected');return;}window.")
        sb.append(obj)
        sb.append("=true;")
        sb.append(js)
        sb.append("}())}catch(e){console.warn(e)}")
        return sb.toString()
    }

    /**
     * 构建一个“带try catch”的js脚本；
     *
     * @param js
     * @return
     */
    open fun buildTryCatchInjectJS(js: String?): String {
        val sb = StringBuilder()
        sb.append("javascript:try{")
        sb.append(js)
        sb.append("}catch(e){console.warn(e)}")
        return sb.toString()
    }


    open class WebForgeClient internal constructor(private val mWebForgeView: LegacyBridgeWebView) : NavigationCallbacks() {
        public override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (mWebForgeView.mJsCallJavas != null) {
                mWebForgeView.injectJavaScript()
                if (LogUtils.isDebug()) {
                    Log.d(TAG, "injectJavaScript, onPageStarted.url = " + view?.getUrl())
                }
            }
            if (mWebForgeView.mInjectJavaScripts != null) {
                mWebForgeView.injectExtraJavaScript()
            }
            mWebForgeView.mFixedOnReceivedTitle.onPageStarted()
            mWebForgeView.fixedAccessibilityInjectorExceptionForOnPageFinished(url)
        }

        public override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.let(mWebForgeView.mFixedOnReceivedTitle::onPageFinished)
            if (LogUtils.isDebug()) {
                Log.d(TAG, "onPageFinished.url = " + view?.getUrl())
            }
        }
    }

    open class WebForgeChrome internal constructor(private val mWebForgeView: LegacyBridgeWebView) : ChromeCallbacks() {
        public override fun onReceivedTitle(view: WebView?, title: String?) {
            this.mWebForgeView.mFixedOnReceivedTitle.onReceivedTitle()
            super.onReceivedTitle(view, title)
        }

        public override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (this.mWebForgeView.mJsCallJavas != null) {
                this.mWebForgeView.injectJavaScript()
                if (LogUtils.isDebug()) {
                    Log.d(TAG, "injectJavaScript, onProgressChanged.newProgress = " + newProgress + ", url = " + view?.getUrl())
                }
            }
            if (this.mWebForgeView.mInjectJavaScripts != null) {
                this.mWebForgeView.injectExtraJavaScript()
            }
            super.onProgressChanged(view, newProgress)
        }

        public override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
            Log.i(TAG, "onJsPrompt:" + url + "  message:" + message + "  d:" + defaultValue + "  ")
            if (this.mWebForgeView.mJsCallJavas != null && message != null && isSafeWebViewCallMsg(message)) {
                val jsonObject = getMsgJSONObject(message)
                val interfacedName = getInterfacedName(jsonObject)
                if (interfacedName != null) {
                    val mJsCallJava = this.mWebForgeView.mJsCallJavas!!.get(interfacedName)
                    if (mJsCallJava != null) {
                        result?.confirm(mJsCallJava.call(view, jsonObject))
                    }
                }
                return true
            } else {
                return super.onJsPrompt(view, url, message, defaultValue, result)
            }
        }
    }

    /**
     * 解决部分手机webView返回时不触发onReceivedTitle的问题（如：三星SM-G9008V 4.4.2）；
     */
    private class FixedOnReceivedTitle {
        private var mWebChromeClient: WebChromeClient? = null
        private var mIsOnReceivedTitle = false

        fun setWebChromeClient(webChromeClient: WebChromeClient?) {
            mWebChromeClient = webChromeClient
        }

        fun onPageStarted() {
            mIsOnReceivedTitle = false
        }

        fun onPageFinished(view: WebView) {
            if (!mIsOnReceivedTitle && mWebChromeClient != null) {
                var list: WebBackForwardList? = null
                try {
                    list = view.copyBackForwardList()
                } catch (e: NullPointerException) {
                    if (LogUtils.isDebug()) {
                        e.printStackTrace()
                    }
                }
                if (list != null && list.getSize() > 0 && list.getCurrentIndex() >= 0 && list.getItemAtIndex(list.getCurrentIndex()) != null) {
                    val previousTitle = list.getItemAtIndex(list.getCurrentIndex()).getTitle()
                    mWebChromeClient!!.onReceivedTitle(view, previousTitle)
                }
            }
        }

        fun onReceivedTitle() {
            mIsOnReceivedTitle = true
        }
    }

    // Activity在onDestory时调用webView的destroy，可以停止播放页面中的音频
    private fun fixedStillAttached() {
        // java.lang.Throwable: Error: WebView.destroy() called while still attached!
        // at android.webkit.WebViewClassic.destroy(WebViewClassic.java:4142)
        // at android.webkit.WebView.destroy(WebView.java:707)
        val parent = getParent()
        if (parent is ViewGroup) { // 由于自定义webView构建时传入了该Activity的context对象，因此需要先从父容器中移除webView，然后再销毁webView；
            val mWebViewContainer = getParent() as ViewGroup
            mWebViewContainer.removeAllViewsInLayout()
        }
    }

    // 解决WebView内存泄漏问题；
    private fun releaseConfigCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) { // JELLY_BEAN
            try {
                var field = WebView::class.java.getDeclaredField("mWebViewCore")
                field = field.getType().getDeclaredField("mBrowserFrame")
                field = field.getType().getDeclaredField("sConfigCallback")
                field.setAccessible(true)
                field.set(null, null)
            } catch (e: NoSuchFieldException) {
                if (LogUtils.isDebug()) {
                    e.printStackTrace()
                }
            } catch (e: IllegalAccessException) {
                if (LogUtils.isDebug()) {
                    e.printStackTrace()
                }
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) { // KITKAT
            try {
                val sConfigCallback = Class.forName("android.webkit.BrowserFrame").getDeclaredField("sConfigCallback")
                if (sConfigCallback != null) {
                    sConfigCallback.setAccessible(true)
                    sConfigCallback.set(null, null)
                }
            } catch (e: NoSuchFieldException) {
                if (LogUtils.isDebug()) {
                    e.printStackTrace()
                }
            } catch (e: ClassNotFoundException) {
                if (LogUtils.isDebug()) {
                    e.printStackTrace()
                }
            } catch (e: IllegalAccessException) {
                if (LogUtils.isDebug()) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Android 4.4 KitKat 使用Chrome DevTools 远程调试WebView
     * WebView.setWebContentsDebuggingEnabled(true);
     */
    @TargetApi(19)
    protected fun trySetWebDebuggEnabled() {
        if (LogUtils.isDebug() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                val clazz: Class<*> = WebView::class.java
                val method = clazz.getMethod("setWebContentsDebuggingEnabled", Boolean::class.javaPrimitiveType)
                method.invoke(null, true)
            } catch (e: Throwable) {
                if (LogUtils.isDebug()) {
                    e.printStackTrace()
                }
            }
        }
    }

    @TargetApi(11)
    protected fun removeSearchBoxJavaBridge(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
            ) {
                val method = this.javaClass.getMethod("removeJavascriptInterface", String::class.java)
                method.invoke(this, "searchBoxJavaBridge_")
                return true
            }
        } catch (e: Exception) {
            if (LogUtils.isDebug()) {
                e.printStackTrace()
            }
        }
        return false
    }

    protected fun fixedAccessibilityInjectorException() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN_MR1 && mIsAccessibilityEnabledOriginal == null && this.isAccessibilityEnabled) {
            mIsAccessibilityEnabledOriginal = true
            this.isAccessibilityEnabled = false
        }
    }

    protected fun fixedAccessibilityInjectorExceptionForOnPageFinished(url: String?) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN && getSettings().getJavaScriptEnabled()
            && mIsAccessibilityEnabledOriginal == null && this.isAccessibilityEnabled
        ) {
            try {
                try {
                    URLEncoder.encode(URI(url).toString(), "utf-8")
                    //                    URLEncodedUtils.parse(new URI(url), null); // AccessibilityInjector.getAxsUrlParameterValue
                } catch (e: IllegalArgumentException) {
                    if ("bad parameter" == e.message) {
                        mIsAccessibilityEnabledOriginal = true
                        this.isAccessibilityEnabled = false
                        safeCheckCrash(TAG, "fixedAccessibilityInjectorExceptionForOnPageFinished.url = " + url, e)
                    }
                }
            } catch (e: Throwable) {
                if (LogUtils.isDebug()) {
                    e(TAG, "fixedAccessibilityInjectorExceptionForOnPageFinished", e)
                }
            }
        }
    }

    private var isAccessibilityEnabled: Boolean
        get() {
            val am =
                getContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            return am.isEnabled()
        }
        private set(enabled) {
            val am =
                getContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            try {
                val setAccessibilityState = am.javaClass.getDeclaredMethod("setAccessibilityState", Boolean::class.javaPrimitiveType)
                setAccessibilityState.setAccessible(true)
                setAccessibilityState.invoke(am, enabled)
                setAccessibilityState.setAccessible(false)
            } catch (e: Throwable) {
                if (LogUtils.isDebug()) {
                    e(TAG, "setAccessibilityEnabled", e)
                }
            }
        }

    private fun resetAccessibilityEnabled() {
        if (mIsAccessibilityEnabledOriginal != null) {
            this.isAccessibilityEnabled = mIsAccessibilityEnabledOriginal!!
        }
    }

    companion object {
        private val TAG: String = LegacyBridgeWebView::class.java.getSimpleName()
        @JvmStatic
        fun isWebViewPackageException(e: Throwable): Pair<Boolean?, String?> {
            val messageCause = if (e.cause == null) e.toString() else e.cause.toString()
            val trace = Log.getStackTraceString(e)
            if (trace.contains("android.content.pm.PackageManager\$NameNotFoundException")
                || trace.contains("java.lang.RuntimeException: Cannot load WebView")
                || trace.contains("android.webkit.WebViewFactory\$MissingWebViewPackageException: Failed to load WebView provider: No WebView installed")
            ) {
                safeCheckCrash(TAG, "isWebViewPackageException", e)
                return Pair<Boolean?, String?>(true, "WebView load failed, " + messageCause)
            }
            return Pair<Boolean?, String?>(false, messageCause)
        }
    }
}
