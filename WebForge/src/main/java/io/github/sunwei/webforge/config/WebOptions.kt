package io.github.sunwei.webforge.config

import android.webkit.WebSettings

/** 一个页面的不可变 WebView 设置，可用于多个页面。  */
class WebOptions private constructor(builder: Builder) {
    val isJavaScriptEnabled: Boolean
    val isDomStorageEnabled: Boolean
    val isDatabaseEnabled: Boolean
    val isGeolocationEnabled: Boolean
    val isFileAccessEnabled: Boolean
    val isContentAccessEnabled: Boolean
    val isFileUrlCrossOriginAccessEnabled: Boolean
    val isResponsiveLayout: Boolean
    val isZoomEnabled: Boolean
    val isMediaPlaybackRequiresUserGesture: Boolean
    val isThirdPartyCookiesEnabled: Boolean
    val isSafeBrowsingEnabled: Boolean
    val isSupportMultipleWindows: Boolean
    val mixedContentMode: Int
    val cacheMode: Int
    val textZoom: Int
    val userAgentSuffix: String?

    init {
        this.isJavaScriptEnabled = builder.javaScriptEnabled
        this.isDomStorageEnabled = builder.domStorageEnabled
        this.isDatabaseEnabled = builder.databaseEnabled
        this.isGeolocationEnabled = builder.geolocationEnabled
        this.isFileAccessEnabled = builder.fileAccessEnabled
        this.isContentAccessEnabled = builder.contentAccessEnabled
        this.isFileUrlCrossOriginAccessEnabled = builder.fileUrlCrossOriginAccessEnabled
        this.isResponsiveLayout = builder.responsiveLayout
        this.isZoomEnabled = builder.zoomEnabled
        this.isMediaPlaybackRequiresUserGesture = builder.mediaPlaybackRequiresUserGesture
        this.isThirdPartyCookiesEnabled = builder.thirdPartyCookiesEnabled
        this.isSafeBrowsingEnabled = builder.safeBrowsingEnabled
        this.isSupportMultipleWindows = builder.supportMultipleWindows
        mixedContentMode = builder.mixedContentMode
        cacheMode = builder.cacheMode
        textZoom = builder.textZoom
        userAgentSuffix = builder.userAgentSuffix
    }

    class Builder {
        internal var javaScriptEnabled = true
        internal var domStorageEnabled = true
        internal var databaseEnabled = true
        internal var geolocationEnabled = true
        internal var fileAccessEnabled = true
        internal var contentAccessEnabled = true
        internal var fileUrlCrossOriginAccessEnabled = false
        internal var responsiveLayout = true
        internal var zoomEnabled = false
        internal var mediaPlaybackRequiresUserGesture = false
        internal var thirdPartyCookiesEnabled = true
        internal var safeBrowsingEnabled = true
        internal var supportMultipleWindows = true
        internal var mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        internal var cacheMode = WebSettings.LOAD_DEFAULT
        internal var textZoom = 100
        internal var userAgentSuffix = ""

        fun setJavaScriptEnabled(enabled: Boolean): Builder {
            javaScriptEnabled = enabled
            return this
        }

        fun setDomStorageEnabled(enabled: Boolean): Builder {
            domStorageEnabled = enabled
            return this
        }

        fun setDatabaseEnabled(enabled: Boolean): Builder {
            databaseEnabled = enabled
            return this
        }

        fun setGeolocationEnabled(enabled: Boolean): Builder {
            geolocationEnabled = enabled
            return this
        }

        fun setFileAccessEnabled(enabled: Boolean): Builder {
            fileAccessEnabled = enabled
            return this
        }

        fun setContentAccessEnabled(enabled: Boolean): Builder {
            contentAccessEnabled = enabled
            return this
        }

        /**
         * Enables file:// pages to read other file:// resources. Keep this off
         * unless the application is loading all of its content from a trusted
         * local directory.
         */
        fun setFileUrlCrossOriginAccessEnabled(enabled: Boolean): Builder {
            fileUrlCrossOriginAccessEnabled = enabled
            return this
        }

        fun setResponsiveLayout(enabled: Boolean): Builder {
            responsiveLayout = enabled
            return this
        }

        fun setZoomEnabled(enabled: Boolean): Builder {
            zoomEnabled = enabled
            return this
        }

        fun setMediaPlaybackRequiresUserGesture(required: Boolean): Builder {
            mediaPlaybackRequiresUserGesture = required
            return this
        }

        fun setThirdPartyCookiesEnabled(enabled: Boolean): Builder {
            thirdPartyCookiesEnabled = enabled
            return this
        }

        fun setSafeBrowsingEnabled(enabled: Boolean): Builder {
            safeBrowsingEnabled = enabled
            return this
        }

        fun setSupportMultipleWindows(enabled: Boolean): Builder {
            supportMultipleWindows = enabled
            return this
        }

        fun setMixedContentMode(mixedContentMode: Int): Builder {
            this.mixedContentMode = mixedContentMode
            return this
        }

        fun setCacheMode(cacheMode: Int): Builder {
            this.cacheMode = cacheMode
            return this
        }

        fun setTextZoom(textZoom: Int): Builder {
            this.textZoom = textZoom
            return this
        }

        fun setUserAgentSuffix(userAgentSuffix: String?): Builder {
            this.userAgentSuffix = if (userAgentSuffix == null) "" else userAgentSuffix.trim { it <= ' ' }
            return this
        }

        fun build(): WebOptions {
            return WebOptions(this)
        }
    }

    companion object {
        private val DEFAULTS: WebOptions = Builder().build()

        @JvmStatic
        fun defaults(): WebOptions {
            return DEFAULTS
        }

        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }
}
