package io.github.sunwei.webforge.config;

import android.webkit.DownloadListener;
import android.webkit.WebView;

import io.github.sunwei.webforge.WebForge;
import io.github.sunwei.webforge.download.LazyDownloadImpl;

/** 默认 WebView 设置和按需加载的下载监听器。 */
public class DefaultWebForgeSettings extends AbsWebForgeSettings {

    @Override
    protected void bindWebForgeSupport(WebForge webForge) {
    }

    @Override
    public WebListenerManager setDownloader(WebView webView, DownloadListener downloadListener) {
        if (downloadListener == null) {
            downloadListener = new LazyDownloadImpl(
                    mWebForge.getActivity(),
                    webView,
                    mWebForge.getPermissionInterceptor()
            );
        }
        return super.setDownloader(webView, downloadListener);
    }

}
