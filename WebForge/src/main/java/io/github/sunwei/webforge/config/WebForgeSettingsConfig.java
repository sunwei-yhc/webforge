
package io.github.sunwei.webforge.config;

import android.webkit.WebSettings;

/**
 * Default WebView settings used by WebForge.
 *
 * <p>The defaults are tuned for a normal mobile H5 application. A business can
 * still override any option through {@link AbsWebForgeSettings#setSettingsConfig}
 * or by supplying a complete {@link IWebForgeSettings} implementation.</p>
 */
public final class WebForgeSettingsConfig {

    private final boolean javaScriptEnabled;
    private final boolean domStorageEnabled;
    private final boolean databaseEnabled;
    private final boolean geolocationEnabled;
    private final boolean fileAccessEnabled;
    private final boolean contentAccessEnabled;
    private final boolean fileUrlCrossOriginAccessEnabled;
    private final boolean responsiveLayout;
    private final boolean zoomEnabled;
    private final boolean mediaPlaybackRequiresUserGesture;
    private final boolean thirdPartyCookiesEnabled;
    private final boolean safeBrowsingEnabled;
    private final boolean supportMultipleWindows;
    private final int mixedContentMode;
    private final int cacheMode;
    private final int textZoom;
    private final String userAgentSuffix;

    private WebForgeSettingsConfig(Builder builder) {
        javaScriptEnabled = builder.javaScriptEnabled;
        domStorageEnabled = builder.domStorageEnabled;
        databaseEnabled = builder.databaseEnabled;
        geolocationEnabled = builder.geolocationEnabled;
        fileAccessEnabled = builder.fileAccessEnabled;
        contentAccessEnabled = builder.contentAccessEnabled;
        fileUrlCrossOriginAccessEnabled = builder.fileUrlCrossOriginAccessEnabled;
        responsiveLayout = builder.responsiveLayout;
        zoomEnabled = builder.zoomEnabled;
        mediaPlaybackRequiresUserGesture = builder.mediaPlaybackRequiresUserGesture;
        thirdPartyCookiesEnabled = builder.thirdPartyCookiesEnabled;
        safeBrowsingEnabled = builder.safeBrowsingEnabled;
        supportMultipleWindows = builder.supportMultipleWindows;
        mixedContentMode = builder.mixedContentMode;
        cacheMode = builder.cacheMode;
        textZoom = builder.textZoom;
        userAgentSuffix = builder.userAgentSuffix;
    }

    public static WebForgeSettingsConfig defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isJavaScriptEnabled() {
        return javaScriptEnabled;
    }

    public boolean isDomStorageEnabled() {
        return domStorageEnabled;
    }

    public boolean isDatabaseEnabled() {
        return databaseEnabled;
    }

    public boolean isGeolocationEnabled() {
        return geolocationEnabled;
    }

    public boolean isFileAccessEnabled() {
        return fileAccessEnabled;
    }

    public boolean isContentAccessEnabled() {
        return contentAccessEnabled;
    }

    public boolean isFileUrlCrossOriginAccessEnabled() {
        return fileUrlCrossOriginAccessEnabled;
    }

    public boolean isResponsiveLayout() {
        return responsiveLayout;
    }

    public boolean isZoomEnabled() {
        return zoomEnabled;
    }

    public boolean isMediaPlaybackRequiresUserGesture() {
        return mediaPlaybackRequiresUserGesture;
    }

    public boolean isThirdPartyCookiesEnabled() {
        return thirdPartyCookiesEnabled;
    }

    public boolean isSafeBrowsingEnabled() {
        return safeBrowsingEnabled;
    }

    public boolean isSupportMultipleWindows() {
        return supportMultipleWindows;
    }

    public int getMixedContentMode() {
        return mixedContentMode;
    }

    public int getCacheMode() {
        return cacheMode;
    }

    public int getTextZoom() {
        return textZoom;
    }

    public String getUserAgentSuffix() {
        return userAgentSuffix;
    }

    public static final class Builder {
        private boolean javaScriptEnabled = true;
        private boolean domStorageEnabled = true;
        private boolean databaseEnabled = true;
        private boolean geolocationEnabled = true;
        private boolean fileAccessEnabled = true;
        private boolean contentAccessEnabled = true;
        private boolean fileUrlCrossOriginAccessEnabled = false;
        private boolean responsiveLayout = true;
        private boolean zoomEnabled = false;
        private boolean mediaPlaybackRequiresUserGesture = false;
        private boolean thirdPartyCookiesEnabled = true;
        private boolean safeBrowsingEnabled = true;
        private boolean supportMultipleWindows = true;
        private int mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE;
        private int cacheMode = WebSettings.LOAD_DEFAULT;
        private int textZoom = 100;
        private String userAgentSuffix = "";

        public Builder setJavaScriptEnabled(boolean enabled) {
            javaScriptEnabled = enabled;
            return this;
        }

        public Builder setDomStorageEnabled(boolean enabled) {
            domStorageEnabled = enabled;
            return this;
        }

        public Builder setDatabaseEnabled(boolean enabled) {
            databaseEnabled = enabled;
            return this;
        }

        public Builder setGeolocationEnabled(boolean enabled) {
            geolocationEnabled = enabled;
            return this;
        }

        public Builder setFileAccessEnabled(boolean enabled) {
            fileAccessEnabled = enabled;
            return this;
        }

        public Builder setContentAccessEnabled(boolean enabled) {
            contentAccessEnabled = enabled;
            return this;
        }

        /**
         * Enables file:// pages to read other file:// resources. Keep this off
         * unless the application is loading all of its content from a trusted
         * local directory.
         */
        public Builder setFileUrlCrossOriginAccessEnabled(boolean enabled) {
            fileUrlCrossOriginAccessEnabled = enabled;
            return this;
        }

        public Builder setResponsiveLayout(boolean enabled) {
            responsiveLayout = enabled;
            return this;
        }

        public Builder setZoomEnabled(boolean enabled) {
            zoomEnabled = enabled;
            return this;
        }

        public Builder setMediaPlaybackRequiresUserGesture(boolean required) {
            mediaPlaybackRequiresUserGesture = required;
            return this;
        }

        public Builder setThirdPartyCookiesEnabled(boolean enabled) {
            thirdPartyCookiesEnabled = enabled;
            return this;
        }

        public Builder setSafeBrowsingEnabled(boolean enabled) {
            safeBrowsingEnabled = enabled;
            return this;
        }

        public Builder setSupportMultipleWindows(boolean enabled) {
            supportMultipleWindows = enabled;
            return this;
        }

        public Builder setMixedContentMode(int mixedContentMode) {
            this.mixedContentMode = mixedContentMode;
            return this;
        }

        public Builder setCacheMode(int cacheMode) {
            this.cacheMode = cacheMode;
            return this;
        }

        public Builder setTextZoom(int textZoom) {
            this.textZoom = textZoom;
            return this;
        }

        public Builder setUserAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = userAgentSuffix == null ? "" : userAgentSuffix.trim();
            return this;
        }

        public WebForgeSettingsConfig build() {
            return new WebForgeSettingsConfig(this);
        }
    }
}
