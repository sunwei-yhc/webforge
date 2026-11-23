package io.github.sunwei.webforge

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import io.github.sunwei.webforge.config.WebForgeConfig
import io.github.sunwei.webforge.config.WebOptions
import java.io.File

/** 将不可变的页面配置应用到当前 WebView。  */
internal object WebViewSettings {
    @JvmStatic
    @SuppressLint("SetJavaScriptEnabled", "WrongConstant")
    fun apply(webView: WebView, config: WebOptions) {
        val settings = webView.getSettings()
        val zoomEnabled = config.isZoomEnabled

        settings.setJavaScriptEnabled(config.isJavaScriptEnabled)
        settings.setSupportZoom(zoomEnabled)
        settings.setBuiltInZoomControls(zoomEnabled)
        settings.setDisplayZoomControls(false)
        settings.setLoadWithOverviewMode(config.isResponsiveLayout)
        settings.setUseWideViewPort(config.isResponsiveLayout)
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL)
        settings.setSavePassword(false)
        settings.setCacheMode(config.cacheMode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(config.mixedContentMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(config.isMediaPlaybackRequiresUserGesture)
        }
        settings.setSupportMultipleWindows(config.isSupportMultipleWindows)
        settings.setTextZoom(config.textZoom)
        settings.setLoadsImagesAutomatically(true)
        settings.setBlockNetworkImage(false)
        settings.setAllowFileAccess(config.isFileAccessEnabled)
        settings.setAllowContentAccess(config.isContentAccessEnabled)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(
                config.isFileUrlCrossOriginAccessEnabled
            )
            settings.setAllowUniversalAccessFromFileURLs(
                config.isFileUrlCrossOriginAccessEnabled
            )
        }
        settings.setJavaScriptCanOpenWindowsAutomatically(true)
        settings.setDomStorageEnabled(config.isDomStorageEnabled)
        settings.setDatabaseEnabled(config.isDatabaseEnabled)
        settings.setNeedInitialFocus(true)
        settings.setDefaultTextEncodingName("UTF-8")
        settings.setDefaultFontSize(16)
        settings.setGeolocationEnabled(config.isGeolocationEnabled)
        if (config.isGeolocationEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val dir = WebForgeConfig.getCachePath(webView.getContext())
            val cacheDirectory = File(dir)
            if (!cacheDirectory.exists()) {
                cacheDirectory.mkdirs()
            }
            settings.setGeolocationDatabasePath(dir)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(config.isSafeBrowsingEnabled)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptThirdPartyCookies(
                webView, config.isThirdPartyCookiesEnabled
            )
        }
        val originalUserAgent = settings.getUserAgentString()
        var userAgent = if (originalUserAgent == null) "" else originalUserAgent
        if (!userAgent.contains(WebForgeConfig.WEBFORGE_VERSION)) {
            userAgent = userAgent + " " + WebForgeConfig.WEBFORGE_VERSION
        }
        val suffix = config.userAgentSuffix.orEmpty()
        if (suffix.isNotEmpty()
            && !userAgent.contains(suffix)
        ) {
            userAgent = userAgent + " " + suffix
        }
        if (userAgent != originalUserAgent) {
            settings.setUserAgentString(userAgent)
        }
    }
}
