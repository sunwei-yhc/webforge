package io.github.sunwei.webforge.host;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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
import io.github.sunwei.webforge.WebPage;
import io.github.sunwei.webforge.client.ChromeCallbacks;
import io.github.sunwei.webforge.client.NavigationCallbacks;
import io.github.sunwei.webforge.config.ExternalLinks;
/** 带工具栏的通用网页容器，生命周期与设置复用基础 Fragment。 */
public class ToolbarWebFragment extends BrowserFragment {
    public static final String URL_KEY = "url_key";
    private View backView;
    private View lineView;
    private TextView titleView;
    private PopupMenu popupMenu;

    public static ToolbarWebFragment newInstance(@NonNull String url) {
        ToolbarWebFragment fragment = new ToolbarWebFragment();
        Bundle args = new Bundle();
        args.putString(URL_KEY, url);
        fragment.setArguments(args);
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
        WebView webView = getPage().getView();
        if (webView != null) {
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
    }

    @NonNull
    @Override
    protected ViewGroup pageContainer(@NonNull View view) {
        return view.findViewById(R.id.webforge_page_container);
    }

    @Override
    @NonNull
    public String initialUrl() {
        Bundle arguments = getArguments();
        String url = arguments == null ? null : arguments.getString(URL_KEY);
        return TextUtils.isEmpty(url) ? "about:blank" : url;
    }

    @Override
    protected void configurePage(@NonNull WebPage.Builder builder) {
        builder.externalLinks(ExternalLinks.BLOCK)
                .chromeCallbacks(new ChromeCallbacks() {
                    @Override
                    public void onReceivedTitle(WebView view, String title) {
                        super.onReceivedTitle(view, title);
                        titleView.setText(title);
                    }
                })
                .navigationCallbacks(new NavigationCallbacks() {
                    @Override
                    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                        int visibility = TextUtils.equals(url, initialUrl()) ? View.GONE : View.VISIBLE;
                        backView.setVisibility(visibility);
                        lineView.setVisibility(visibility);
                    }
                });
    }

    protected void initView(View view) {
        backView = view.findViewById(R.id.iv_back);
        lineView = view.findViewById(R.id.view_line);
        titleView = view.findViewById(R.id.toolbar_title);
        backView.setVisibility(View.GONE);
        lineView.setVisibility(View.GONE);
        backView.setOnClickListener(v -> {
            WebPage page = getPage();
            if (page != null && !page.goBack()) {
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
                WebPage page = getPage();
                WebView webView = page == null ? null : page.getView();
                if (webView == null) {
                    return false;
                }
                String url = webView.getUrl();
                int id = item.getItemId();
                if (id == R.id.refresh) {
                    page.reload();
                } else if (id == R.id.copy) {
                    ClipboardManager clipboard = (ClipboardManager) requireContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null && url != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(null, url));
                    }
                } else if (id == R.id.default_browser) {
                    openBrowser(url);
                } else if (id == R.id.default_clean) {
                    page.clearBrowserData();
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
