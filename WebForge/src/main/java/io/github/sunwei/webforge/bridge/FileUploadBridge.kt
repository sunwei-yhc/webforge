package io.github.sunwei.webforge.bridge

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.webkit.JavascriptInterface
import io.github.sunwei.webforge.WebPage
import io.github.sunwei.webforge.permission.PermissionInterceptor
import io.github.sunwei.webforge.util.WebForgeUtils
import org.json.JSONArray
import org.json.JSONException
import java.lang.ref.WeakReference

/** 实现 H5 的 window.webForge.uploadFile()。  */
class FileUploadBridge(page: WebPage?, activity: Activity?, private val permissionInterceptor: PermissionInterceptor?) {
    private val pageReference: WeakReference<WebPage?>
    private val activityReference: WeakReference<Activity?>

    init {
        pageReference = WeakReference<WebPage?>(page)
        activityReference = WeakReference<Activity?>(activity)
    }

    @JavascriptInterface
    fun uploadFile() {
        uploadFile("*/*")
    }

    @JavascriptInterface
    fun uploadFile(acceptType: String?) {
        val activity = activityReference.get()
        if (activity != null) {
            activity.runOnUiThread(Runnable { openPicker(activity, acceptType) })
        }
    }

    private fun openPicker(activity: Activity, acceptType: String?) {
        val page = pageReference.get()
        val view = if (page == null) null else page.view
        if (view == null || activity.isFinishing()) {
            return
        }
        WebForgeUtils.showFileChooserCompat(
            activity, view, null, null,
            permissionInterceptor, null, acceptType, Handler.Callback { message: Message? ->
                val current = pageReference.get()
                if (current != null && !current.isClosed) {
                    var files: JSONArray? = null
                    if (message!!.obj is String) {
                        try {
                            files = JSONArray(message.obj as String)
                        } catch (ignored: JSONException) {
                            // 无法解析时按选择失败处理。
                        }
                    }
                    current.callJavaScript("uploadFileResult", files)
                }
                true
            })
    }
}
