package io.github.sunwei.webforge.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import io.github.sunwei.webforge.R;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 页面的默认交互界面，宿主可重写需要定制的回调。 */
public class PageUiController {
    private static final boolean HAS_MATERIAL = hasMaterial();

    private Activity activity;
    private WebParentLayout layout;
    private Dialog chooser;
    private AlertDialog externalLink;
    private AlertDialog download;
    private AlertDialog confirm;
    private AlertDialog prompt;
    private AlertDialog certificate;
    private ProgressDialog loading;

    private static boolean hasMaterial() {
        try {
            Class.forName("com.google.android.material.snackbar.Snackbar");
            Class.forName("com.google.android.material.bottomsheet.BottomSheetDialog");
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }

    final void bindWebParent(WebParentLayout layout, Activity activity) {
        if (this.layout != null) {
            throw new IllegalStateException("PageUiController already belongs to a page");
        }
        this.layout = layout;
        this.activity = activity;
        bindSupportWebParent(layout, activity);
    }

    final void unbindWebParent(WebParentLayout layout) {
        if (this.layout != layout) {
            return;
        }
        try {
            onPageClosed();
        } finally {
            toDismissDialog(chooser);
            toDismissDialog(externalLink);
            toDismissDialog(download);
            toDismissDialog(confirm);
            toDismissDialog(prompt);
            toDismissDialog(certificate);
            toDismissDialog(loading);
            loading = null;
            chooser = null;
            externalLink = null;
            download = null;
            confirm = null;
            prompt = null;
            certificate = null;
            this.layout = null;
            activity = null;
        }
    }

    protected void bindSupportWebParent(WebParentLayout layout, Activity activity) {}

    /** 关闭当前页面后释放宿主扩展持有的对话框和视图引用。 */
    protected void onPageClosed() {}

    protected void toDismissDialog(Dialog dialog) {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    public void onJsAlert(WebView view, String url, String message) {
        if (!canShowUi()) {
            return;
        }
        AlertDialog dialog = newJsDialogBuilder()
                .setTitle(R.string.webforge_tips)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        styleJsDialog(dialog);
    }

    private void showMessage(WebView view, String message) {
        if (!canShowUi()) {
            return;
        }
        if (HAS_MATERIAL) {
            try {
                WebForgeUtils.show(view, message, Snackbar.LENGTH_SHORT, Color.WHITE,
                        activity.getResources().getColor(R.color.black), null, -1, null);
                return;
            } catch (IllegalArgumentException noSnackbarParent) {
                // 未附着到视图层级时也可以显示消息。
            }
        }
        WebForgeUtils.toastShowShort(activity.getApplicationContext(), message);
    }

    public void onOpenPagePrompt(WebView view, String url, Handler.Callback callback) {
        if (!canShowUi()) {
            return;
        }
        toDismissDialog(externalLink);
        externalLink = new AlertDialog.Builder(activity)
                .setTitle(R.string.webforge_tips)
                .setMessage(activity.getString(R.string.webforge_leave_app_and_go_other_page,
                        WebForgeUtils.getApplicationName(activity)))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> send(callback, -1))
                .setPositiveButton(R.string.webforge_leave, (dialog, which) -> send(callback, 1))
                .show();
    }

    public void onJsConfirm(WebView view, String url, String message, JsResult result) {
        if (!canShowUi()) {
            result.cancel();
            return;
        }
        if (confirm != null && confirm.isShowing()) {
            confirm.cancel();
        }
        confirm = newJsDialogBuilder()
                .setTitle(R.string.webforge_tips)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                .setOnCancelListener(dialog -> result.cancel())
                .show();
        styleJsDialog(confirm);
    }

    public void onJsPrompt(WebView view, String url, String message, String defaultValue,
                           JsPromptResult result) {
        if (!canShowUi()) {
            result.cancel();
            return;
        }
        if (prompt != null && prompt.isShowing()) {
            prompt.cancel();
        }
        EditText input = new EditText(activity);
        input.setText(defaultValue);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setHint(R.string.webforge_prompt_hint);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        prompt = newJsDialogBuilder()
                .setView(input)
                .setTitle(message)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        result.confirm(input.getText().toString()))
                .setOnCancelListener(dialog -> result.cancel())
                .show();
        styleJsDialog(prompt);
        input.requestFocus();
    }

    /** 创建统一的网页 JavaScript 弹框，避免直接使用宿主系统的老旧默认样式。 */
    private AlertDialog.Builder newJsDialogBuilder() {
        return new AlertDialog.Builder(activity, R.style.WebForgeJsDialog);
    }

    private void styleJsDialog(AlertDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        dialog.getWindow().setDimAmount(0.42f);
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(activity.getResources().getColor(R.color.webforge_dialog_text));
            message.setTextSize(16);
            message.setLineSpacing(0, 1.15f);
        }
        TextView title = dialog.findViewById(activity.getResources().getIdentifier(
                "alertTitle", "id", "android"));
        if (title != null) {
            title.setTextColor(activity.getResources().getColor(R.color.webforge_dialog_title));
            title.setTextSize(19);
        }
        TextView positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        TextView negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) {
            positive.setTextColor(activity.getResources().getColor(R.color.webforge_dialog_accent));
            positive.setTextSize(14);
        }
        if (negative != null) {
            negative.setTextColor(activity.getResources().getColor(R.color.webforge_dialog_muted));
            negative.setTextSize(14);
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public void onSelectItemsPrompt(WebView view, String url, String[] options, Handler.Callback callback) {
        if (!canShowUi()) {
            return;
        }
        if (chooser != null && chooser.isShowing()) {
            chooser.cancel();
        }
        Dialog dialog = HAS_MATERIAL ? new BottomSheetDialog(activity)
                : new AlertDialog.Builder(activity).create();
        WebForgeChooserPanel panel = new WebForgeChooserPanel(activity, options, callback, dialog::dismiss);
        if (HAS_MATERIAL) {
            dialog.setContentView(panel.getContentView());
        } else {
            ((AlertDialog) dialog).setView(panel.getContentView());
        }
        dialog.setOnCancelListener(ignored -> panel.cancel());
        chooser = dialog;
        dialog.show();
    }

    public void onForceDownloadAlert(String url, Handler.Callback callback) {
        if (!canShowUi()) {
            return;
        }
        toDismissDialog(download);
        download = new AlertDialog.Builder(activity)
                .setTitle(R.string.webforge_tips)
                .setMessage(R.string.webforge_honeycomblow)
                .setNegativeButton(R.string.webforge_download, (dialog, which) -> send(callback, 0))
                .setPositiveButton(R.string.webforge_cancel, null)
                .show();
    }

    public void onMainFrameError(WebView view, int errorCode, String description, String failingUrl) {
        if (layout != null) {
            layout.showPageMainFrameError();
        }
    }

    public void onShowMainFrame() {
        if (layout != null) {
            layout.hideErrorLayout();
        }
    }

    public void onLoading(String message) {
        if (!canShowUi()) {
            return;
        }
        if (loading == null) {
            loading = new ProgressDialog(activity);
            loading.setCancelable(false);
            loading.setCanceledOnTouchOutside(false);
        }
        loading.setMessage(message);
        loading.show();
    }

    public void onCancelLoading() {
        toDismissDialog(loading);
        loading = null;
    }

    public void onShowMessage(String message, String from) {
        WebParentLayout parent = layout;
        if (parent != null && parent.getWebView() != null
                && (from == null || !from.contains("performDownload"))) {
            showMessage(parent.getWebView(), message);
        }
    }

    public void onPermissionsDeny(String[] permissions, String permissionType, String action) {}

    public void onShowSslCertificateErrorDialog(WebView view, SslErrorHandler handler, SslError error) {
        if (!canShowUi()) {
            handler.cancel();
            return;
        }
        int message;
        switch (error.getPrimaryError()) {
            case SslError.SSL_UNTRUSTED:
                message = R.string.webforge_message_show_ssl_untrusted;
                break;
            case SslError.SSL_EXPIRED:
                message = R.string.webforge_message_show_ssl_expired;
                break;
            case SslError.SSL_IDMISMATCH:
                message = R.string.webforge_message_show_ssl_hostname_mismatch;
                break;
            case SslError.SSL_NOTYETVALID:
                message = R.string.webforge_message_show_ssl_not_yet_valid;
                break;
            default:
                message = R.string.webforge_message_show_ssl_error;
        }
        if (certificate != null && certificate.isShowing()) {
            certificate.cancel();
        }
        certificate = new AlertDialog.Builder(activity)
                .setTitle(R.string.webforge_title_ssl_error)
                .setMessage(activity.getString(message) + activity.getString(R.string.webforge_message_show_continue))
                .setPositiveButton(R.string.webforge_continue, (dialog, which) -> handler.proceed())
                .setNegativeButton(R.string.webforge_cancel, (dialog, which) -> handler.cancel())
                .setOnCancelListener(dialog -> handler.cancel())
                .show();
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public void onPermissionRequest(PermissionRequest request) {
        if (request == null) {
            return;
        }
        Activity host = activity;
        String[] resources = request.getResources();
        if (host == null || host.isFinishing() || resources == null || resources.length == 0) {
            request.deny();
            return;
        }

        List<String> requested = Arrays.asList(resources);
        List<String> allowed = new ArrayList<>(2);
        List<String> permissions = new ArrayList<>(2);
        if (requested.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
            allowed.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
            permissions.add(Manifest.permission.CAMERA);
        }
        if (requested.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            allowed.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (allowed.isEmpty()) {
            request.deny();
            return;
        }

        String[] required = permissions.toArray(new String[0]);
        String[] grant = allowed.toArray(new String[0]);
        Runnable finish = () -> {
            if (WebForgeUtils.hasPermission(host, required)) {
                request.grant(grant);
            } else {
                request.deny();
            }
        };
        if (!WebForgeUtils.requestPermissions(host, required, (results, flags, extras) -> finish.run())) {
            finish.run();
        }
    }

    private boolean canShowUi() {
        return activity != null && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed());
    }

    private static void send(Handler.Callback callback, int resultCode) {
        if (callback != null) {
            Message message = Message.obtain(null, resultCode);
            try {
                callback.handleMessage(message);
            } finally {
                message.recycle();
            }
        }
    }
}
