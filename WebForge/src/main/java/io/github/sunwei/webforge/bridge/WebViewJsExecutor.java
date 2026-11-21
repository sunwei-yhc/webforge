package io.github.sunwei.webforge.bridge;

import android.os.Build;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import org.json.JSONObject;

import io.github.sunwei.webforge.core.WebViewTaskDispatcher;
import io.github.sunwei.webforge.util.WebForgeUtils;

/** JS 调用的统一入口，保留对象/数组参数，并正确转义普通字符串。 */
public final class WebViewJsExecutor implements JsAccessEntrace {
    private final WebViewTaskDispatcher dispatcher;

    public WebViewJsExecutor(WebView webView) {
        this(new WebViewTaskDispatcher(webView));
    }

    public WebViewJsExecutor(WebViewTaskDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void callJs(String js, ValueCallback<String> callback) {
        dispatcher.dispatch(webView -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript(js, value -> {
                    if (callback != null && !dispatcher.isDestroyed()) {
                        callback.onReceiveValue(value);
                    }
                });
            } else {
                webView.loadUrl(js.startsWith("javascript:") ? js : "javascript:" + js);
            }
        });
    }

    @Override
    public void callJs(String js) {
        callJs(js, null);
    }

    @Override
    public void quickCallJs(String method, ValueCallback<String> callback, String... params) {
        StringBuilder script = new StringBuilder("javascript:").append(method).append('(');
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    script.append(',');
                }
                String param = params[i];
                script.append(param == null ? "null"
                        : WebForgeUtils.isJson(param) ? param : JSONObject.quote(param));
            }
        }
        callJs(script.append(')').toString(), callback);
    }

    @Override
    public void quickCallJs(String method, String... params) {
        quickCallJs(method, null, params);
    }

    @Override
    public void quickCallJs(String method) {
        quickCallJs(method, (String[]) null);
    }
}
