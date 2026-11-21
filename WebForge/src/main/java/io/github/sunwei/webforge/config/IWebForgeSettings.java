
package io.github.sunwei.webforge.config;

import android.webkit.WebView;

/**
 */

public interface IWebForgeSettings<T extends android.webkit.WebSettings> {

    IWebForgeSettings toSetting(WebView webView);

    //是否可用缩放
    void setScale(Boolean isScale);

    T getWebSettings();

}
