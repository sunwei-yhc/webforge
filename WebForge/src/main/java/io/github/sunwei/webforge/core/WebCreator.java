
package io.github.sunwei.webforge.core;

import android.webkit.WebView;
import android.widget.FrameLayout;

import io.github.sunwei.webforge.ui.indicator.IWebIndicator;

/**
 */
public interface WebCreator extends IWebIndicator {
    WebCreator create();

    WebView getWebView();

    FrameLayout getWebParentLayout();

    int getWebViewType();
}
