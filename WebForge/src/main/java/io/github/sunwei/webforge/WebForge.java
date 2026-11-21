package io.github.sunwei.webforge;

import android.app.Activity;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.fragment.app.Fragment;

import io.github.sunwei.webforge.bridge.FileUploadJsBridge;
import io.github.sunwei.webforge.bridge.JsAccessEntrace;
import io.github.sunwei.webforge.bridge.JsInterfaceHolder;
import io.github.sunwei.webforge.bridge.WebViewJsExecutor;
import io.github.sunwei.webforge.bridge.WebViewJsInterfaces;
import io.github.sunwei.webforge.client.DefaultChromeClient;
import io.github.sunwei.webforge.client.DefaultWebClient;
import io.github.sunwei.webforge.client.MiddlewareWebChromeBase;
import io.github.sunwei.webforge.client.MiddlewareWebClientBase;
import io.github.sunwei.webforge.config.AbsWebForgeSettings;
import io.github.sunwei.webforge.config.DefaultWebForgeSettings;
import io.github.sunwei.webforge.config.IWebForgeSettings;
import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.config.WebForgeSettingsConfig;
import io.github.sunwei.webforge.config.WebListenerManager;
import io.github.sunwei.webforge.core.DefaultWebCreator;
import io.github.sunwei.webforge.core.HttpHeaders;
import io.github.sunwei.webforge.core.IEventHandler;
import io.github.sunwei.webforge.core.IUrlLoader;
import io.github.sunwei.webforge.core.WebBackHandler;
import io.github.sunwei.webforge.core.WebCreator;
import io.github.sunwei.webforge.core.WebLifeCycle;
import io.github.sunwei.webforge.core.WebViewLifecycle;
import io.github.sunwei.webforge.core.WebViewTaskDispatcher;
import io.github.sunwei.webforge.core.WebViewUrlLoader;
import io.github.sunwei.webforge.permission.PermissionInterceptor;
import io.github.sunwei.webforge.security.LegacyWebViewSecurity;
import io.github.sunwei.webforge.ui.IVideo;
import io.github.sunwei.webforge.ui.IWebLayout;
import io.github.sunwei.webforge.ui.VideoImpl;
import io.github.sunwei.webforge.ui.WebForgeUIControllerImplBase;
import io.github.sunwei.webforge.ui.WebParentLayout;
import io.github.sunwei.webforge.ui.indicator.BaseIndicatorView;
import io.github.sunwei.webforge.ui.indicator.IndicatorController;
import io.github.sunwei.webforge.ui.indicator.IndicatorHandler;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.lang.ref.WeakReference;
import java.util.Map;

/** 组装和管理一个 WebView 页面。 */
public final class WebForge {
    private final Activity mActivity;
    private final WebCreator mWebCreator;
    private final WebViewTaskDispatcher mTaskDispatcher;
    private final WebLifeCycle mWebLifeCycle;
    private final IUrlLoader mUrlLoader;
    private final JsAccessEntrace mJsAccessEntrace;
    private final ArrayMap<String, Object> mJavaObjects = new ArrayMap<>();
    private final SecurityType mSecurityType;
    private final PermissionInterceptor mPermissionInterceptor;
    private final VideoImpl mVideo;
    private final boolean mWebClientHelper;
    private final boolean mInterceptUnknownUrl;
    private final boolean mZoomEnabled;
    private final int mUrlHandleWays;
    private final io.github.sunwei.webforge.client.WebViewClient mCustomWebClient;
    private final io.github.sunwei.webforge.client.WebChromeClient mCustomChromeClient;
    private final MiddlewareWebClientBase mWebClientMiddleware;
    private final MiddlewareWebChromeBase mChromeClientMiddleware;
    private final WebForgeSettingsConfig mWebForgeSettingsConfig;
    private IWebForgeSettings mWebForgeSettings;
    private IEventHandler mEventHandler;
    private IndicatorController mIndicatorController;
    private JsInterfaceHolder mJsInterfaceHolder;
    private DefaultChromeClient mDefaultChromeClient;

    private WebForge(WebForgeBuilder builder) {
        mActivity = builder.mActivity;
        mWebForgeSettings = builder.mWebForgeSettings;
        mWebForgeSettingsConfig = builder.mWebForgeSettingsConfig;
        mEventHandler = builder.mIEventHandler;
        mSecurityType = builder.mSecurityType;
        mPermissionInterceptor = builder.mPermissionInterceptor == null
                ? null : new PermissionInterceptorWrapper(builder.mPermissionInterceptor);
        mWebClientHelper = builder.mWebClientHelper;
        mInterceptUnknownUrl = builder.mInterceptUnknownUrl;
        mZoomEnabled = builder.isScale;
        mUrlHandleWays = builder.mOpenOtherPage == null ? -1 : builder.mOpenOtherPage.getCode();

        mCustomWebClient = builder.mWebViewClient;
        mCustomChromeClient = builder.mWebChromeClient;
        mWebClientMiddleware = builder.mMiddlewareWebClientBaseHeader;
        mChromeClientMiddleware = builder.mChromeMiddleWareHeader;

        mWebCreator = createWebCreator(builder).create();
        WebView webView = mWebCreator.getWebView();
        mTaskDispatcher = new WebViewTaskDispatcher(webView);
        mUrlLoader = new WebViewUrlLoader(mTaskDispatcher, builder.mHttpHeaders);
        mJsAccessEntrace = new WebViewJsExecutor(mTaskDispatcher);
        mVideo = new VideoImpl(mActivity, webView, builder.mLandscapeFullscreenVideo);
        mWebLifeCycle = new WebViewLifecycle(mTaskDispatcher, this::releasePage);
        configureParentLayout(builder);

        if (builder.mJavaObject != null) {
            mJavaObjects.putAll(builder.mJavaObject);
        }
        mJavaObjects.put("webForge", new FileUploadJsBridge(this, mActivity));
        LegacyWebViewSecurity.removeBuiltInJsInterfaces(webView);
    }

    private static WebCreator createWebCreator(WebForgeBuilder builder) {
        if (!builder.mEnableIndicator) {
            return new DefaultWebCreator(builder.mActivity, builder.mViewGroup,
                    builder.mLayoutParams, builder.mIndex, builder.mWebView, builder.mWebLayout);
        }
        if (builder.mBaseIndicatorView != null) {
            return new DefaultWebCreator(builder.mActivity, builder.mViewGroup,
                    builder.mLayoutParams, builder.mIndex, builder.mBaseIndicatorView,
                    builder.mWebView, builder.mWebLayout);
        }
        return new DefaultWebCreator(builder.mActivity, builder.mViewGroup,
                builder.mLayoutParams, builder.mIndex, builder.mIndicatorColor, builder.mHeight,
                builder.mWebView, builder.mWebLayout);
    }

    private void configureParentLayout(WebForgeBuilder builder) {
        if (mWebCreator.getWebParentLayout() instanceof WebParentLayout) {
            WebParentLayout layout = (WebParentLayout) mWebCreator.getWebParentLayout();
            layout.bindController(builder.mWebForgeUIController == null
                    ? WebForgeUIControllerImplBase.build() : builder.mWebForgeUIController);
            layout.setErrorLayoutRes(builder.mErrorLayout, builder.mReloadId);
            layout.setErrorView(builder.mErrorView);
        }
    }

    private void releasePage() {
        mJavaObjects.clear();
        if (mDefaultChromeClient != null) {
            mDefaultChromeClient.destroy();
        }
        mVideo.onHideCustomView();
    }

    /**
     * @return PermissionInterceptor 权限控制者
     */
    public PermissionInterceptor getPermissionInterceptor() {
        return this.mPermissionInterceptor;
    }

    public WebLifeCycle getWebLifeCycle() {
        return this.mWebLifeCycle;
    }

    public JsAccessEntrace getJsAccessEntrace() {
        return mJsAccessEntrace;
    }

    public WebForge clearWebCache() {
        WebView webView = mWebCreator.getWebView();
        if (webView == null) {
            WebForgeUtils.clearWebViewAllCache(mActivity);
        } else {
            WebForgeUtils.clearWebViewAllCache(mActivity, webView);
        }
        return this;
    }

    public static WebForgeBuilder with(@NonNull Activity activity) {
        if (activity == null) {
            throw new NullPointerException("activity can not be null .");
        }
        return new WebForgeBuilder(activity);
    }

    public static WebForgeBuilder with(@NonNull Fragment fragment) {
        Activity activity = fragment.getActivity();
        if (activity == null) {
            throw new NullPointerException("Fragment must be attached to an Activity");
        }
        return new WebForgeBuilder(activity, fragment);
    }

    public boolean handleKeyEvent(int keyCode, KeyEvent keyEvent) {
        return !mTaskDispatcher.isDestroyed() && getIEventHandler().onKeyDown(keyCode, keyEvent);
    }

    public boolean back() {
        return !mTaskDispatcher.isDestroyed() && getIEventHandler().back();
    }

    public WebCreator getWebCreator() {
        return this.mWebCreator;
    }

    public IEventHandler getIEventHandler() {
        if (mEventHandler == null) {
            mEventHandler = new WebBackHandler(mWebCreator.getWebView(), mVideo);
        }
        return mEventHandler;
    }


    public IWebForgeSettings getWebForgeSettings() {
        return this.mWebForgeSettings;
    }

    public IndicatorController getIndicatorController() {
        return this.mIndicatorController;
    }

    public JsInterfaceHolder getJsInterfaceHolder() {
        return this.mJsInterfaceHolder;
    }

    public IUrlLoader getUrlLoader() {
        return mUrlLoader;
    }

    public void destroy() {
        this.mWebLifeCycle.onDestroy();
    }

    public static class PreWebForge {
        private final WebForge mWebForge;
        private boolean isReady = false;

        PreWebForge(WebForge webForge) {
            this.mWebForge = webForge;
        }

        public PreWebForge ready() {
            if (!isReady) {
                mWebForge.ready();
                isReady = true;
            }
            return this;
        }

        public WebForge get() {
            ready();
            return mWebForge;
        }

        public WebForge go(@Nullable String url) {
            ready();
            return mWebForge.go(url);
        }
    }

    private WebForge go(String url) {
        if (!TextUtils.isEmpty(url) && !mTaskDispatcher.isDestroyed()) {
            mUrlLoader.loadUrl(url);
            if (mIndicatorController != null) {
                mIndicatorController.showIndicator();
            }
        }
        return this;
    }

    public IVideo getIVideo() {
        return mVideo;
    }

    private WebViewClient createWebViewClient(WebView webView) {
        DefaultWebClient defaultClient = DefaultWebClient.createBuilder()
                .setActivity(mActivity)
                .setWebClientHelper(mWebClientHelper)
                .setWebView(webView)
                .setInterceptUnknownUrl(mInterceptUnknownUrl)
                .setUrlHandleWays(mUrlHandleWays)
                .build();
        MiddlewareWebClientBase head = mWebClientMiddleware;
        if (mCustomWebClient != null) {
            mCustomWebClient.enq(head);
            head = mCustomWebClient;
        }
        if (head == null) {
            return defaultClient;
        }
        MiddlewareWebClientBase tail = head;
        while (tail.next() != null) {
            tail = tail.next();
        }
        tail.setDelegate(defaultClient);
        return head;
    }

    private WebChromeClient createChromeClient(WebView webView) {
        mIndicatorController = IndicatorHandler.getInstance().inJectIndicator(mWebCreator.offer());
        mDefaultChromeClient = new DefaultChromeClient(mActivity, mIndicatorController,
                null, mVideo, mPermissionInterceptor, webView);
        MiddlewareWebChromeBase head = mChromeClientMiddleware;
        if (mCustomChromeClient != null) {
            mCustomChromeClient.enq(head);
            head = mCustomChromeClient;
        }
        if (head == null) {
            return mDefaultChromeClient;
        }
        MiddlewareWebChromeBase tail = head;
        while (tail.next() != null) {
            tail = tail.next();
        }
        tail.setDelegate(mDefaultChromeClient);
        return head;
    }

    private void ready() {
        if (mTaskDispatcher.isDestroyed()) {
            return;
        }
        WebForgeConfig.initCookiesManager(mActivity.getApplicationContext());
        if (mWebForgeSettings == null) {
            mWebForgeSettings = new DefaultWebForgeSettings();
        }
        if (mWebForgeSettings instanceof AbsWebForgeSettings) {
            AbsWebForgeSettings settings = (AbsWebForgeSettings) mWebForgeSettings;
            if (mWebForgeSettingsConfig != null) {
                settings.setSettingsConfig(mWebForgeSettingsConfig);
            }
            settings.bindWebForge(this);
        }
        WebView webView = mWebCreator.getWebView();
        mWebForgeSettings.setScale(mZoomEnabled);
        mWebForgeSettings.toSetting(webView);
        mJsInterfaceHolder = new WebViewJsInterfaces(mWebCreator, mSecurityType);
        mJsInterfaceHolder.addJavaObjects(mJavaObjects);
        if (mWebForgeSettings instanceof AbsWebForgeSettings) {
            WebListenerManager listeners = (AbsWebForgeSettings) mWebForgeSettings;
            listeners.setDownloader(webView, null);
            listeners.setWebChromeClient(webView, createChromeClient(webView));
            listeners.setWebViewClient(webView, createWebViewClient(webView));
        }
    }

    public enum SecurityType {
        DEFAULT_CHECK, STRICT_CHECK;
    }

    public static final class WebForgeBuilder {
        private final Activity mActivity;
        private final boolean mFragmentHost;
        private ViewGroup mViewGroup;
        private int mIndex = -1;
        private BaseIndicatorView mBaseIndicatorView;
        /*默认进度条是显示的*/
        private boolean mEnableIndicator = true;
        private ViewGroup.LayoutParams mLayoutParams = null;
        private io.github.sunwei.webforge.client.WebViewClient mWebViewClient;
        private io.github.sunwei.webforge.client.WebChromeClient mWebChromeClient;
        private int mIndicatorColor = -1;
        private IWebForgeSettings mWebForgeSettings;
        private WebForgeSettingsConfig mWebForgeSettingsConfig;
        private HttpHeaders mHttpHeaders = null;
        private IEventHandler mIEventHandler;
        private int mHeight = -1;
        private Map<String, Object> mJavaObject;
        private SecurityType mSecurityType = SecurityType.DEFAULT_CHECK;
        private WebView mWebView;
        private boolean mWebClientHelper = true;
        private IWebLayout mWebLayout = null;
        private PermissionInterceptor mPermissionInterceptor = null;
        private WebForgeUIControllerImplBase mWebForgeUIController;
        private DefaultWebClient.OpenOtherPageWays mOpenOtherPage = null;
        private boolean mInterceptUnknownUrl = false;
        private boolean isScale = false;//是否可用缩放
        private boolean mLandscapeFullscreenVideo = true;
        private MiddlewareWebClientBase mMiddlewareWebClientBaseHeader;
        private MiddlewareWebClientBase mMiddlewareWebClientBaseTail;
        private MiddlewareWebChromeBase mChromeMiddleWareHeader = null;
        private MiddlewareWebChromeBase mChromeMiddleWareTail = null;
        private View mErrorView;
        private int mErrorLayout;
        private int mReloadId;

        public WebForgeBuilder(@NonNull Activity activity, @NonNull Fragment fragment) {
            mActivity = activity;
            mFragmentHost = true;
        }

        public WebForgeBuilder(@NonNull Activity activity) {
            mActivity = activity;
            mFragmentHost = false;
        }


        public IndicatorBuilder setWebForgeParent(@NonNull ViewGroup v, @NonNull ViewGroup.LayoutParams lp) {
            this.mViewGroup = v;
            this.mLayoutParams = lp;
            return new IndicatorBuilder(this);
        }

        public IndicatorBuilder setWebForgeParent(@NonNull ViewGroup v, int index, @NonNull ViewGroup.LayoutParams lp) {
            this.mViewGroup = v;
            this.mLayoutParams = lp;
            this.mIndex = index;
            return new IndicatorBuilder(this);
        }

        private PreWebForge buildWebForge() {
            if (mFragmentHost && this.mViewGroup == null) {
                throw new NullPointerException("ViewGroup is null,Please check your parameters .");
            }
            return new PreWebForge(new WebForge(this));
        }

        private void addJavaObject(String key, Object o) {
            if (mJavaObject == null) {
                mJavaObject = new ArrayMap<>();
            }
            mJavaObject.put(key, o);
        }

        private void addHeader(String baseUrl, String k, String v) {
            if (mHttpHeaders == null) {
                mHttpHeaders = HttpHeaders.create();
            }
            mHttpHeaders.additionalHttpHeader(baseUrl, k, v);
        }

        private void addHeader(String baseUrl, Map<String, String> headers) {
            if (mHttpHeaders == null) {
                mHttpHeaders = HttpHeaders.create();
            }
            mHttpHeaders.additionalHttpHeaders(baseUrl, headers);
        }
    }

    public static class IndicatorBuilder {
        private final WebForgeBuilder mWebForgeBuilder;

        public IndicatorBuilder(WebForgeBuilder webForgeBuilder) {
            this.mWebForgeBuilder = webForgeBuilder;
        }

        public CommonBuilder useDefaultIndicator(int color) {
            this.mWebForgeBuilder.mEnableIndicator = true;
            this.mWebForgeBuilder.mIndicatorColor = color;
            return new CommonBuilder(mWebForgeBuilder);
        }

        public CommonBuilder useDefaultIndicator() {
            this.mWebForgeBuilder.mEnableIndicator = true;
            return new CommonBuilder(mWebForgeBuilder);
        }

        public CommonBuilder closeIndicator() {
            this.mWebForgeBuilder.mEnableIndicator = false;
            this.mWebForgeBuilder.mIndicatorColor = -1;
            this.mWebForgeBuilder.mHeight = -1;
            return new CommonBuilder(mWebForgeBuilder);
        }

        public CommonBuilder setCustomIndicator(@NonNull BaseIndicatorView v) {
            this.mWebForgeBuilder.mEnableIndicator = true;
            if (v != null) {
                this.mWebForgeBuilder.mBaseIndicatorView = v;
            }
            return new CommonBuilder(mWebForgeBuilder);
        }

        public CommonBuilder useDefaultIndicator(@ColorInt int color, int height_dp) {
            this.mWebForgeBuilder.mIndicatorColor = color;
            this.mWebForgeBuilder.mHeight = height_dp;
            return new CommonBuilder(this.mWebForgeBuilder);
        }
    }

    public static class CommonBuilder {
        private final WebForgeBuilder mWebForgeBuilder;

        public CommonBuilder(WebForgeBuilder webForgeBuilder) {
            this.mWebForgeBuilder = webForgeBuilder;
        }

        public CommonBuilder setEventHandler(@Nullable IEventHandler eventHandler) {
            mWebForgeBuilder.mIEventHandler = eventHandler;
            return this;
        }

        /** @deprecated Use {@link #setEventHandler(IEventHandler)}. */
        @Deprecated
        public CommonBuilder setEventHanadler(@Nullable IEventHandler eventHandler) {
            return setEventHandler(eventHandler);
        }

        public CommonBuilder closeWebViewClientHelper() {
            mWebForgeBuilder.mWebClientHelper = false;
            return this;
        }

        public CommonBuilder setWebChromeClient(@Nullable io.github.sunwei.webforge.client.WebChromeClient webChromeClient) {
            this.mWebForgeBuilder.mWebChromeClient = webChromeClient;
            return this;
        }

        public CommonBuilder setWebViewClient(@Nullable io.github.sunwei.webforge.client.WebViewClient webViewClient) {
            this.mWebForgeBuilder.mWebViewClient = webViewClient;
            return this;
        }

        public CommonBuilder useMiddlewareWebClient(@NonNull MiddlewareWebClientBase middlewareWebClient) {
            if (middlewareWebClient == null) {
                return this;
            }
            if (this.mWebForgeBuilder.mMiddlewareWebClientBaseHeader == null) {
                this.mWebForgeBuilder.mMiddlewareWebClientBaseHeader = this.mWebForgeBuilder.mMiddlewareWebClientBaseTail = middlewareWebClient;
            } else {
                this.mWebForgeBuilder.mMiddlewareWebClientBaseTail.enq(middlewareWebClient);
                this.mWebForgeBuilder.mMiddlewareWebClientBaseTail = middlewareWebClient;
            }
            return this;
        }

        public CommonBuilder useMiddlewareWebChrome(@NonNull MiddlewareWebChromeBase middlewareWebChromeBase) {
            if (middlewareWebChromeBase == null) {
                return this;
            }
            if (this.mWebForgeBuilder.mChromeMiddleWareHeader == null) {
                this.mWebForgeBuilder.mChromeMiddleWareHeader = this.mWebForgeBuilder.mChromeMiddleWareTail = middlewareWebChromeBase;
            } else {
                this.mWebForgeBuilder.mChromeMiddleWareTail.enq(middlewareWebChromeBase);
                this.mWebForgeBuilder.mChromeMiddleWareTail = middlewareWebChromeBase;
            }
            return this;
        }

        public CommonBuilder setMainFrameErrorView(@NonNull View view) {
            this.mWebForgeBuilder.mErrorView = view;
            return this;
        }

        public CommonBuilder setMainFrameErrorView(@LayoutRes int errorLayout, @IdRes int clickViewId) {
            this.mWebForgeBuilder.mErrorLayout = errorLayout;
            this.mWebForgeBuilder.mReloadId = clickViewId;
            return this;
        }

        public CommonBuilder setWebForgeWebSettings(@Nullable IWebForgeSettings webForgeSettings) {
            this.mWebForgeBuilder.mWebForgeSettings = webForgeSettings;
            return this;
        }

        /**
         * Applies the built-in WebForge WebSettings defaults. This option has
         * no effect when a fully custom WebSettings implementation is used.
         */
        public CommonBuilder setWebForgeSettingsConfig(
                @Nullable WebForgeSettingsConfig settingsConfig) {
            this.mWebForgeBuilder.mWebForgeSettingsConfig = settingsConfig;
            return this;
        }

        public PreWebForge createWebForge() {
            return this.mWebForgeBuilder.buildWebForge();
        }


        public CommonBuilder addJavascriptInterface(@NonNull String name, @NonNull Object o) {
            this.mWebForgeBuilder.addJavaObject(name, o);
            return this;
        }

        public CommonBuilder setSecurityType(@NonNull SecurityType type) {
            this.mWebForgeBuilder.mSecurityType = type;
            return this;
        }

        public CommonBuilder setWebView(@Nullable WebView webView) {
            this.mWebForgeBuilder.mWebView = webView;
            return this;
        }

        public CommonBuilder setWebLayout(@Nullable IWebLayout iWebLayout) {
            this.mWebForgeBuilder.mWebLayout = iWebLayout;
            return this;
        }

        public CommonBuilder additionalHttpHeader(String baseUrl, String k, String v) {
            this.mWebForgeBuilder.addHeader(baseUrl, k, v);
            return this;
        }

        public CommonBuilder additionalHttpHeader(String baseUrl, Map<String, String> headers) {
            this.mWebForgeBuilder.addHeader(baseUrl, headers);
            return this;
        }

        public CommonBuilder setPermissionInterceptor(@Nullable PermissionInterceptor permissionInterceptor) {
            this.mWebForgeBuilder.mPermissionInterceptor = permissionInterceptor;
            return this;
        }

        public CommonBuilder setWebForgeUIController(@Nullable WebForgeUIControllerImplBase webForgeUIController) {
            this.mWebForgeBuilder.mWebForgeUIController = webForgeUIController;
            return this;
        }

        public CommonBuilder setOpenOtherPageWays(@Nullable DefaultWebClient.OpenOtherPageWays openOtherPageWays) {
            this.mWebForgeBuilder.mOpenOtherPage = openOtherPageWays;
            return this;
        }

        //是否可用缩放
        public CommonBuilder isScale(boolean isScale) {
            this.mWebForgeBuilder.isScale = isScale;
            return this;
        }
        public CommonBuilder setLandscapeFullscreenVideo(boolean enabled) {
            this.mWebForgeBuilder.mLandscapeFullscreenVideo = enabled;
            return this;
        }

        public CommonBuilder setInterceptUnknownUrl(boolean enabled) {
            this.mWebForgeBuilder.mInterceptUnknownUrl = enabled;
            return this;
        }

        /** @deprecated Use {@link #setLandscapeFullscreenVideo(boolean)}. */
        @Deprecated
        public CommonBuilder isisLandscapeScreen(boolean enabled) {
            return setLandscapeFullscreenVideo(enabled);
        }

        /** @deprecated Use {@link #setInterceptUnknownUrl(boolean)}. */
        @Deprecated
        public CommonBuilder isInterceptUnkownUrl(boolean enabled) {
            return setInterceptUnknownUrl(enabled);
        }

        /** @deprecated Use {@link #setInterceptUnknownUrl(boolean)}. */
        @Deprecated
        public CommonBuilder interceptUnkownUrl() {
            return setInterceptUnknownUrl(true);
        }
    }

    public Activity getActivity() {
        return this.mActivity;
    }

    private static final class PermissionInterceptorWrapper implements PermissionInterceptor {

        private final WeakReference<PermissionInterceptor> mWeakReference;

        private PermissionInterceptorWrapper(PermissionInterceptor permissionInterceptor) {
            this.mWeakReference = new WeakReference<>(permissionInterceptor);
        }

        @Override
        public boolean intercept(String url, String[] permissions, String a) {
            PermissionInterceptor interceptor = mWeakReference.get();
            return interceptor != null && interceptor.intercept(url, permissions, a);
        }
    }
}
