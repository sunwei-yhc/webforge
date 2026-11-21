package io.github.sunwei.webforge.core;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import io.github.sunwei.webforge.util.WebForgeUtils;

/** 页面独享的主线程任务队列，销毁后拒绝新任务并撤销待执行任务。 */
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY)
public final class WebViewTaskDispatcher {
    public interface Task {
        void run(WebView webView);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private volatile boolean destroyed;

    public WebViewTaskDispatcher(WebView webView) {
        if (webView == null) {
            throw new NullPointerException("WebView 不能为空");
        }
        this.webView = webView;
    }

    public synchronized void dispatch(Task task) {
        if (destroyed) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run(webView);
        } else {
            handler.post(() -> runIfActive(task));
        }
    }

    private synchronized void runIfActive(Task task) {
        if (!destroyed) {
            task.run(webView);
        }
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public synchronized void destroy(Runnable beforeDestroy) {
        if (destroyed) {
            return;
        }
        destroyed = true;
        // 只撤销本页面提交的任务，不触碰 WebView 或主线程共用的 Handler。
        handler.removeCallbacksAndMessages(null);
        final WebView target = webView;
        webView = null;
        Runnable cleanup = () -> {
            try {
                if (beforeDestroy != null) {
                    beforeDestroy.run();
                }
            } finally {
                WebForgeUtils.clearWebView(target);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run();
        } else {
            handler.post(cleanup);
        }
    }
}
