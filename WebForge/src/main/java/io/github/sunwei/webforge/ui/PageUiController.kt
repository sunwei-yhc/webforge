package io.github.sunwei.webforge.ui

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import io.github.sunwei.webforge.R
import io.github.sunwei.webforge.permission.WebForgeActionFragment
import io.github.sunwei.webforge.util.WebForgeUtils
import java.util.Arrays

/** 页面的默认交互界面，宿主可重写需要定制的回调。  */
open class PageUiController {
    private var activity: Activity? = null
    private var layout: WebParentLayout? = null
    private var chooser: Dialog? = null
    private var externalLink: AlertDialog? = null
    private var download: AlertDialog? = null
    private var confirm: AlertDialog? = null
    private var prompt: AlertDialog? = null
    private var certificate: AlertDialog? = null
    private var loading: ProgressDialog? = null

    open fun bindWebParent(layout: WebParentLayout?, activity: Activity?) {
        check(this.layout == null) { "PageUiController already belongs to a page" }
        this.layout = layout
        this.activity = activity
        bindSupportWebParent(layout, activity)
    }

    open fun unbindWebParent(layout: WebParentLayout?) {
        if (this.layout != layout) {
            return
        }
        try {
            onPageClosed()
        } finally {
            toDismissDialog(chooser)
            toDismissDialog(externalLink)
            toDismissDialog(download)
            toDismissDialog(confirm)
            toDismissDialog(prompt)
            toDismissDialog(certificate)
            toDismissDialog(loading)
            loading = null
            chooser = null
            externalLink = null
            download = null
            confirm = null
            prompt = null
            certificate = null
            this.layout = null
            activity = null
        }
    }

    protected fun bindSupportWebParent(layout: WebParentLayout?, activity: Activity?) {}

    /** 关闭当前页面后释放宿主扩展持有的对话框和视图引用。  */
    protected fun onPageClosed() {}

    protected fun toDismissDialog(dialog: Dialog?) {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss()
        }
    }

    open fun onJsAlert(view: WebView?, url: String?, message: String?) {
        if (!canShowUi()) {
            return
        }
        val dialog = newJsDialogBuilder()
            .setTitle(R.string.webforge_tips)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        styleJsDialog(dialog)
    }

    private fun showMessage(view: WebView?, message: String?) {
        if (!canShowUi()) {
            return
        }
        if (HAS_MATERIAL && view != null) {
            try {
                WebForgeUtils.show(
                    view, message, Snackbar.LENGTH_SHORT, Color.WHITE,
                    activity!!.getResources().getColor(R.color.black), null, -1, null
                )
                return
            } catch (noSnackbarParent: IllegalArgumentException) {
                // 未附着到视图层级时也可以显示消息。
            }
        }
        WebForgeUtils.toastShowShort(activity!!.getApplicationContext(), message)
    }

    open fun onOpenPagePrompt(view: WebView?, url: String?, callback: Handler.Callback?) {
        if (!canShowUi()) {
            return
        }
        val currentActivity = activity ?: return
        toDismissDialog(externalLink)
        externalLink = AlertDialog.Builder(currentActivity)
            .setTitle(R.string.webforge_tips)
            .setMessage(
                currentActivity.getString(
                    R.string.webforge_leave_app_and_go_other_page,
                    WebForgeUtils.getApplicationName(currentActivity)
                )
            )
            .setNegativeButton(android.R.string.cancel, DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> send(callback, -1) })
            .setPositiveButton(R.string.webforge_leave, DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> send(callback, 1) })
            .show()
    }

    open fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult) {
        if (!canShowUi()) {
            result.cancel()
            return
        }
        if (confirm != null && confirm!!.isShowing()) {
            confirm!!.cancel()
        }
        confirm = newJsDialogBuilder()
            .setTitle(R.string.webforge_tips)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> result.cancel() })
            .setPositiveButton(android.R.string.ok, DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> result.confirm() })
            .setOnCancelListener(DialogInterface.OnCancelListener { dialog: DialogInterface? -> result.cancel() })
            .show()
        styleJsDialog(confirm)
    }

    open fun onJsPrompt(
        view: WebView?, url: String?, message: String?, defaultValue: String?,
        result: JsPromptResult
    ) {
        if (!canShowUi()) {
            result.cancel()
            return
        }
        if (prompt != null && prompt!!.isShowing()) {
            prompt!!.cancel()
        }
        val input = EditText(activity)
        input.setText(defaultValue)
        input.setSingleLine(true)
        input.setSelectAllOnFocus(true)
        input.setHint(R.string.webforge_prompt_hint)
        input.setPadding(dp(14), dp(10), dp(14), dp(10))
        prompt = newJsDialogBuilder()
            .setView(input)
            .setTitle(message)
            .setNegativeButton(android.R.string.cancel, DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> result.cancel() })
            .setPositiveButton(android.R.string.ok, DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> result.confirm(input.getText().toString()) })
            .setOnCancelListener(DialogInterface.OnCancelListener { dialog: DialogInterface? -> result.cancel() })
            .show()
        styleJsDialog(prompt)
        input.requestFocus()
    }

    /** 创建统一的网页 JavaScript 弹框，避免直接使用宿主系统的老旧默认样式。  */
    private fun newJsDialogBuilder(): AlertDialog.Builder {
        return AlertDialog.Builder(activity!!, R.style.WebForgeJsDialog)
    }

    private fun styleJsDialog(dialog: AlertDialog?) {
        if (dialog == null || dialog.getWindow() == null) {
            return
        }
        dialog.getWindow()!!.setDimAmount(0.42f)
        val message = dialog.findViewById<TextView?>(android.R.id.message)
        if (message != null) {
            message.setTextColor(activity!!.getResources().getColor(R.color.webforge_dialog_text))
            message.setTextSize(16f)
            message.setLineSpacing(0f, 1.15f)
        }
        val title = dialog.findViewById<TextView?>(
            activity!!.getResources().getIdentifier(
                "alertTitle", "id", "android"
            )
        )
        if (title != null) {
            title.setTextColor(activity!!.getResources().getColor(R.color.webforge_dialog_title))
            title.setTextSize(19f)
        }
        val positive: TextView? = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negative: TextView? = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        if (positive != null) {
            positive.setTextColor(activity!!.getResources().getColor(R.color.webforge_dialog_accent))
            positive.setTextSize(14f)
        }
        if (negative != null) {
            negative.setTextColor(activity!!.getResources().getColor(R.color.webforge_dialog_muted))
            negative.setTextSize(14f)
        }
    }

    private fun dp(value: Int): Int {
        return Math.round(value * activity!!.getResources().getDisplayMetrics().density)
    }

    open fun onSelectItemsPrompt(view: WebView?, url: String?, options: Array<String>, callback: Handler.Callback?) {
        if (!canShowUi()) {
            return
        }
        if (chooser != null && chooser!!.isShowing()) {
            chooser!!.cancel()
        }
        val dialog: Dialog = if (HAS_MATERIAL)
            BottomSheetDialog(activity!!)
        else
            AlertDialog.Builder(activity!!).create()
        val panel = WebForgeChooserPanel(activity!!, options, callback, Runnable { dialog.dismiss() })
        if (HAS_MATERIAL) {
            dialog.setContentView(panel.contentView)
        } else {
            (dialog as AlertDialog).setView(panel.contentView)
        }
        dialog.setOnCancelListener(DialogInterface.OnCancelListener { ignored: DialogInterface? -> panel.cancel() })
        chooser = dialog
        dialog.show()
    }

    open fun onForceDownloadAlert(url: String?, callback: Handler.Callback?) {
        if (!canShowUi()) {
            return
        }
        toDismissDialog(download)
        download = AlertDialog.Builder(activity!!)
            .setTitle(R.string.webforge_tips)
            .setMessage(R.string.webforge_honeycomblow)
            .setNegativeButton(R.string.webforge_download, DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> send(callback, 0) })
            .setPositiveButton(R.string.webforge_cancel, null)
            .show()
    }

    open fun onMainFrameError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
        if (layout != null) {
            layout!!.showPageMainFrameError()
        }
    }

    open fun onShowMainFrame() {
        if (layout != null) {
            layout!!.hideErrorLayout()
        }
    }

    open fun onLoading(message: String?) {
        if (!canShowUi()) {
            return
        }
        if (loading == null) {
            loading = ProgressDialog(activity)
            loading!!.setCancelable(false)
            loading!!.setCanceledOnTouchOutside(false)
        }
        loading!!.setMessage(message)
        loading!!.show()
    }

    open fun onCancelLoading() {
        toDismissDialog(loading)
        loading = null
    }

    open fun onShowMessage(message: String?, from: String?) {
        val parent = layout
        if (parent != null && parent.webView != null && (from == null || !from.contains("performDownload"))) {
            showMessage(parent.webView, message)
        }
    }

    open fun onPermissionsDeny(permissions: Array<String>, permissionType: String?, action: String?) {}

    open fun onShowSslCertificateErrorDialog(view: WebView?, handler: SslErrorHandler, error: SslError) {
        if (!canShowUi()) {
            handler.cancel()
            return
        }
        val message: Int
        when (error.getPrimaryError()) {
            SslError.SSL_UNTRUSTED -> message = R.string.webforge_message_show_ssl_untrusted
            SslError.SSL_EXPIRED -> message = R.string.webforge_message_show_ssl_expired
            SslError.SSL_IDMISMATCH -> message = R.string.webforge_message_show_ssl_hostname_mismatch
            SslError.SSL_NOTYETVALID -> message = R.string.webforge_message_show_ssl_not_yet_valid
            else -> message = R.string.webforge_message_show_ssl_error
        }
        if (certificate != null && certificate!!.isShowing()) {
            certificate!!.cancel()
        }
        certificate = AlertDialog.Builder(activity!!)
            .setTitle(R.string.webforge_title_ssl_error)
            .setMessage(activity!!.getString(message) + activity!!.getString(R.string.webforge_message_show_continue))
            .setPositiveButton(R.string.webforge_continue, DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> handler.proceed() })
            .setNegativeButton(R.string.webforge_cancel, DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> handler.cancel() })
            .setOnCancelListener(DialogInterface.OnCancelListener { dialog: DialogInterface? -> handler.cancel() })
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    open fun onPermissionRequest(request: PermissionRequest) {
        val host = activity
        val resources = request.getResources()
        if (host == null || host.isFinishing() || resources == null || resources.size == 0) {
            request.deny()
            return
        }

        val requested: List<String> = resources.asList()
        val allowed: MutableList<String> = ArrayList(2)
        val permissions: MutableList<String> = ArrayList(2)
        if (requested.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
            allowed.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            permissions.add(Manifest.permission.CAMERA)
        }
        if (requested.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            allowed.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (allowed.isEmpty()) {
            request.deny()
            return
        }

        val required = permissions.toTypedArray()
        val grant = allowed.toTypedArray()
        val finish = Runnable {
            if (WebForgeUtils.hasPermission(host, required.asList())) {
                request.grant(grant)
            } else {
                request.deny()
            }
        }
        if (!WebForgeUtils.requestPermissions(host, required, WebForgeActionFragment.PermissionListener { results: Array<String>, flags: IntArray, extras: Bundle? -> finish.run() })) {
            finish.run()
        }
    }

    private fun canShowUi(): Boolean {
        return activity != null && !activity!!.isFinishing() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity!!.isDestroyed())
    }

    companion object {
        private val HAS_MATERIAL: Boolean = hasMaterial()

        private fun hasMaterial(): Boolean {
            try {
                Class.forName("com.google.android.material.snackbar.Snackbar")
                Class.forName("com.google.android.material.bottomsheet.BottomSheetDialog")
                return true
            } catch (unavailable: ClassNotFoundException) {
                return false
            } catch (unavailable: LinkageError) {
                return false
            }
        }

        private fun send(callback: Handler.Callback?, resultCode: Int) {
            if (callback != null) {
                val message = Message.obtain(null, resultCode)
                try {
                    callback.handleMessage(message)
                } finally {
                    message.recycle()
                }
            }
        }
    }
}
