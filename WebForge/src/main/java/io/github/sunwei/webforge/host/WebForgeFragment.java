package io.github.sunwei.webforge.host;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sunwei.webforge.R;
import io.github.sunwei.webforge.client.MiddlewareWebChromeBase;
import io.github.sunwei.webforge.client.MiddlewareWebClientBase;
import io.github.sunwei.webforge.client.WebChromeClient;
import io.github.sunwei.webforge.client.WebViewClient;
import io.github.sunwei.webforge.config.IWebForgeSettings;
import io.github.sunwei.webforge.permission.PermissionInterceptor;

/** 带工具栏的通用网页容器，生命周期与设置复用基础 Fragment。 */
public class WebForgeFragment extends BaseWebForgeFragment implements FragmentKeyDown {
    public static final String URL_KEY = "url_key";
    public static final String TAG = WebForgeFragment.class.getSimpleName();
    private View backView;
    private View lineView;
    private TextView titleView;
    private PopupMenu popupMenu;
    protected PermissionInterceptor mPermissionInterceptor;

    public static WebForgeFragment getInstance(Bundle bundle) {
        WebForgeFragment fragment = new WebForgeFragment();
        if (bundle != null) {
            fragment.setArguments(bundle);
        }
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_webforge, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // 先绑定工具栏，首次加载的同步回调也能安全更新视图。
        initView(view);
        super.onViewCreated(view, savedInstanceState);
        mWebForge.getWebCreator().getWebView().setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    @NonNull
    @Override
    protected ViewGroup getWebForgeParent() {
        return (ViewGroup) requireView();
    }

    @Override
    public String getUrl() {
        Bundle arguments = getArguments();
        String url = arguments == null ? null : arguments.getString(URL_KEY);
        return TextUtils.isEmpty(url) ? "about:blank" : url;
    }

    public IWebForgeSettings getSettings() {
        return super.getWebForgeSettings();
    }

    @Override
    protected IWebForgeSettings getWebForgeSettings() {
        return getSettings();
    }

    @Override
    protected PermissionInterceptor getPermissionInterceptor() {
        return mPermissionInterceptor;
    }

    protected WebChromeClient mWebChromeClient = new WebChromeClient() {
        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (titleView != null) {
                titleView.setText(title);
            }
        }
    };

    protected WebViewClient mWebViewClient = new WebViewClient() {
        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            int visibility = TextUtils.equals(url, getUrl()) ? View.GONE : View.VISIBLE;
            if (backView != null) {
                backView.setVisibility(visibility);
                lineView.setVisibility(visibility);
            }
        }
    };

    @Override
    protected WebChromeClient getWebChromeClient() {
        return mWebChromeClient;
    }

    @Override
    protected WebViewClient getWebViewClient() {
        return mWebViewClient;
    }

    // 保留原有扩展入口，基础类负责将中间件接入调用链。
    protected MiddlewareWebClientBase getMiddlewareWebClient() {
        return new MiddlewareWebClientBase() { };
    }

    protected MiddlewareWebChromeBase getMiddlewareWebChrome() {
        return new MiddlewareWebChromeBase() { };
    }

    @NonNull
    @Override
    protected MiddlewareWebClientBase getMiddleWareWebClient() {
        return getMiddlewareWebClient();
    }

    @NonNull
    @Override
    protected MiddlewareWebChromeBase getMiddleWareWebChrome() {
        return getMiddlewareWebChrome();
    }

    protected void initView(View view) {
        backView = view.findViewById(R.id.iv_back);
        lineView = view.findViewById(R.id.view_line);
        titleView = view.findViewById(R.id.toolbar_title);
        backView.setVisibility(View.GONE);
        lineView.setVisibility(View.GONE);
        backView.setOnClickListener(v -> {
            if (mWebForge != null && !mWebForge.back()) {
                requireActivity().finish();
            }
        });
        view.findViewById(R.id.iv_finish).setOnClickListener(v -> requireActivity().finish());
        view.findViewById(R.id.iv_more).setOnClickListener(this::showMenu);
    }

    private void showMenu(View anchor) {
        if (popupMenu == null) {
            popupMenu = new PopupMenu(requireContext(), anchor);
            popupMenu.inflate(R.menu.toolbar_menu);
            popupMenu.setOnMenuItemClickListener(item -> {
                if (mWebForge == null) {
                    return false;
                }
                String url = mWebForge.getWebCreator().getWebView().getUrl();
                int id = item.getItemId();
                if (id == R.id.refresh) {
                    mWebForge.getUrlLoader().reload();
                } else if (id == R.id.copy) {
                    ClipboardManager clipboard = (ClipboardManager) requireContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null && url != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(null, url));
                    }
                } else if (id == R.id.default_browser) {
                    openBrowser(url);
                } else if (id == R.id.default_clean) {
                    mWebForge.clearWebCache();
                    Toast.makeText(requireContext(), "已清理缓存", Toast.LENGTH_SHORT).show();
                } else {
                    return false;
                }
                return true;
            });
        }
        popupMenu.show();
    }

    private void openBrowser(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        Uri uri = Uri.parse(url);
        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "未找到可用的浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onFragmentKeyDown(int keyCode, KeyEvent event) {
        return mWebForge != null && mWebForge.handleKeyEvent(keyCode, event);
    }

    @Override
    public void onDestroyView() {
        if (popupMenu != null) {
            popupMenu.dismiss();
            popupMenu = null;
        }
        super.onDestroyView();
        backView = null;
        lineView = null;
        titleView = null;
    }
}
