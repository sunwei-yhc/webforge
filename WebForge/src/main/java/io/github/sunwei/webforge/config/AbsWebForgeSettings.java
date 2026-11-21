
package io.github.sunwei.webforge.config;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import io.github.sunwei.webforge.WebForge;
import io.github.sunwei.webforge.util.LogUtils;

import java.io.File;

/**
 */

public abstract class AbsWebForgeSettings implements IWebForgeSettings, WebListenerManager {

    private WebSettings mWebSettings;
    private boolean isScale = false;
    private WebForgeSettingsConfig mSettingsConfig = WebForgeSettingsConfig.defaults();
    //    private static final String TAG = AbsWebForgeSettings.class.getSimpleName();
    private static final String TAG = "WebForgeSettings";
    public static final String USERAGENT_UC = " UCBrowser/11.6.4.950 ";
    public static final String USERAGENT_QQ_BROWSER = " MQQBrowser/8.0 ";
    public static final String USERAGENT_WEBFORGE = " " + WebForgeConfig.WEBFORGE_VERSION + " ";
    protected WebForge mWebForge;

    public static AbsWebForgeSettings getInstance() {
        return new DefaultWebForgeSettings();
    }

    public AbsWebForgeSettings() {
    }

    public final void bindWebForge(WebForge webForge) {
        this.mWebForge = webForge;
        this.bindWebForgeSupport(webForge);
    }

    protected abstract void bindWebForgeSupport(WebForge webForge);

    @Override
    public IWebForgeSettings toSetting(WebView webView) {
        settings(webView);
        return this;
    }

    @Override
    public void setScale(Boolean isScale) {
        this.isScale = Boolean.TRUE.equals(isScale);
    }

    /**
     * Overrides the defaults used by {@link #toSetting(WebView)}.
     */
    public AbsWebForgeSettings setSettingsConfig(WebForgeSettingsConfig settingsConfig) {
        this.mSettingsConfig = settingsConfig == null
                ? WebForgeSettingsConfig.defaults() : settingsConfig;
        return this;
    }

    @SuppressLint({"SetJavaScriptEnabled", "WrongConstant"})
    private void settings(WebView webView) {
        mWebSettings = webView.getSettings();
        final WebForgeSettingsConfig config = mSettingsConfig;
        final boolean zoomEnabled = isScale || config.isZoomEnabled();

        mWebSettings.setJavaScriptEnabled(config.isJavaScriptEnabled());
        mWebSettings.setSupportZoom(zoomEnabled);
        mWebSettings.setBuiltInZoomControls(zoomEnabled);
        mWebSettings.setDisplayZoomControls(false);
        mWebSettings.setLoadWithOverviewMode(config.isResponsiveLayout());
        mWebSettings.setUseWideViewPort(config.isResponsiveLayout());
        mWebSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        mWebSettings.setSavePassword(false);
        mWebSettings.setCacheMode(config.getCacheMode());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mWebSettings.setMixedContentMode(config.getMixedContentMode());
            mWebSettings.setMediaPlaybackRequiresUserGesture(
                    config.isMediaPlaybackRequiresUserGesture());
            mWebSettings.setSupportMultipleWindows(config.isSupportMultipleWindows());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mWebSettings.setSupportMultipleWindows(config.isSupportMultipleWindows());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        mWebSettings.setTextZoom(config.getTextZoom());
        mWebSettings.setLoadsImagesAutomatically(true);
        mWebSettings.setBlockNetworkImage(false);
        mWebSettings.setAllowFileAccess(config.isFileAccessEnabled());
        mWebSettings.setAllowContentAccess(config.isContentAccessEnabled());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mWebSettings.setAllowFileAccessFromFileURLs(
                    config.isFileUrlCrossOriginAccessEnabled());
            mWebSettings.setAllowUniversalAccessFromFileURLs(
                    config.isFileUrlCrossOriginAccessEnabled());
        }
        mWebSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        mWebSettings.setDomStorageEnabled(config.isDomStorageEnabled());
        mWebSettings.setDatabaseEnabled(config.isDatabaseEnabled());
        mWebSettings.setNeedInitialFocus(true);
        mWebSettings.setDefaultTextEncodingName("UTF-8");
        mWebSettings.setDefaultFontSize(16);
        mWebSettings.setGeolocationEnabled(config.isGeolocationEnabled());
        String dir = WebForgeConfig.getCachePath(webView.getContext());
        File cacheDirectory = new File(dir);
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs();
        }
        mWebSettings.setGeolocationDatabasePath(dir);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mWebSettings.setSafeBrowsingEnabled(config.isSafeBrowsingEnabled());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(
                    webView, config.isThirdPartyCookiesEnabled());
        }
        String userAgent = mWebSettings.getUserAgentString();
        if (userAgent == null) {
            userAgent = "";
        }
        if (!userAgent.contains(USERAGENT_WEBFORGE.trim())) {
            userAgent = userAgent.concat(USERAGENT_WEBFORGE);
        }
        if (!config.getUserAgentSuffix().isEmpty()
                && !userAgent.contains(config.getUserAgentSuffix())) {
            userAgent = userAgent.concat(" ").concat(config.getUserAgentSuffix());
        }
        mWebSettings.setUserAgentString(userAgent);
        LogUtils.i(TAG, "UserAgentString : " + userAgent);
    }

    @Override
    public WebSettings getWebSettings() {
        return mWebSettings;
    }

    @Override
    public WebListenerManager setWebChromeClient(WebView webview, WebChromeClient webChromeClient) {
        webview.setWebChromeClient(webChromeClient);
        return this;
    }

    @Override
    public WebListenerManager setWebViewClient(WebView webView, WebViewClient webViewClient) {
        webView.setWebViewClient(webViewClient);
        return this;
    }

    @Override
    public WebListenerManager setDownloader(WebView webView, DownloadListener downloadListener) {
        webView.setDownloadListener(downloadListener);

        return this;
    }

}
