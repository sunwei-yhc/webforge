package io.github.sunwei.webforge.security

import android.os.Build
import android.webkit.WebView

/** 移除旧版系统 WebView 内置的 JavaScript 接口。  */
object LegacyWebViewSecurity {
    @JvmStatic
    fun removeBuiltInJsInterfaces(webView: WebView) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webView.removeJavascriptInterface("searchBoxJavaBridge_")
            webView.removeJavascriptInterface("accessibility")
            webView.removeJavascriptInterface("accessibilityTraversal")
        }
    }
}
