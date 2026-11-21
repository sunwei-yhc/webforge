package io.github.sunwei.webforge;

import android.annotation.SuppressLint;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.config.WebOptions;

import java.io.File;

/** 将不可变的页面配置应用到当前 WebView。 */
final class WebViewSettings {
    private WebViewSettings() {}

    @SuppressLint({"SetJavaScriptEnabled", "WrongConstant"})
    static void apply(WebView webView, WebOptions config) {
        WebSettings settings = webView.getSettings();
        boolean zoomEnabled = config.isZoomEnabled();

        settings.setJavaScriptEnabled(config.isJavaScriptEnabled());
        settings.setSupportZoom(zoomEnabled);
        settings.setBuiltInZoomControls(zoomEnabled);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(config.isResponsiveLayout());
        settings.setUseWideViewPort(config.isResponsiveLayout());
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        settings.setSavePassword(false);
        settings.setCacheMode(config.getCacheMode());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(config.getMixedContentMode());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(config.isMediaPlaybackRequiresUserGesture());
        }
        settings.setSupportMultipleWindows(config.isSupportMultipleWindows());
        settings.setTextZoom(config.getTextZoom());
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setAllowFileAccess(config.isFileAccessEnabled());
        settings.setAllowContentAccess(config.isContentAccessEnabled());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(
                    config.isFileUrlCrossOriginAccessEnabled());
            settings.setAllowUniversalAccessFromFileURLs(
                    config.isFileUrlCrossOriginAccessEnabled());
        }
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setDomStorageEnabled(config.isDomStorageEnabled());
        settings.setDatabaseEnabled(config.isDatabaseEnabled());
        settings.setNeedInitialFocus(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setDefaultFontSize(16);
        settings.setGeolocationEnabled(config.isGeolocationEnabled());
        if (config.isGeolocationEnabled() && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            String dir = WebForgeConfig.getCachePath(webView.getContext());
            File cacheDirectory = new File(dir);
            if (!cacheDirectory.exists()) {
                cacheDirectory.mkdirs();
            }
            settings.setGeolocationDatabasePath(dir);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(config.isSafeBrowsingEnabled());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptThirdPartyCookies(
                    webView, config.isThirdPartyCookiesEnabled());
        }
        String originalUserAgent = settings.getUserAgentString();
        String userAgent = originalUserAgent == null ? "" : originalUserAgent;
        if (!userAgent.contains(WebForgeConfig.WEBFORGE_VERSION)) {
            userAgent = userAgent + " " + WebForgeConfig.WEBFORGE_VERSION;
        }
        if (!config.getUserAgentSuffix().isEmpty()
                && !userAgent.contains(config.getUserAgentSuffix())) {
            userAgent = userAgent.concat(" ").concat(config.getUserAgentSuffix());
        }
        if (!userAgent.equals(originalUserAgent)) {
            settings.setUserAgentString(userAgent);
        }
    }
}
