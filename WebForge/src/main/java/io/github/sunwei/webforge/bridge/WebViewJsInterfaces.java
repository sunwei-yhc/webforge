package io.github.sunwei.webforge.bridge;

import android.annotation.SuppressLint;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.lang.reflect.Method;
import java.util.Map;

import io.github.sunwei.webforge.WebForge;
import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.core.WebCreator;
import io.github.sunwei.webforge.util.LogUtils;

/** 检查并注册宿主注入的 JavaScript 接口。 */
public final class WebViewJsInterfaces implements JsInterfaceHolder {
    private static final String TAG = WebViewJsInterfaces.class.getSimpleName();

    private final WebCreator webCreator;
    private final WebView webView;
    private final WebForge.SecurityType securityType;

    public WebViewJsInterfaces(WebCreator webCreator, WebForge.SecurityType securityType) {
        this.webCreator = webCreator;
        this.webView = webCreator.getWebView();
        this.securityType = securityType;
    }

    @Override
    public boolean checkObject(Object object) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || usesWebForgeBridge()) {
            return true;
        }
        for (Method method : object.getClass().getMethods()) {
            if (method.isAnnotationPresent(JavascriptInterface.class)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public JsInterfaceHolder addJavaObjects(Map<String, Object> objects) {
        if (!canInject()) {
            LogUtils.e(TAG, "Strict mode blocked JavaScript interface injection");
            return this;
        }
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            inject(entry.getKey(), entry.getValue());
        }
        return this;
    }

    @Override
    public JsInterfaceHolder addJavaObject(String name, Object object) {
        if (canInject()) {
            inject(name, object);
        }
        return this;
    }

    private boolean canInject() {
        return securityType != WebForge.SecurityType.STRICT_CHECK
                || usesWebForgeBridge()
                || Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    private boolean usesWebForgeBridge() {
        return webCreator.getWebViewType() == WebForgeConfig.WEBVIEW_WEBFORGE_SAFE_TYPE;
    }

    @SuppressLint("JavascriptInterface")
    private void inject(String name, Object object) {
        if (!checkObject(object)) {
            throw new JsInterfaceObjectException("No public method annotated with @JavascriptInterface");
        }
        webView.addJavascriptInterface(object, name);
    }
}
