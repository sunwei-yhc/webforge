package io.github.sunwei.webforge;

import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.activity.ComponentActivity;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import io.github.sunwei.webforge.bridge.AndroidBridge;
import io.github.sunwei.webforge.bridge.FileUploadBridge;
import io.github.sunwei.webforge.client.WebInteractionClient;
import io.github.sunwei.webforge.client.WebNavigationClient;
import io.github.sunwei.webforge.client.ChromeCallbacks;
import io.github.sunwei.webforge.client.NavigationCallbacks;
import io.github.sunwei.webforge.config.ExternalLinks;
import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.config.WebOptions;
import io.github.sunwei.webforge.download.LazyDownloadListener;
import io.github.sunwei.webforge.permission.PermissionInterceptor;
import io.github.sunwei.webforge.security.LegacyWebViewSecurity;
import io.github.sunwei.webforge.ui.FullscreenVideo;
import io.github.sunwei.webforge.ui.WebParentLayout;
import io.github.sunwei.webforge.ui.PageUiController;
import io.github.sunwei.webforge.ui.indicator.BaseIndicatorView;
import io.github.sunwei.webforge.widget.CompatibleWebView;
import io.github.sunwei.webforge.widget.LegacyBridgeWebView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 宿主管理的单个 WebView 页面。Fragment 页面随当前视图销毁。 */
public final class WebPage {
    private final PageTaskQueue queue = new PageTaskQueue();
    private Session session;

    @MainThread
    public static WebPage open(@NonNull ComponentActivity activity, @NonNull ViewGroup container,
                               @NonNull String url) {
        return builder(activity, container).open(url);
    }

    /** 从 Fragment 的 onViewCreated 起调用。 */
    @MainThread
    public static WebPage open(@NonNull Fragment fragment, @NonNull ViewGroup container,
                               @NonNull String url) {
        return builder(fragment, container).open(url);
    }

    @MainThread
    public static Builder builder(@NonNull ComponentActivity activity, @NonNull ViewGroup container) {
        return new Builder(activity, activity, container);
    }

    @MainThread
    public static Builder builder(@NonNull Fragment fragment, @NonNull ViewGroup container) {
        requireMainThread();
        return new Builder(fragment.requireActivity(), fragment.getViewLifecycleOwner(), container);
    }

    private WebPage(Builder builder) {
        WebView view = builder.view;
        if (view == null) {
            view = Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT
                    ? new LegacyBridgeWebView(builder.activity) : new CompatibleWebView(builder.activity);
        }
        session = new Session(view);
        try {
            initialize(builder);
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    private void initialize(Builder builder) {
        Session current = session;
        WebView view = current.view;
        current.root = PageLayout.create(builder.activity);
        PageLayout.setContent(current.root, builder.content, view);
        current.root.setErrorLayoutRes(builder.errorLayout, builder.retryView);
        current.root.setErrorView(builder.errorView);
        current.root.bindController(builder.uiController == null
                ? new PageUiController() : builder.uiController);
        BaseIndicatorView progress = builder.progressEnabled ? PageLayout.addProgress(
                current.root, builder.progressView, builder.progressColor, builder.progressHeight) : null;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            WebForgeConfig.initCookiesManager(builder.activity.getApplicationContext());
        }
        LegacyWebViewSecurity.removeBuiltInJsInterfaces(view);
        WebViewSettings.apply(view, builder.options);
        if (builder.settings != null) {
            builder.settings.apply(view.getSettings());
        }

        current.video = new FullscreenVideo(builder.activity, view, builder.landscapeVideo);
        current.chrome = new WebInteractionClient(builder.activity, progress,
                current.video, builder.permissionInterceptor, view);
        current.headers = builder.headers.isEmpty() ? Collections.emptyMap()
                : new HashMap<>(builder.headers);
        view.setDownloadListener(new LazyDownloadListener(builder.activity, view,
                builder.permissionInterceptor));
        installClients(current, builder);
        if (builder.builtInBridges) {
            if (!builder.bridges.containsKey("Android")) {
                PageJavaScript.register(view, "Android", new AndroidBridge(this, builder.activity));
            }
            if (!builder.bridges.containsKey("webForge")) {
                PageJavaScript.register(view, "webForge", new FileUploadBridge(
                        this, builder.activity, builder.permissionInterceptor));
            }
        }
        for (Map.Entry<String, Object> bridge : builder.bridges.entrySet()) {
            PageJavaScript.register(view, bridge.getKey(), bridge.getValue());
        }

        builder.container.addView(current.root, builder.index, builder.layoutParams);
        current.host = new PageHost(this, builder.activity, builder.owner, builder.handleBack);
        current.host.bind();
    }

    private void installClients(Session current, Builder builder) {
        Runnable listener = builder.rendererGone;
        Runnable rendererGone = () -> {
            if (session != current) {
                return;
            }
            current.rendererGone = true;
            boolean wasOpen = !isClosed();
            close();
            releaseSession();
            if (wasOpen && listener != null) {
                listener.run();
            }
        };
        android.webkit.WebViewClient navigation = new WebNavigationClient(builder.activity,
                current.view, builder.externalLinks, builder.blockUnknownSchemes, rendererGone);
        if (!builder.navigationCallbacks.isEmpty()) {
            current.navigationCallbacks = new ArrayList<>(builder.navigationCallbacks.size());
            for (int i = builder.navigationCallbacks.size() - 1; i >= 0; i--) {
                NavigationCallbacks next = builder.navigationCallbacks.get(i);
                next.connect(navigation);
                current.navigationCallbacks.add(next);
                navigation = next;
            }
        }
        if (!current.navigationCallbacks.isEmpty()) {
            NavigationCallbacks renderGuard = new NavigationCallbacks() {
                @Override
                public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                    rendererGone.run();
                    return true;
                }
            };
            renderGuard.connect(navigation);
            navigation = renderGuard;
        }
        android.webkit.WebChromeClient chrome = current.chrome;
        if (!builder.chromeCallbacks.isEmpty()) {
            current.chromeCallbacks = new ArrayList<>(builder.chromeCallbacks.size());
            for (int i = builder.chromeCallbacks.size() - 1; i >= 0; i--) {
                ChromeCallbacks next = builder.chromeCallbacks.get(i);
                next.connect(chrome);
                current.chromeCallbacks.add(next);
                chrome = next;
            }
        }
        current.view.setWebViewClient(navigation);
        current.view.setWebChromeClient(chrome);
    }

    /** 加载 URL。可从后台线程调用。 */
    public void load(@NonNull String url) {
        String target = requireUrl(url);
        run(current -> {
            Map<String, String> headers = current.headers.isEmpty() ? null
                    : current.headers.get(PageHeaders.origin(target));
            current.load(target, headers);
        });
    }

    /** 使用本次导航的请求头。 */
    public void load(@NonNull String url, @NonNull Map<String, String> headers) {
        String target = requireUrl(url);
        Map<String, String> snapshot = PageHeaders.copy(headers);
        run(current -> current.load(target, snapshot));
    }

    public void loadHtml(@NonNull String html, @Nullable String baseUrl) {
        run(current -> current.view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null));
    }

    public void post(@NonNull String url, @NonNull byte[] body) {
        String target = requireUrl(url);
        byte[] snapshot = body.clone();
        run(current -> current.view.postUrl(target, snapshot));
    }

    public void reload() {
        run(current -> current.view.reload());
    }

    public void stop() {
        run(current -> current.view.stopLoading());
    }

    public void evaluateJavaScript(@NonNull String script) {
        evaluateJavaScript(script, null);
    }

    /** API 19 起可获得 JSON 格式的结果。回调在主线程运行。 */
    public void evaluateJavaScript(@NonNull String script, @Nullable ValueCallback<String> result) {
        run(current -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                current.view.evaluateJavascript(script, result == null ? null : value -> {
                    if (!isClosed()) {
                        result.onReceiveValue(value);
                    }
                });
            } else {
                current.view.loadUrl(script.startsWith("javascript:") ? script : "javascript:" + script);
            }
        });
    }

    public void callJavaScript(@NonNull String function, Object... arguments) {
        if (!isClosed()) {
            evaluateJavaScript(PageJavaScript.call(function, arguments));
        }
    }

    /** 立即注册桥接对象。请在加载可信网页前调用。 */
    @MainThread
    public void addJavaScriptBridge(@NonNull String name, @NonNull Object bridge) {
        requireMainThread();
        run(current -> PageJavaScript.register(current.view, name, bridge));
    }

    /** 先退出全屏视频，再尝试导航到上一页。 */
    @MainThread
    public boolean goBack() {
        requireMainThread();
        if (isClosed()) {
            return false;
        }
        Session current = session;
        if (current.video.exitFullscreen()) {
            return true;
        }
        if (!current.view.canGoBack()) {
            return false;
        }
        current.view.goBack();
        return true;
    }

    /** 访问底层 WebView。页面关闭后返回 null。 */
    @MainThread
    @Nullable
    public WebView getView() {
        requireMainThread();
        return isClosed() ? null : session.view;
    }

    /** 清理应用 WebView 的缓存和 Cookie。 */
    public void clearBrowserData() {
        run(current -> {
            WebForgeConfig.removeAllCookies();
            current.view.clearCache(true);
            current.view.clearHistory();
            current.view.clearFormData();
        });
    }

    public boolean isClosed() {
        return queue.isClosed();
    }

    /** 可从任意线程调用。重复关闭安全。 */
    public void close() {
        queue.close(this::releaseSession);
    }

    private void releaseSession() {
        Session current = session;
        session = null;
        if (current != null) {
            current.release();
        }
    }

    void pause() {
        run(current -> current.view.onPause());
    }

    void resume() {
        run(current -> current.view.onResume());
    }

    private interface PageAction {
        void run(Session current);
    }

    private void run(PageAction action) {
        queue.run(() -> action.run(session));
    }

    private void closeAfterFailure(Throwable failure) {
        try {
            close();
        } catch (RuntimeException | Error cleanupFailure) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Access WebPage on the main thread");
        }
    }

    private static String requireUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("WebPage requires a non-empty URL");
        }
        return url.trim();
    }

    private static final class Session {
        final WebView view;
        WebParentLayout root;
        FullscreenVideo video;
        WebInteractionClient chrome;
        PageHost host;
        Map<String, Map<String, String>> headers;
        List<NavigationCallbacks> navigationCallbacks = Collections.emptyList();
        List<ChromeCallbacks> chromeCallbacks = Collections.emptyList();
        boolean rendererGone;

        Session(WebView view) {
            this.view = view;
        }

        void load(String url, Map<String, String> headers) {
            if (headers == null || headers.isEmpty()) {
                view.loadUrl(url);
            } else {
                view.loadUrl(url, headers);
            }
        }

        void release() {
            if (host != null) {
                host.unbind();
            }
            try {
                if (chrome != null) {
                    chrome.release(!rendererGone);
                }
            } finally {
                try {
                    if (video != null) {
                        video.release(!rendererGone);
                    }
                } finally {
                    for (NavigationCallbacks callback : navigationCallbacks) {
                        callback.disconnect();
                    }
                    for (ChromeCallbacks callback : chromeCallbacks) {
                        callback.disconnect();
                    }
                    try {
                        if (root != null) {
                            try {
                                root.release();
                            } finally {
                                if (root.getParent() instanceof ViewGroup) {
                                    ((ViewGroup) root.getParent()).removeView(root);
                                }
                            }
                        }
                    } finally {
                        releaseWebView();
                    }
                }
            }
        }

        private void releaseWebView() {
            try {
                if (view.getParent() instanceof ViewGroup) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
                if (!rendererGone) {
                    view.stopLoading();
                    view.setDownloadListener(null);
                    view.setWebChromeClient(null);
                    view.setWebViewClient(null);
                }
            } finally {
                view.destroy();
            }
        }
    }

    @FunctionalInterface
    public interface SettingsCustomizer {
        void apply(@NonNull WebSettings settings);
    }

    /** 可选配置。每个 Builder 创建一个页面。 */
    public static final class Builder {
        private final ComponentActivity activity;
        private final LifecycleOwner owner;
        private final ViewGroup container;
        private final Map<String, Object> bridges = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> headers = new HashMap<>();
        private final List<NavigationCallbacks> navigationCallbacks = new ArrayList<>();
        private final List<ChromeCallbacks> chromeCallbacks = new ArrayList<>();
        private WebOptions options = WebOptions.defaults();
        private SettingsCustomizer settings;
        private ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        private int index = -1;
        private boolean used;
        private boolean handleBack = true;
        private boolean builtInBridges;
        private boolean progressEnabled = true;
        private boolean landscapeVideo = true;
        private boolean blockUnknownSchemes;
        private ExternalLinks externalLinks = ExternalLinks.ASK;
        private WebView view;
        private ViewGroup content;
        private BaseIndicatorView progressView;
        private Integer progressColor;
        private int progressHeight;
        private PermissionInterceptor permissionInterceptor;
        private PageUiController uiController;
        private View errorView;
        private int errorLayout;
        private int retryView;
        private Runnable rendererGone;

        private Builder(ComponentActivity activity, LifecycleOwner owner, ViewGroup container) {
            requireMainThread();
            if (container == null) {
                throw new NullPointerException("WebPage container is required");
            }
            this.activity = activity;
            this.owner = owner;
            this.container = container;
        }

        public Builder options(@NonNull WebOptions options) {
            this.options = options;
            return this;
        }

        public Builder configureSettings(@NonNull SettingsCustomizer settings) {
            this.settings = settings;
            return this;
        }

        /** 使用调用方创建的 WebView。页面接管其生命周期。 */
        public Builder webView(@NonNull WebView view) {
            this.view = view;
            return this;
        }

        /** 将提供的布局作为网页内容。自定义 WebView 可以已在该布局内。 */
        public Builder content(@NonNull ViewGroup content) {
            this.content = content;
            return this;
        }

        public Builder placement(int index, @NonNull ViewGroup.LayoutParams params) {
            this.index = index;
            this.layoutParams = params;
            return this;
        }

        public Builder handleBack(boolean enabled) {
            handleBack = enabled;
            return this;
        }

        public Builder progress(boolean enabled) {
            progressEnabled = enabled;
            return this;
        }

        public Builder progress(@ColorInt int color, int heightDp) {
            progressEnabled = true;
            progressColor = color;
            progressHeight = heightDp;
            return this;
        }

        public Builder progress(@NonNull BaseIndicatorView view) {
            progressEnabled = true;
            progressView = view;
            return this;
        }

        /** 为可信 H5 暴露 window.Android 和 window.webForge。 */
        public Builder enableBuiltInBridges() {
            builtInBridges = true;
            return this;
        }

        public Builder bridge(@NonNull String name, @NonNull Object bridge) {
            bridges.put(name, bridge);
            return this;
        }

        public Builder headers(@NonNull String url, @NonNull Map<String, String> values) {
            String origin = PageHeaders.origin(url);
            if (origin == null) {
                throw new IllegalArgumentException("Expected an HTTP(S) origin");
            }
            headers.put(origin, PageHeaders.copy(values));
            return this;
        }

        public Builder navigationCallbacks(@NonNull NavigationCallbacks callbacks) {
            navigationCallbacks.add(callbacks);
            return this;
        }

        public Builder chromeCallbacks(@NonNull ChromeCallbacks callbacks) {
            chromeCallbacks.add(callbacks);
            return this;
        }

        public Builder permissionInterceptor(@Nullable PermissionInterceptor interceptor) {
            permissionInterceptor = interceptor;
            return this;
        }

        public Builder externalLinks(@NonNull ExternalLinks policy) {
            externalLinks = policy;
            return this;
        }

        public Builder blockUnknownSchemes(boolean enabled) {
            blockUnknownSchemes = enabled;
            return this;
        }

        public Builder landscapeVideo(boolean enabled) {
            landscapeVideo = enabled;
            return this;
        }

        public Builder uiController(@NonNull PageUiController controller) {
            uiController = controller;
            return this;
        }

        public Builder errorView(@NonNull View view) {
            errorView = view;
            return this;
        }

        public Builder errorLayout(@LayoutRes int layout, @IdRes int retryId) {
            errorLayout = layout;
            retryView = retryId;
            return this;
        }

        /** API 26 起在渲染器退出并清理原页面后，于主线程调用。 */
        public Builder onRendererGone(@NonNull Runnable listener) {
            rendererGone = listener;
            return this;
        }

        @MainThread
        public WebPage build() {
            requireMainThread();
            if (used) {
                throw new IllegalStateException("WebPage.Builder can only build once");
            }
            if (owner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
                throw new IllegalStateException("The WebPage host has been destroyed");
            }
            if (index < -1 || index > container.getChildCount()) {
                throw new IndexOutOfBoundsException("Invalid WebPage position: " + index);
            }
            if (content != null && (content.getParent() != null || contains(content, container))) {
                throw new IllegalArgumentException("WebPage content must be a detached layout outside the container");
            }
            if (view != null && view.getParent() != null && !contains(content, view)) {
                throw new IllegalArgumentException("WebView already belongs to another layout");
            }
            if (progressEnabled && progressView != null && progressView.getParent() != null) {
                throw new IllegalArgumentException("Progress view already belongs to another layout");
            }
            if (errorView != null && errorView.getParent() != null) {
                throw new IllegalArgumentException("Error view already belongs to another layout");
            }
            used = true;
            return new WebPage(this);
        }

        private static boolean contains(ViewGroup ancestor, View view) {
            for (View current = view; current != null; ) {
                if (current == ancestor) {
                    return true;
                }
                current = current.getParent() instanceof View ? (View) current.getParent() : null;
            }
            return false;
        }

        @MainThread
        public WebPage open(@NonNull String url) {
            String target = requireUrl(url);
            WebPage page = build();
            try {
                page.load(target);
                return page;
            } catch (RuntimeException | Error failure) {
                page.closeAfterFailure(failure);
                throw failure;
            }
        }
    }
}
