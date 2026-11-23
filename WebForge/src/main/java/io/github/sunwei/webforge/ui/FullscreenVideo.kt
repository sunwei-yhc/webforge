package io.github.sunwei.webforge.ui

import android.R
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout

/**
 */
class FullscreenVideo(private val mActivity: Activity?, private val mWebView: WebView?, isLandscapeScreen: Boolean) {
    private var mMoiveView: View? = null
    private var mMoiveParentView: ViewGroup? = null
    private var mCallback: WebChromeClient.CustomViewCallback? = null
    private var mOriginalOrientation = 0
    private var mKeepScreenOn = false

    private var isLandscapeScreen = true

    init {
        this.isLandscapeScreen = isLandscapeScreen
    }

    fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        val mActivity: Activity?
        if ((this.mActivity.also { mActivity = it }) == null || mActivity!!.isFinishing()) {
            return
        }
        if (view == null || callback == null) {
            return
        }
        if (mMoiveView != null) {
            callback.onCustomViewHidden()
            return
        }
        mOriginalOrientation = mActivity.getRequestedOrientation()
        if (isLandscapeScreen) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        }
        val mWindow = mActivity.getWindow()
        if ((mWindow.getAttributes().flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0) {
            mWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            mKeepScreenOn = true
        }
        if (mWebView != null) {
            mWebView.setVisibility(View.GONE)
        }
        if (mMoiveParentView == null) {
            val mContentView = mActivity.findViewById<FrameLayout>(R.id.content)
            mMoiveParentView = FrameLayout(mActivity)
            mMoiveParentView!!.setBackgroundColor(Color.BLACK)
            mContentView.addView(
                mMoiveParentView, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        this.mCallback = callback
        mMoiveParentView!!.addView(
            view.also { this.mMoiveView = it }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        mMoiveParentView!!.setVisibility(View.VISIBLE)
    }

    fun onHideCustomView() {
        release(true)
    }

    fun release(rendererAvailable: Boolean) {
        if (mMoiveView == null) {
            return
        }
        if (mActivity != null) {
            mActivity.setRequestedOrientation(mOriginalOrientation)
            if (mKeepScreenOn) {
                mActivity.getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
        }
        mKeepScreenOn = false
        mMoiveView!!.setVisibility(View.GONE)
        if (mMoiveParentView != null) {
            mMoiveParentView!!.removeView(mMoiveView)
            val parent = mMoiveParentView!!.getParent() as ViewGroup?
            if (parent != null) {
                parent.removeView(mMoiveParentView)
            }
        }
        val callback = mCallback
        mCallback = null
        mMoiveView = null
        mMoiveParentView = null
        if (rendererAvailable) {
            mWebView!!.setVisibility(View.VISIBLE)
            if (callback != null) {
                callback.onCustomViewHidden()
            }
        }
    }

    fun setLandscapeScreen(landscapeScreen: Boolean) {
        isLandscapeScreen = landscapeScreen
    }

    val isVideoState: Boolean
        get() = mMoiveView != null

    fun exitFullscreen(): Boolean {
        if (!this.isVideoState) {
            return false
        }
        onHideCustomView()
        return true
    }
}
