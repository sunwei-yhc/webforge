package io.github.sunwei.webforge.bridge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.Patterns
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.util.Consumer
import io.github.sunwei.webforge.WebPage
import io.github.sunwei.webforge.config.WebForgeConfig
import io.github.sunwei.webforge.permission.WebForgeActionFragment
import io.github.sunwei.webforge.permission.WebForgePermissions
import io.github.sunwei.webforge.util.MapUtil
import io.github.sunwei.webforge.util.StatusBarUtil
import io.github.sunwei.webforge.util.WebForgeUtils
import java.lang.ref.WeakReference

/** 供可信 H5 调用的 Android 原生能力。  */
open class AndroidBridge(page: WebPage?, activity: Activity?) {
    private val pageReference: WeakReference<WebPage?>
    private val activityReference: WeakReference<Activity?>

    init {
        this.pageReference = WeakReference<WebPage?>(page)
        this.activityReference = WeakReference<Activity?>(activity)
    }

    /** 关闭承载 WebView 的 Activity。  */
    @JavascriptInterface
    open fun closePage() {
        runOnUiThread(Consumer { obj: Activity? -> obj!!.finish() })
    }

    /** 先退出视频全屏或返回上一页，已无历史时关闭 Activity。  */
    @JavascriptInterface
    open fun goBackOrClose() {
        runOnUiThread(Consumer { activity: Activity? ->
            val page = pageReference.get()
            if (page != null && !page.isClosed && !page.goBack()) {
                activity!!.finish()
            }
        })
    }

    /** 用系统处理程序打开 HTTP 或 HTTPS 地址。  */
    @JavascriptInterface
    open fun openExternalBrowser(url: String?) {
        val uri: Uri? = parseWebUri(url)
        if (uri == null) {
            showToast("请输入有效的 HTTP 或 HTTPS 地址")
            return
        }
        startExternalActivity(Intent(Intent.ACTION_VIEW, uri), "无法打开浏览器")
    }

    /** 按经度、纬度、地点名的顺序传入起点和终点，打开高德驾车导航。  */
    @JavascriptInterface
    open fun openAmapRoute(
        startLongitude: String?, startLatitude: String?, startName: String?,
        endLongitude: String?, endLatitude: String?, endName: String?
    ) {
        if (TextUtils.isEmpty(startLongitude) || TextUtils.isEmpty(startLatitude)
            || TextUtils.isEmpty(endLongitude) || TextUtils.isEmpty(endLatitude)
        ) {
            return
        }
        runOnUiThread(Consumer { activity: Activity? ->
            MapUtil.route(
                activity, startLongitude, startLatitude,
                startName, endLongitude, endLatitude, endName
            )
        })
    }

    /**
     * 设置状态栏颜色，支持 RRGGBB 或 AARRGGBB，可带 # 前缀。
     * whiteIcons 为 1 时使用白色图标，其余值使用深色图标。
     */
    @JavascriptInterface
    open fun setStatusBarColor(color: String?, whiteIcons: Int) {
        if (TextUtils.isEmpty(color) || !color!!.matches("(?i)#?[0-9a-f]{6}([0-9a-f]{2})?".toRegex())) {
            return
        }
        val parsedColor = Color.parseColor(if (color.startsWith("#")) color else "#" + color)
        runOnUiThread(Consumer { activity: Activity? ->
            if (whiteIcons == 1) {
                StatusBarUtil.setLightMode(activity!!)
            } else {
                StatusBarUtil.setDarkMode(activity!!)
            }
            StatusBarUtil.setColor(activity, parsedColor)
        })
    }

    /** 1 清除透明状态栏，其他值启用透明状态栏。  */
    @JavascriptInterface
    open fun setTransparentForWindow(isShowWindow: Int) {
        runOnUiThread(Consumer { activity: Activity? ->
            if (isShowWindow == 1) {
                StatusBarUtil.clearFlag(activity!!)
            } else {
                StatusBarUtil.setTransparentForWindow(activity!!)
            }
        })
    }

    /**
     * 是否已经拥有指定类型的权限
     *
     * @param type 权限类型 Camera | Audio | Call | Location | Media ，支持多个类型使用 , 分隔
     */
    @JavascriptInterface
    open fun hasPermission(type: String?): Boolean {
        val activity = activityReference.get()
        val permissions = WebForgePermissions.of(type)
        if (activity == null || permissions.size == 0) {
            return false
        }
        return WebForgeUtils.hasPermission(activity, permissions.asList())
    }

    /**
     * 主动申请指定类型的权限，例如 H5 录音前调用 Android.requestPermission("audio")
     * 权限类型 Camera | Audio | Call | Location | Media ，支持多个类型使用 , 分隔 ，例如 "audio,call"
     *
     * @param type 权限类型
     */
    @JavascriptInterface
    open fun requestPermission(type: String?) {
        requestPermission(type, null)
    }

    /** 申请权限，结果通过指定的 JavaScript 函数返回布尔值。  */
    @JavascriptInterface
    open fun requestPermissionsWithCallback(type: String?, callbackName: String?) {
        val permissions = WebForgePermissions.of(type)
        if (!isActivityActive(activityReference.get()) || permissions.size == 0) {
            invokePermissionCallback(callbackName, false)
            return
        }
        runOnUiThread(Consumer { activity: Activity? ->
            val requesting = WebForgeUtils.requestPermissions(
                activity!!, permissions,
                if (callbackName == null) null else WebForgeActionFragment.PermissionListener { resultPermissions: Array<String>, grantResults: IntArray, extras: Bundle? ->
                    invokePermissionCallback(
                        callbackName,
                        WebForgeUtils.hasPermission(activity, permissions.asList())
                    )
                })
            if (!requesting && callbackName != null) {
                invokePermissionCallback(
                    callbackName,
                    WebForgeUtils.hasPermission(activity, permissions.asList())
                )
            }
        })
    }

    @JavascriptInterface
    open fun requestPermission(type: String?, callbackName: String?) {
        requestPermissionsWithCallback(type, callbackName)
    }

    private fun invokePermissionCallback(callbackName: String?, granted: Boolean) {
        if (TextUtils.isEmpty(callbackName)
            || !callbackName!!.matches("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$".toRegex())
        ) {
            return
        }
        val page = pageReference.get()
        if (page == null) {
            return
        }
        page.callJavaScript(callbackName, granted)
    }

    /** 刷新当前网页。  */
    @JavascriptInterface
    open fun reloadPage() {
        val page = pageReference.get()
        if (page != null && isActivityActive(activityReference.get())) {
            page.reload()
        }
    }

    /** 前进到下一条历史，没有前进历史时不操作。  */
    @JavascriptInterface
    open fun goForward() {
        runOnUiThread(Consumer { activity: Activity? ->
            val page = pageReference.get()
            if (page != null && !page.isClosed && page.view!!.canGoForward()) {
                page.view!!.goForward()
            }
        })
    }

    /** 停止当前网页加载。  */
    @JavascriptInterface
    open fun stopLoading() {
        runOnUiThread(Consumer { activity: Activity? ->
            val page = pageReference.get()
            if (page != null && !page.isClosed) {
                page.stop()
            }
        })
    }

    /** 控制宿主窗口常亮；离开需要常亮的页面时应传入 false。  */
    @JavascriptInterface
    open fun setKeepScreenOn(enabled: Boolean) {
        runOnUiThread(Consumer { activity: Activity? ->
            if (enabled) {
                activity!!.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity!!.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        })
    }

    /** 收起当前窗口的软键盘。  */
    @JavascriptInterface
    open fun hideKeyboard() {
        runOnUiThread(Consumer { activity: Activity? ->
            val manager = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            if (manager != null) {
                manager.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0)
            }
        })
    }

    /** 打开当前应用的系统设置，供用户手动调整权限等配置。  */
    @JavascriptInterface
    open fun openAppSettings() {
        val activity = activityReference.get()
        if (isActivityActive(activity)) {
            startExternalActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity!!.getPackageName(), null)
                ), "无法打开应用设置"
            )
        }
    }

    /** 显示短时原生提示。  */
    @JavascriptInterface
    open fun showToast(message: String?) {
        if (!TextUtils.isEmpty(message)) {
            runOnUiThread(Consumer { activity: Activity? ->
                Toast.makeText(
                    activity!!.getApplicationContext(),
                    message, Toast.LENGTH_SHORT
                ).show()
            })
        }
    }

    /** 将纯文本写入系统剪贴板。  */
    @JavascriptInterface
    open fun copyToClipboard(text: String?) {
        if (TextUtils.isEmpty(text)) {
            return
        }
        runOnUiThread(Consumer { activity: Activity? ->
            val clipboard =
                activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("webview", text))
            }
        })
    }

    /** 打开系统分享面板，分享纯文本。  */
    @JavascriptInterface
    open fun shareText(text: String?) {
        if (text == null || text.trim { it <= ' ' }.isEmpty()) {
            return
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        startExternalActivity(Intent.createChooser(intent, null), "无法打开系统分享")
    }

    /** 分享当前 HTTP/HTTPS 网页的标题与地址，不暴露本地文件地址。  */
    @JavascriptInterface
    open fun shareCurrentPage() {
        runOnUiThread(Consumer { activity: Activity? ->
            val page = pageReference.get()
            if (page == null || page.isClosed) {
                return@Consumer
            }
            val url = page.view!!.getUrl()
            if (parseWebUri(url) == null) {
                showToast("当前页面没有可分享的网页地址")
                return@Consumer
            }
            val title = page.view!!.getTitle()
            shareText(if (TextUtils.isEmpty(title)) url else title + "\n" + url)
        })
    }

    /** 打开短信编辑器，由用户确认发送；号码只接受单个电话号码。  */
    @JavascriptInterface
    open fun openSms(number: String?, message: String?) {
        if (number == null || !number.trim { it <= ' ' }.matches("[+]?[0-9][0-9 ()-]*".toRegex())) {
            showToast("请输入有效的电话号码")
            return
        }
        startExternalActivity(
            Intent(
                Intent.ACTION_SENDTO,
                Uri.fromParts("smsto", number.trim { it <= ' ' }, null)
            )
                .putExtra("sms_body", if (message == null) "" else message), "无法打开短信应用"
        )
    }

    /** 打开邮件编辑器，收件人、主题和正文均作为文本处理，由用户确认发送。  */
    @JavascriptInterface
    open fun openEmail(address: String?, subject: String?, body: String?) {
        if (address == null || !Patterns.EMAIL_ADDRESS.matcher(address.trim { it <= ' ' }).matches()) {
            showToast("请输入有效的邮箱地址")
            return
        }
        val uri = Uri.parse(
            ("mailto:" + Uri.encode(address.trim { it <= ' ' })
                    + "?subject=" + Uri.encode(if (subject == null) "" else subject)
                    + "&body=" + Uri.encode(if (body == null) "" else body))
        )
        startExternalActivity(Intent(Intent.ACTION_SENDTO, uri), "无法打开邮件应用")
    }

    /** 打开系统打印面板；API 19 起支持，须在网页加载完成后调用。  */
    @JavascriptInterface
    open fun printPage() {
        runOnUiThread(Consumer { activity: Activity? ->
            val page = pageReference.get()
            if (page == null || page.isClosed) {
                return@Consumer
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                showToast("当前系统不支持网页打印")
                return@Consumer
            }
            val manager = activity!!.getSystemService(Context.PRINT_SERVICE) as PrintManager?
            if (manager == null) {
                showToast("系统打印服务不可用")
                return@Consumer
            }
            val title = page.view!!.getTitle()
            val jobName: String = (if (android.text.TextUtils.isEmpty(title)) "WebForge" else title)!!
            manager.print(
                jobName, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    page.view!!.createPrintDocumentAdapter(jobName)
                else
                    page.view!!.createPrintDocumentAdapter(), PrintAttributes.Builder().build()
            )
        })
    }

    /** 打开拨号器并预填号码，由用户确认拨打。  */
    @JavascriptInterface
    open fun openDialer(number: String?) {
        if (number == null || !number.trim { it <= ' ' }.matches("[+]?[0-9][0-9 ()-]*".toRegex())) {
            showToast("请输入有效的电话号码")
            return
        }
        val uri = Uri.fromParts("tel", number.trim { it <= ' ' }, null)
        startExternalActivity(Intent(Intent.ACTION_DIAL, uri), "无法打开拨号器")
    }

    @get:JavascriptInterface
    val appVersionName: String
        /** 读取宿主应用的 versionName，读取失败时返回空字符串。  */
        get() {
            val activity = activityReference.get()
            if (activity == null) {
                return ""
            }
            try {
                val versionName = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName
                return if (versionName == null) "" else versionName
            } catch (e: PackageManager.NameNotFoundException) {
                return ""
            }
        }

    @Deprecated("")
    @JavascriptInterface
    open fun exitWebview() {
        closePage()
    }

    @Deprecated("")
    @JavascriptInterface
    open fun finishPagebyHistory() {
        goBackOrClose()
    }

    @Deprecated("")
    @JavascriptInterface
    open fun openBrowser(url: String?) {
        openExternalBrowser(url)
    }

    @Deprecated("")
    @JavascriptInterface
    open fun openGaode(
        slon: String?, slat: String?, sName: String?,
        dlon: String?, dlat: String?, dName: String?
    ) {
        openAmapRoute(slon, slat, sName, dlon, dlat, dName)
    }

    @Deprecated("")
    @JavascriptInterface
    open fun reload() {
        reloadPage()
    }

    @Deprecated("")
    @JavascriptInterface
    open fun toast(message: String?) {
        showToast(message)
    }

    @Deprecated("")
    @JavascriptInterface
    open fun copyText(text: String?) {
        copyToClipboard(text)
    }

    @get:JavascriptInterface
    val networkType: String
        /**
         * Returns none|wifi|4g|3g|2g|unknown.
         */
        get() {
            val activity = activityReference.get()
            if (activity == null) {
                return "none"
            }
            when (WebForgeUtils.checkNetworkType(activity)) {
                1 -> return "wifi"
                2 -> return "4g"
                3 -> return "3g"
                4 -> return "2g"
                else -> return if (WebForgeUtils.checkNetwork(activity)) "unknown" else "none"
            }
        }

    @get:JavascriptInterface
    val webForgeVersion: String
        get() = WebForgeConfig.WEBFORGE_VERSION

    private fun startExternalActivity(intent: Intent?, failureMessage: String?) {
        runOnUiThread(Consumer { activity: Activity? ->
            try {
                activity!!.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, failureMessage, e)
                Toast.makeText(
                    activity!!.getApplicationContext(),
                    failureMessage, Toast.LENGTH_SHORT
                ).show()
            } catch (e: SecurityException) {
                Log.w(TAG, failureMessage, e)
                Toast.makeText(
                    activity!!.getApplicationContext(),
                    failureMessage, Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun runOnUiThread(action: Consumer<Activity?>) {
        val activity = activityReference.get()
        if (isActivityActive(activity)) {
            activity!!.runOnUiThread(Runnable {
                if (isActivityActive(activity)) {
                    action.accept(activity)
                }
            })
        }
    }

    companion object {
        private const val TAG = "AndroidBridge"
        private fun isActivityActive(activity: Activity?): Boolean {
            return activity != null && !activity.isFinishing() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                    || !activity.isDestroyed())
        }

        private fun parseWebUri(url: String?): Uri? {
            if (TextUtils.isEmpty(url)) {
                return null
            }
            val uri = Uri.parse(url)
            val scheme = uri.getScheme()
            if (scheme == null) {
                return null
            }
            if (!"http".equals(scheme, ignoreCase = true) && !"https".equals(scheme, ignoreCase = true)) {
                return null
            }
            return if (TextUtils.isEmpty(uri.getHost())) null else uri
        }
    }
}
