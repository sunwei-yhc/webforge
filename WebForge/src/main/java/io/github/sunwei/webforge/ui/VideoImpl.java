
package io.github.sunwei.webforge.ui;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import io.github.sunwei.webforge.core.EventInterceptor;

/**
 */
public class VideoImpl implements IVideo, EventInterceptor {

    private Activity mActivity;
    private WebView mWebView;
    private View mMoiveView = null;
    private ViewGroup mMoiveParentView = null;
    private WebChromeClient.CustomViewCallback mCallback;
    private int mOriginalOrientation;
    private boolean mKeepScreenOn;
    private boolean mShowRequested;

    private boolean isLandscapeScreen = true;

    public VideoImpl(Activity mActivity, WebView webView, boolean isLandscapeScreen) {
        this.mActivity = mActivity;
        this.mWebView = webView;
        this.isLandscapeScreen = isLandscapeScreen;
    }

    @Override
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
        mShowRequested = true;
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

    public static void mute(Context context) {
        AudioManager mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int mute_volume = 0;
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mute_volume, 0);
    }

    /**
     * 获取当前屏幕是否是横屏（默认）
     *
     * @param context
     * @return
     */
    public static boolean isCurOriLand(Context context) {
        boolean isLand = true;
        try {
            Configuration mConfiguration = context.getResources().getConfiguration(); //获取设置的配置信息
            int ori = mConfiguration.orientation; //获取屏幕方向
            if (ori == mConfiguration.ORIENTATION_LANDSCAPE) {
                //横屏
                isLand = true;
            } else if (ori == mConfiguration.ORIENTATION_PORTRAIT) {
                //竖屏
                isLand = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isLand;
    }
    @Override
    public void onHideCustomView() {
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
        if (this.mCallback != null) {
            mCallback.onCustomViewHidden();
        }
        this.mCallback = null;
        this.mMoiveView = null;
        this.mMoiveParentView = null;
        this.mShowRequested = false;
        if (mWebView != null) {
            mWebView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void setLandscapeScreen(boolean landscapeScreen) {
        isLandscapeScreen = landscapeScreen;
    }

    @Override
    public boolean isVideoState() {
        return mShowRequested && mMoiveView != null;
    }

    @Override
    public boolean event() {

        if (isVideoState()) {
            onHideCustomView();
            return true;
        } else {
            return false;
        }
    }
}
