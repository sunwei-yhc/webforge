package io.github.sunwei.webforge

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import io.github.sunwei.webforge.widget.LegacyBridgeWebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

internal object PageJavaScript {
    private val BRIDGE_NAME: Pattern = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*")
    private val FUNCTION: Pattern = Pattern.compile(
        "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
    )

    fun call(function: String, arguments: Array<out Any?>?): String {
        require(FUNCTION.matcher(function).matches()) { "Expected a JavaScript function name" }
        val script = StringBuilder(function).append('(')
        if (arguments == null) {
            return script.append("null)").toString()
        }
        for (i in arguments.indices) {
            if (i > 0) {
                script.append(',')
            }
            val argument = arguments[i]
            if (argument == null || argument === JSONObject.NULL) {
                script.append("null")
            } else if (argument is Number || argument is Boolean
                || argument is JSONObject || argument is JSONArray
            ) {
                script.append(argument)
            } else {
                script.append(JSONObject.quote(argument.toString()))
            }
        }
        return script.append(')').toString()
    }

    @JvmStatic
    @SuppressLint("JavascriptInterface")
    fun register(view: WebView, name: String, bridge: Any) {
        require(BRIDGE_NAME.matcher(name).matches()) { "Expected a JavaScript bridge identifier" }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            check(view is LegacyBridgeWebView) { "JavaScript bridge requires API 17 or the legacy WebForge view" }
            view.addJavascriptInterface(bridge, name)
            return
        }
        for (method in bridge.javaClass.getMethods()) {
            if (method.isAnnotationPresent(JavascriptInterface::class.java)) {
                view.addJavascriptInterface(bridge, name)
                return
            }
        }
        throw IllegalArgumentException("JavaScript bridge needs a public @JavascriptInterface method")
    }
}
