package io.github.sunwei.webforge

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import io.github.sunwei.webforge.ui.WebParentLayout
import io.github.sunwei.webforge.ui.indicator.BaseIndicatorView
import io.github.sunwei.webforge.ui.indicator.LoadingProgressBar
import io.github.sunwei.webforge.util.WebForgeUtils

internal object PageLayout {
    @JvmStatic
    fun create(activity: Activity): WebParentLayout {
        val root = WebParentLayout(activity)
        root.setId(R.id.web_parent_layout_id)
        root.setBackgroundColor(Color.WHITE)
        return root
    }

    @JvmStatic
    fun setContent(root: WebParentLayout, layout: ViewGroup?, webView: WebView) {
        var content: View? = webView
        if (layout != null) {
            if (webView.getParent() == null) {
                layout.addView(
                    webView, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            content = layout
        }
        root.addView(
            content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.bindWebView(webView)
    }

    @JvmStatic
    fun addProgress(
        root: WebParentLayout, custom: BaseIndicatorView?,
        color: Int?, heightDp: Int
    ): BaseIndicatorView {
        var indicator = custom
        if (indicator == null) {
            val progress = LoadingProgressBar(root.getContext())
            if (color != null) {
                progress.setColor(color)
            }
            indicator = progress
        }
        val params = indicator.offerLayoutParams()
            ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        if (heightDp > 0) {
            params.height = WebForgeUtils.dp2px(root.getContext(), heightDp.toFloat())
        }
        params.gravity = Gravity.TOP
        root.addView(indicator, params)
        indicator.setVisibility(View.GONE)
        return indicator
    }
}
