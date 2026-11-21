package io.github.sunwei.webforge.host;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.github.sunwei.webforge.WebForge;
import io.github.sunwei.webforge.bridge.AndroidInterface;
import io.github.sunwei.webforge.client.DefaultWebClient;
import io.github.sunwei.webforge.client.MiddlewareWebChromeBase;
import io.github.sunwei.webforge.client.MiddlewareWebClientBase;
import io.github.sunwei.webforge.client.WebChromeClient;
import io.github.sunwei.webforge.client.WebViewClient;
import io.github.sunwei.webforge.config.IWebForgeSettings;
import io.github.sunwei.webforge.config.WebForgeSettingsConfig;
import io.github.sunwei.webforge.config.DefaultWebForgeSettings;
import io.github.sunwei.webforge.permission.PermissionInterceptor;
import io.github.sunwei.webforge.ui.IWebLayout;
import io.github.sunwei.webforge.ui.WebForgeUIControllerImplBase;

/**
 */

public abstract class BaseWebForgeFragment extends Fragment {

    protected WebForge mWebForge;
    private MiddlewareWebChromeBase mMiddleWareWebChrome;
    private MiddlewareWebClientBase mMiddleWareWebClient;
    private ErrorLayoutEntity mErrorLayoutEntity;
    private WebForgeUIControllerImplBase mWebForgeUIController;

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ErrorLayoutEntity mErrorLayoutEntity = getErrorLayoutEntity();
        WebForge.PreWebForge preWebForge = WebForge.with(this)
                .setWebForgeParent(getWebForgeParent(), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                .useDefaultIndicator(getIndicatorColor(), getIndicatorHeight())
                .setWebView(getWebView())
                .setWebLayout(getWebLayout())
                .setWebForgeWebSettings(getWebForgeSettings())
                .setWebViewClient(getWebViewClient())
                .setPermissionInterceptor(getPermissionInterceptor())
                .setWebChromeClient(getWebChromeClient())
                .setOpenOtherPageWays(DefaultWebClient.OpenOtherPageWays.DISALLOW)
                .setSecurityType(WebForge.SecurityType.STRICT_CHECK)
                .setWebForgeUIController(getWebForgeUIController())
                .setMainFrameErrorView(mErrorLayoutEntity.layoutRes, mErrorLayoutEntity.reloadId)
                .useMiddlewareWebChrome(getMiddleWareWebChrome())
                .useMiddlewareWebClient(getMiddleWareWebClient())
                .createWebForge()//
                .ready();
        mWebForge = preWebForge.get();
        Object androidInterface = getAndroidInterface();
        if (androidInterface != null) {
            mWebForge.getJsInterfaceHolder().addJavaObject("Android", androidInterface);
        }
        preWebForge.go(getUrl());
    }


    protected void setTitle(WebView view, String title) {

    }

    /** 返回默认 H5 桥接对象；宿主自行注入时可返回 null。 */
    @Nullable
    protected Object getAndroidInterface() {
        return getActivity() == null ? null
                : new AndroidInterface(mWebForge, getActivity());
    }

    protected @NonNull
    ErrorLayoutEntity getErrorLayoutEntity() {
        if (this.mErrorLayoutEntity == null) {
            this.mErrorLayoutEntity = new ErrorLayoutEntity();
        }
        return mErrorLayoutEntity;
    }

    protected @Nullable
    WebForgeUIControllerImplBase getWebForgeUIController() {
        return mWebForgeUIController;
    }

    protected static class ErrorLayoutEntity {
        private int layoutRes = io.github.sunwei.webforge.R.layout.webforge_error_page;
        private int reloadId;

        public void setLayoutRes(int layoutRes) {
            this.layoutRes = layoutRes <= 0 ? -1 : layoutRes;
        }

        public void setReloadId(int reloadId) {
            this.reloadId = reloadId <= 0 ? -1 : reloadId;
        }
    }

    @Override
    public void onPause() {
        if (mWebForge != null) {
            mWebForge.getWebLifeCycle().onPause();
        }
        super.onPause();

    }

    @Override
    public void onResume() {
        if (mWebForge != null) {
            mWebForge.getWebLifeCycle().onResume();
        }
        super.onResume();
    }


    protected @Nullable
    String getUrl() {
        return "";
    }

    @Override
    public void onDestroyView() {
        if (mWebForge != null) {
            mWebForge.destroy();
            mWebForge = null;
        }
        // Fragment 进入返回栈时仍存活，但旧视图与其 Activity 引用必须释放。
        mMiddleWareWebChrome = null;
        mMiddleWareWebClient = null;
        mWebForgeUIController = null;
        super.onDestroyView();
    }

    protected @Nullable
    IWebForgeSettings getWebForgeSettings() {
        DefaultWebForgeSettings settings = new DefaultWebForgeSettings();
        WebForgeSettingsConfig config = getWebForgeSettingsConfig();
        if (config != null) {
            settings.setSettingsConfig(config);
        }
        return settings;
    }

    protected @Nullable
    WebForgeSettingsConfig getWebForgeSettingsConfig() {
        return null;
    }

    protected @Nullable
    WebChromeClient getWebChromeClient() {
        return null;
    }

    protected abstract @NonNull
    ViewGroup getWebForgeParent();

    protected @ColorInt
    int getIndicatorColor() {
        return -1;
    }

    protected int getIndicatorHeight() {
        return -1;
    }

    protected @Nullable
    WebViewClient getWebViewClient() {
        return null;
    }

    protected @Nullable
    WebView getWebView() {
        return null;
    }

    protected @Nullable
    IWebLayout getWebLayout() {
        return null;
    }

    protected @Nullable
    PermissionInterceptor getPermissionInterceptor() {
        return null;
    }

    protected @NonNull
    MiddlewareWebChromeBase getMiddleWareWebChrome() {
        return this.mMiddleWareWebChrome = new MiddlewareWebChromeBase() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                setTitle(view, title);
            }
        };
    }

    protected @NonNull
    MiddlewareWebClientBase getMiddleWareWebClient() {
        return this.mMiddleWareWebClient = new MiddlewareWebClientBase() {
        };
    }
}
