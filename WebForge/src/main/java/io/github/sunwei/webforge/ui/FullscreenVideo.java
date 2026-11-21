
package io.github.sunwei.webforge.ui;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;


/**
 */
public final class FullscreenVideo {

    private Activity mActivity;
    private WebView mWebView;
    private View mMoiveView = null;
    private ViewGroup mMoiveParentView = null;
    private WebChromeClient.CustomViewCallback mCallback;
    private int mOriginalOrientation;
    private boolean mKeepScreenOn;

    private boolean isLandscapeScreen = true;

    public FullscreenVideo(Activity mActivity, WebView webView, boolean isLandscapeScreen) {
        this.mActivity = mActivity;
        this.mWebView = webView;
        this.isLandscapeScreen = isLandscapeScreen;
    }

    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        Activity mActivity;
        if ((mActivity = this.mActivity) == null || mActivity.isFinishing()) {
            return;
        }
        if (view == null || callback == null) {
            return;
        }
        if (mMoiveView != null) {
            callback.onCustomViewHidden();
            return;
        }
        mOriginalOrientation = mActivity.getRequestedOrientation();
        if (isLandscapeScreen) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        Window mWindow = mActivity.getWindow();
        if ((mWindow.getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0) {
            mWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mKeepScreenOn = true;
        }
        if (mWebView != null) {
            mWebView.setVisibility(View.GONE);
        }
        if (mMoiveParentView == null) {
            FrameLayout mContentView = mActivity.findViewById(android.R.id.content);
            mMoiveParentView = new FrameLayout(mActivity);
            mMoiveParentView.setBackgroundColor(Color.BLACK);
            mContentView.addView(mMoiveParentView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        this.mCallback = callback;
        mMoiveParentView.addView(this.mMoiveView = view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mMoiveParentView.setVisibility(View.VISIBLE);
    }

    public void onHideCustomView() {
        release(true);
    }

    public void release(boolean rendererAvailable) {
        if (mMoiveView == null) {
            return;
        }
        if (mActivity != null) {
            mActivity.setRequestedOrientation(mOriginalOrientation);
            if (mKeepScreenOn) {
                mActivity.getWindow().clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
        mKeepScreenOn = false;
        mMoiveView.setVisibility(View.GONE);
        if (mMoiveParentView != null) {
            mMoiveParentView.removeView(mMoiveView);
            ViewGroup parent = (ViewGroup) mMoiveParentView.getParent();
            if (parent != null) {
                parent.removeView(mMoiveParentView);
            }
        }
        WebChromeClient.CustomViewCallback callback = mCallback;
        mCallback = null;
        mMoiveView = null;
        mMoiveParentView = null;
        if (rendererAvailable) {
            mWebView.setVisibility(View.VISIBLE);
            if (callback != null) {
                callback.onCustomViewHidden();
            }
        }
    }

    public void setLandscapeScreen(boolean landscapeScreen) {
        isLandscapeScreen = landscapeScreen;
    }

    public boolean isVideoState() {
        return mMoiveView != null;
    }

    public boolean exitFullscreen() {
        if (!isVideoState()) {
            return false;
        }
        onHideCustomView();
        return true;
    }
}
