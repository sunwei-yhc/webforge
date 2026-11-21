package io.github.sunwei.webforge.core;

import android.view.KeyEvent;
import android.webkit.WebView;

/** 返回键优先退出全屏视频，再尝试回到上一页。 */
public final class WebBackHandler implements IEventHandler {
    private final WebView webView;
    private final EventInterceptor interceptor;

    public WebBackHandler(WebView webView, EventInterceptor interceptor) {
        this.webView = webView;
        this.interceptor = interceptor;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return keyCode == KeyEvent.KEYCODE_BACK && back();
    }

    @Override
    public boolean back() {
        if (interceptor != null && interceptor.event()) {
            return true;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }
}
