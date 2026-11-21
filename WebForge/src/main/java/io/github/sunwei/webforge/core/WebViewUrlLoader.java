
package io.github.sunwei.webforge.core;

import android.webkit.WebView;

import java.util.HashMap;
import java.util.Map;

/** URL 加载入口；所有操作共用页面队列，销毁后自动失效。 */
public class WebViewUrlLoader implements IUrlLoader {
    public static final String TAG = WebViewUrlLoader.class.getSimpleName();
    private final WebViewTaskDispatcher dispatcher;
    private final HttpHeaders httpHeaders;

    public WebViewUrlLoader(WebView webView, HttpHeaders httpHeaders) {
        this(new WebViewTaskDispatcher(webView), httpHeaders);
    }

    public WebViewUrlLoader(WebViewTaskDispatcher dispatcher, HttpHeaders httpHeaders) {
        this.dispatcher = dispatcher;
        this.httpHeaders = httpHeaders == null ? HttpHeaders.create() : httpHeaders;
    }

    @Override
    public void loadUrl(String url) {
        dispatcher.dispatch(webView -> load(webView, url, httpHeaders.getHeadersSnapshot(url)));
    }

    @Override
    public void loadUrl(String url, Map<String, String> headers) {
        // 入队时复制，避免调用方随后修改请求头而改变本次请求。
        Map<String, String> snapshot = headers == null ? null : new HashMap<>(headers);
        dispatcher.dispatch(webView -> load(webView, url, snapshot));
    }

    private void load(WebView webView, String url, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            webView.loadUrl(url);
        } else {
            webView.loadUrl(url, headers);
        }
    }

    @Override
    public void reload() {
        dispatcher.dispatch(WebView::reload);
    }

    @Override
    public void loadData(String data, String mimeType, String encoding) {
        dispatcher.dispatch(webView -> webView.loadData(data, mimeType, encoding));
    }

    @Override
    public void stopLoading() {
        dispatcher.dispatch(WebView::stopLoading);
    }

    @Override
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType,
                                    String encoding, String historyUrl) {
        dispatcher.dispatch(webView -> webView.loadDataWithBaseURL(
                baseUrl, data, mimeType, encoding, historyUrl));
    }

    @Override
    public void postUrl(String url, byte[] postData) {
        byte[] snapshot = postData == null ? null : postData.clone();
        dispatcher.dispatch(webView -> webView.postUrl(url, snapshot));
    }

    @Override
    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }
}
