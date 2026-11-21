package io.github.sunwei.webforge;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

import io.github.sunwei.webforge.ui.WebParentLayout;
import io.github.sunwei.webforge.ui.indicator.BaseIndicatorView;
import io.github.sunwei.webforge.ui.indicator.LoadingProgressBar;
import io.github.sunwei.webforge.util.WebForgeUtils;

final class PageLayout {
    private PageLayout() {}

    static WebParentLayout create(Activity activity) {
        WebParentLayout root = new WebParentLayout(activity);
        root.setId(R.id.web_parent_layout_id);
        root.setBackgroundColor(Color.WHITE);
        return root;
    }

    static void setContent(WebParentLayout root, ViewGroup layout, WebView webView) {
        View content = webView;
        if (layout != null) {
            if (webView.getParent() == null) {
                layout.addView(webView, ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }
            content = layout;
        }
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.bindWebView(webView);
    }

    static BaseIndicatorView addProgress(WebParentLayout root, BaseIndicatorView custom,
                                         Integer color, int heightDp) {
        BaseIndicatorView indicator = custom;
        if (indicator == null) {
            LoadingProgressBar progress = new LoadingProgressBar(root.getContext());
            if (color != null) {
                progress.setColor(color);
            }
            indicator = progress;
        }
        FrameLayout.LayoutParams params = indicator.offerLayoutParams();
        if (heightDp > 0) {
            params.height = WebForgeUtils.dp2px(root.getContext(), heightDp);
        }
        params.gravity = Gravity.TOP;
        root.addView(indicator, params);
        indicator.setVisibility(View.GONE);
        return indicator;
    }
}
