package io.github.sunwei.webforge;

import android.annotation.SuppressLint;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.sunwei.webforge.widget.LegacyBridgeWebView;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

final class PageJavaScript {
    private static final Pattern BRIDGE_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern FUNCTION = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    private PageJavaScript() {}

    static String call(String function, Object[] arguments) {
        if (!FUNCTION.matcher(function).matches()) {
            throw new IllegalArgumentException("Expected a JavaScript function name");
        }
        StringBuilder script = new StringBuilder(function).append('(');
        if (arguments == null) {
            return script.append("null)").toString();
        }
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0) {
                script.append(',');
            }
            Object argument = arguments[i];
            if (argument == null || argument == JSONObject.NULL) {
                script.append("null");
            } else if (argument instanceof Number || argument instanceof Boolean
                    || argument instanceof JSONObject || argument instanceof JSONArray) {
                script.append(argument);
            } else {
                script.append(JSONObject.quote(argument.toString()));
            }
        }
        return script.append(')').toString();
    }

    @SuppressLint("JavascriptInterface")
    static void register(WebView view, String name, Object bridge) {
        if (!BRIDGE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Expected a JavaScript bridge identifier");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (!(view instanceof LegacyBridgeWebView)) {
                throw new IllegalStateException("JavaScript bridge requires API 17 or the legacy WebForge view");
            }
            view.addJavascriptInterface(bridge, name);
            return;
        }
        for (Method method : bridge.getClass().getMethods()) {
            if (method.isAnnotationPresent(JavascriptInterface.class)) {
                view.addJavascriptInterface(bridge, name);
                return;
            }
        }
        throw new IllegalArgumentException("JavaScript bridge needs a public @JavascriptInterface method");
    }
}
