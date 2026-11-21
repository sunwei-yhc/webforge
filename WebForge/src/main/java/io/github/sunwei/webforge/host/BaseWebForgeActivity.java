package io.github.sunwei.webforge.host;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.ColorInt;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
 * @Author 萤火虫
 * @Date 2021/7/23
 * @Desc webview基类
 */
public abstract class BaseWebForgeActivity extends AppCompatActivity {

    protected WebForge mWebForge;
    private ErrorLayoutEntity mErrorLayoutEntity;
    private MiddlewareWebChromeBase mMiddleWareWebChrome;
    private MiddlewareWebClientBase mMiddleWareWebClient;

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
        buildWebForge();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        buildWebForge();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        buildWebForge();
    }

    protected void buildWebForge() {
        // 替换布局时先释放旧页面，避免重复持有 WebView。
        if (mWebForge != null) {
            mWebForge.destroy();
            mWebForge = null;
        }
        ErrorLayoutEntity mErrorLayoutEntity = getErrorLayoutEntity();
        WebForge.PreWebForge preWebForge = WebForge.with(this)
                .setWebForgeParent(getWebForgeParent(), new ViewGroup.LayoutParams(-1, -1))
                .useDefaultIndicator(getIndicatorColor(), getIndicatorHeight())
                .setWebChromeClient(getWebChromeClient())
                .setWebViewClient(getWebViewClient())
                .setWebView(getWebView())
                .setPermissionInterceptor(getPermissionInterceptor())
                .setWebLayout(getWebLayout())
                .setWebForgeUIController(getWebForgeUIController())
                .isScale(false)
                .setOpenOtherPageWays(getOpenOtherAppWay())
                .useMiddlewareWebChrome(getMiddleWareWebChrome())
                .useMiddlewareWebClient(getMiddleWareWebClient())
                .setWebForgeWebSettings(getWebForgeSettings())
                .setMainFrameErrorView(mErrorLayoutEntity.layoutRes, mErrorLayoutEntity.reloadId)
                .setSecurityType(WebForge.SecurityType.STRICT_CHECK)
                .createWebForge()
                .ready();
        mWebForge = preWebForge.get();
        Object androidInterface = getAndroidInterface();
        if (androidInterface != null) {
            mWebForge.getJsInterfaceHolder().addJavaObject("Android", androidInterface);
        }
        preWebForge.go(getUrl());
    }


    @NonNull
    protected ErrorLayoutEntity getErrorLayoutEntity() {
        if (this.mErrorLayoutEntity == null) {
            this.mErrorLayoutEntity = new ErrorLayoutEntity();
        }
        return mErrorLayoutEntity;
    }

    protected WebForge getWebForge() {
        return this.mWebForge;
    }

    /** 返回默认 H5 桥接对象；宿主自行注入时可返回 null。 */
    @Nullable
    protected Object getAndroidInterface() {
        return new AndroidInterface(mWebForge, this);
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (mWebForge != null && mWebForge.handleKeyEvent(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        if (mWebForge != null) {
            mWebForge.getWebLifeCycle().onPause();
        }
        super.onPause();

    }

    @Override
    protected void onResume() {
        if (mWebForge != null) {
            mWebForge.getWebLifeCycle().onResume();
        }
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        if (mWebForge != null) {
            mWebForge.destroy();
            mWebForge = null;
        }
        super.onDestroy();
    }


    /**
     * 必须要子类必须要实现并给值
     *
     * @return
     */
    @Nullable
    protected String getUrl() {
        return null;
    }

    @Nullable
    public IWebForgeSettings getWebForgeSettings() {
        DefaultWebForgeSettings settings = new DefaultWebForgeSettings();
        WebForgeSettingsConfig config = getWebForgeSettingsConfig();
        if (config != null) {
            settings.setSettingsConfig(config);
        }
        return settings;
    }

    @Nullable
    protected WebForgeSettingsConfig getWebForgeSettingsConfig() {
        return null;
    }

    @NonNull
    protected abstract ViewGroup getWebForgeParent();

    @Nullable
    protected WebChromeClient getWebChromeClient() {
        return null;
    }

    @ColorInt
    protected int getIndicatorColor() {
        return -1;
    }

    protected int getIndicatorHeight() {
        return -1;
    }

    @Nullable
    protected WebViewClient getWebViewClient() {
        return null;
    }


    @Nullable
    protected WebView getWebView() {
        return null;
    }

    @Nullable
    protected IWebLayout getWebLayout() {
        return null;
    }

    @Nullable
    protected PermissionInterceptor getPermissionInterceptor() {
        return null;
    }

    @Nullable
    public WebForgeUIControllerImplBase getWebForgeUIController() {
        return null;
    }

    @Nullable
    public DefaultWebClient.OpenOtherPageWays getOpenOtherAppWay() {
        return null;
    }

    @NonNull
    protected MiddlewareWebChromeBase getMiddleWareWebChrome() {
        return this.mMiddleWareWebChrome = new MiddlewareWebChromeBase() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                setTitle(view, title);
            }
        };
    }

    protected void setTitle(WebView view, String title) {

    }

    @NonNull
    protected MiddlewareWebClientBase getMiddleWareWebClient() {
        return this.mMiddleWareWebClient = new MiddlewareWebClientBase() {
        };
    }
}
