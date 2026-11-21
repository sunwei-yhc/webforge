
package io.github.sunwei.webforge.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import io.github.sunwei.webforge.R;
import io.github.sunwei.webforge.util.LogUtils;
import io.github.sunwei.webforge.util.WebForgeUtils;

/**
 */
public class DefaultDesignUIController extends DefaultUIController {

    private BottomSheetDialog mBottomSheetDialog;
    private Activity mActivity = null;
    private WebParentLayout mWebParentLayout;

    @Override
    public void onJsAlert(WebView view, String url, String message) {
        onJsAlertInternal(view, message);
    }

    private void onJsAlertInternal(WebView view, String message) {
        Activity mActivity = this.mActivity;
        if (mActivity == null || mActivity.isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (mActivity.isDestroyed()) {
                return;
            }
        }
        try {
            WebForgeUtils.show(view,
                    message,
                    Snackbar.LENGTH_SHORT,
                    Color.WHITE,
                    mActivity.getResources().getColor(R.color.black),
                    null,
                    -1,
                    null);
        } catch (Throwable throwable) {
            if (LogUtils.isDebug()){
                throwable.printStackTrace();
            }
        }
    }

    @Override
    public void onJsConfirm(WebView view, String url, String message, JsResult jsResult) {
        super.onJsConfirm(view, url, message, jsResult);
    }

    @Override
    public void onSelectItemsPrompt(WebView view, String url, String[] ways, Handler.Callback callback) {
        showChooserInternal(view, url, ways, callback);
    }

    @Override
    public void onForceDownloadAlert(String url, final Handler.Callback callback) {
        super.onForceDownloadAlert(url, callback);
    }

    private void showChooserInternal(WebView view, String url, final String[] ways, final Handler.Callback callback) {
        Activity mActivity;
        if ((mActivity = this.mActivity) == null || mActivity.isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (mActivity.isDestroyed()) {
                return;
            }
        }
        LogUtils.i(TAG, "url:" + url);
        if (mBottomSheetDialog != null && mBottomSheetDialog.isShowing()) {
            mBottomSheetDialog.cancel();
        }
        final BottomSheetDialog dialog = new BottomSheetDialog(mActivity);
        final WebForgeChooserPanel chooserPanel = new WebForgeChooserPanel(
                mActivity,
                ways,
                callback,
                new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                    }
                });
        dialog.setContentView(chooserPanel.getContentView());
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                chooserPanel.cancel();
            }
        });
        mBottomSheetDialog = dialog;
        dialog.show();
    }

    @Override
    public void onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult jsPromptResult) {
        super.onJsPrompt(view, url, message, defaultValue, jsPromptResult);
    }

    @Override
    protected void bindSupportWebParent(WebParentLayout webParentLayout, Activity activity) {
        super.bindSupportWebParent(webParentLayout, activity);
        this.mActivity = activity;
        this.mWebParentLayout = webParentLayout;
    }

    @Override
    public void onShowMessage(String message, String from) {
        Activity mActivity;
        if ((mActivity = this.mActivity) == null || mActivity.isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (mActivity.isDestroyed()) {
                return;
            }
        }
        if (!TextUtils.isEmpty(from) && from.contains("performDownload")) {
            return;
        }
        onJsAlertInternal(mWebParentLayout.getWebView(), message);
    }
}
