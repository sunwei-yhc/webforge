
package io.github.sunwei.webforge.core;

import android.webkit.WebView;

/** 生命周期只控制当前页面，不暂停进程中其他 WebView 的计时器。 */
public class WebViewLifecycle implements WebLifeCycle {
    private final WebViewTaskDispatcher dispatcher;
    private final Runnable beforeDestroy;

    public WebViewLifecycle(WebView webView) {
        this(new WebViewTaskDispatcher(webView), null);
    }

    public WebViewLifecycle(WebViewTaskDispatcher dispatcher, Runnable beforeDestroy) {
        this.dispatcher = dispatcher;
        this.beforeDestroy = beforeDestroy;
    }

    @Override
    public void onResume() {
        dispatcher.dispatch(WebView::onResume);
    }

    @Override
    public void onPause() {
        dispatcher.dispatch(WebView::onPause);
    }

    @Override
    public void onDestroy() {
        dispatcher.destroy(beforeDestroy);
    }
}
