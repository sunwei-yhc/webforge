package io.github.sunwei.webforge.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import io.github.sunwei.webforge.R
import io.github.sunwei.webforge.util.LogUtils

/**
 */
open class WebParentLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = -1) : FrameLayout(context, attrs, defStyleAttr) {
    var controller: PageUiController? = null
        private set

    @LayoutRes
    private var mErrorLayoutRes = 0

    @IdRes
    private var mClickId = -1
    private var mErrorView: View? = null
    var webView: WebView? = null
        private set
    private var mErrorLayout: FrameLayout? = null

    constructor(context: Context) : this(context, null) {
        LogUtils.i(TAG, "WebParentLayout")
    }

    init {
        require(context is Activity) { "WebParentLayout context must be activity or activity sub class ." }
        this.mErrorLayoutRes = R.layout.webforge_error_page
    }

    open fun bindController(webForgeUIController: PageUiController?) {
        this.controller = webForgeUIController
        this.controller!!.bindWebParent(this, getContext() as Activity?)
    }

    open fun release() {
        val controller = this.controller
        this.controller = null
        this.webView = null
        if (controller != null) {
            controller.unbindWebParent(this)
        }
    }

    open fun showPageMainFrameError() {
        var container: View? = this.mErrorLayout
        if (container != null) {
            container.setVisibility(VISIBLE)
        } else {
            createErrorLayout()
            container = this.mErrorLayout
        }
        var clickView: View? = null
        if (mClickId != -1 && (container!!.findViewById<View?>(mClickId).also { clickView = it }) != null) {
            clickView!!.setClickable(true)
        } else {
            container!!.setClickable(true)
        }
    }

    private fun createErrorLayout() {
        val mFrameLayout = FrameLayout(getContext())
        mFrameLayout.setBackgroundColor(Color.WHITE)
        mFrameLayout.setId(R.id.mainframe_error_container_id)
        if (this.mErrorView == null) {
            val mLayoutInflater = LayoutInflater.from(getContext())
            LogUtils.i(TAG, "mErrorLayoutRes:" + mErrorLayoutRes)
            mLayoutInflater.inflate(mErrorLayoutRes, mFrameLayout, true)
        } else {
            mFrameLayout.addView(mErrorView)
        }
        mErrorLayout = mFrameLayout
        addView(
            mErrorLayout, 1, LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
            )
        )
        mFrameLayout.setVisibility(VISIBLE)
        if (mClickId != -1) {
            val clickView = mFrameLayout.findViewById<View?>(mClickId)
            if (clickView != null) {
                clickView.setOnClickListener(object : OnClickListener {
                    override fun onClick(v: View?) {
                        if (this@WebParentLayout.webView != null) {
                            clickView.setClickable(false)
                            this@WebParentLayout.webView!!.reload()
                        }
                    }
                })
                return
            } else {
                if (LogUtils.isDebug()) {
                    LogUtils.e(TAG, "ClickView is null , cannot bind accurate view to refresh or reload .")
                }
            }
        }
        mFrameLayout.setOnClickListener(object : OnClickListener {
            override fun onClick(v: View?) {
                if (this@WebParentLayout.webView != null) {
                    mFrameLayout.setClickable(false)
                    this@WebParentLayout.webView!!.reload()
                }
            }
        })
    }

    open fun hideErrorLayout() {
        var mView: View? = null
        if ((this.findViewById<View?>(R.id.mainframe_error_container_id).also { mView = it }) != null) {
            mView!!.setVisibility(GONE)
        }
    }

    open fun setErrorView(errorView: View?) {
        this.mErrorView = errorView
    }

    open fun setErrorLayoutRes(@LayoutRes resLayout: Int, @IdRes id: Int) {
        this.mClickId = id
        if (this.mClickId <= 0) {
            this.mClickId = -1
        }
        this.mErrorLayoutRes = resLayout
        if (this.mErrorLayoutRes <= 0) {
            this.mErrorLayoutRes = R.layout.webforge_error_page
        }
    }


    open fun bindWebView(view: WebView?) {
        if (this.webView == null) {
            this.webView = view
        }
    }

    companion object {
        private val TAG: String = WebParentLayout::class.java.getSimpleName()
    }
}
