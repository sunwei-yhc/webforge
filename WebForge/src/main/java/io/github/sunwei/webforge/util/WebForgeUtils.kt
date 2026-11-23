package io.github.sunwei.webforge.util

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.telephony.TelephonyManager
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.app.AppOpsManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.EnvironmentCompat
import androidx.fragment.app.FragmentActivity
import androidx.loader.content.CursorLoader
import com.google.android.material.snackbar.Snackbar
import io.github.sunwei.webforge.config.WebForgeConfig
import io.github.sunwei.webforge.filechooser.FileChooser.Companion.newBuilder
import io.github.sunwei.webforge.permission.Action
import io.github.sunwei.webforge.permission.PermissionInterceptor
import io.github.sunwei.webforge.permission.WebForgeActionFragment
import io.github.sunwei.webforge.permission.WebForgeActionFragment.Companion.start
import io.github.sunwei.webforge.ui.PageUiController
import io.github.sunwei.webforge.ui.WebParentLayout
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 */
open class WebForgeUtils {
    companion object {
        private val TAG: String = WebForgeUtils::class.java.getSimpleName()
        private var mHandler: Handler? = null

        @JvmStatic
        fun dp2px(context: Context, dipValue: Float): Int {
            val scale = context.getResources().getDisplayMetrics().density
            return (dipValue * scale + 0.5f).toInt()
        }

        @JvmStatic
        fun getWebForgeFilePath(context: Context): String? {
            if (!TextUtils.isEmpty(WebForgeConfig.WEBFORGE_FILE_PATH)) {
                return WebForgeConfig.WEBFORGE_FILE_PATH
            }
            val dir: String? = getDiskExternalCacheDir(context)
            val mFile = File(dir, WebForgeConfig.FILE_CACHE_PATH)
            try {
                if (!mFile.exists()) {
                    mFile.mkdirs()
                }
            } catch (throwable: Throwable) {
                LogUtils.i(TAG, "create dir exception")
            }
            LogUtils.i(TAG, "path:" + mFile.getAbsolutePath() + "  path:" + mFile.getPath())
            return mFile.getAbsolutePath().also { WebForgeConfig.WEBFORGE_FILE_PATH = it }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun createFileByName(context: Context, name: String, cover: Boolean): File? {
            val path: String? = getWebForgeFilePath(context)
            if (TextUtils.isEmpty(path)) {
                return null
            }
            val mFile = File(path, name)
            if (mFile.exists()) {
                if (cover) {
                    mFile.delete()
                    mFile.createNewFile()
                }
            } else {
                mFile.createNewFile()
            }
            return mFile
        }

        @JvmStatic
        fun checkNetworkType(context: Context): Int {
            val netType = 0
            //连接管理对象
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (manager == null) {
                return netType
            }
            //获取NetworkInfo对象
            @SuppressLint("MissingPermission") val networkInfo = manager.getActiveNetworkInfo()
            if (networkInfo == null) {
                return netType
            }
            when (networkInfo.getType()) {
                ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_WIMAX, ConnectivityManager.TYPE_ETHERNET -> return 1
                ConnectivityManager.TYPE_MOBILE -> when (networkInfo.getSubtype()) {
                    TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_EHRPD -> return 2
                    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B -> return 3
                    TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE -> return 4
                    else -> return netType
                }

                else -> return netType
            }
        }

        @JvmStatic
        fun getUriFromFile(context: Context, file: File): Uri? {
            var uri: Uri? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = getUriFromFileForN(context, file)
            } else {
                uri = Uri.fromFile(file)
            }
            return uri
        }

        fun getUriFromFileForN(context: Context, file: File): Uri? {
            val fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".WebForgeFileProvider", file)
            return fileUri
        }

        fun setIntentDataAndType(
            context: Context,
            intent: Intent,
            type: String?,
            file: File,
            writeAble: Boolean
        ) {
            if (Build.VERSION.SDK_INT >= 24) {
                intent.setDataAndType(getUriFromFile(context, file), type)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (writeAble) {
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            } else {
                intent.setDataAndType(Uri.fromFile(file), type)
            }
        }

        fun getDiskExternalCacheDir(context: Context): String? {
            val mFile = context.getExternalCacheDir()
            if (Environment.MEDIA_MOUNTED == EnvironmentCompat.getStorageState(mFile!!)) {
                return mFile.getAbsolutePath()
            }
            return null
        }

        private fun getMIMEType(f: File): String {
            var type = ""
            val fName = f.getName()
            /* 取得扩展名 */
            val end = fName.substring(fName.lastIndexOf(".") + 1, fName.length).lowercase(Locale.getDefault())
            /* 依扩展名的类型决定MimeType */
            if (end == "pdf") {
                type = "application/pdf" //
            } else if (end == "m4a" || end == "mp3" || end == "mid" ||
                end == "xmf" || end == "ogg" || end == "wav"
            ) {
                type = "audio/*"
            } else if (end == "3gp" || end == "mp4") {
                type = "video/*"
            } else if (end == "jpg" || end == "gif" || end == "png" ||
                end == "jpeg" || end == "bmp"
            ) {
                type = "image/*"
            } else if (end == "apk") {
                type = "application/vnd.android.package-archive"
            } else if (end == "pptx" || end == "ppt") {
                type = "application/vnd.ms-powerpoint"
            } else if (end == "docx" || end == "doc") {
                type = "application/vnd.ms-word"
            } else if (end == "xlsx" || end == "xls") {
                type = "application/vnd.ms-excel"
            } else {
                type = "*/*"
            }
            return type
        }

        @JvmStatic
        fun show(
            parent: View,
            text: CharSequence?,
            duration: Int,
            @ColorInt textColor: Int,
            @ColorInt bgColor: Int,
            actionText: CharSequence?,
            @ColorInt actionTextColor: Int,
            listener: View.OnClickListener?
        ) {
            val spannableString = SpannableString(text)
            val colorSpan = ForegroundColorSpan(textColor)
            spannableString.setSpan(colorSpan, 0, spannableString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val snackbar = Snackbar.make(parent, spannableString, duration)
            val view = snackbar.getView()
            view.setBackgroundColor(bgColor)
            if (actionText != null && actionText.length > 0 && listener != null) {
                snackbar.setActionTextColor(actionTextColor)
                snackbar.setAction(actionText, listener)
            }
            snackbar.show()
        }

        @JvmStatic
        fun checkNetwork(context: Context): Boolean {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (connectivity == null) {
                return false
            }
            @SuppressLint("MissingPermission") val info = connectivity.getActiveNetworkInfo()
            return info != null && info.isConnected()
        }

        @JvmStatic
        fun clearCacheFolder(dir: File?, numDays: Int): Int {
            var deletedFiles = 0
            if (dir != null) {
                Log.i("Info", "dir:" + dir.getAbsolutePath())
            }
            if (dir != null && dir.isDirectory()) {
                try {
                    for (child in dir.listFiles()) {
                        //first delete subdirectories recursively

                        if (child.isDirectory()) {
                            deletedFiles += clearCacheFolder(child, numDays)
                        }

                        //then delete the files and subdirectories in this dir
                        //only empty directories can be deleted, so subdirs have been done first
                        if (child.lastModified() < Date().getTime() - numDays * DateUtils.DAY_IN_MILLIS) {
                            Log.i(TAG, "file name:" + child.getName())
                            if (child.delete()) {
                                deletedFiles++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Info", String.format("Failed to clean the cache, result %s", e.message))
                }
            }
            return deletedFiles
        }

        fun clearCache(context: Context, numDays: Int) {
            Log.i("Info", String.format("Starting cache prune, deleting files older than %d days", numDays))
            val numDeletedFiles: Int = clearCacheFolder(context.getCacheDir(), numDays)
            Log.i("Info", String.format("Cache pruning completed, %d files deleted", numDeletedFiles))
        }

        @JvmStatic
        fun uriToPath(activity: Activity?, uris: Array<Uri>?): Array<String?>? {
            if (activity == null || uris == null || uris.size == 0) {
                return null
            }
            try {
                val paths = arrayOfNulls<String>(uris.size)
                var i = 0
                for (mUri in uris) {
                    paths[i++] = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) getFileAbsolutePath(activity, mUri) else getRealPathBelowVersion(activity, mUri)
                }
                return paths
            } catch (throwable: Throwable) {
                if (LogUtils.isDebug()) {
                    throwable.printStackTrace()
                }
            }
            return null
        }

        private fun getRealPathBelowVersion(context: Context, uri: Uri): String? {
            var filePath: String? = null
            LogUtils.i(TAG, "method -> getRealPathBelowVersion " + uri + "   path:" + uri.getPath() + "    getAuthority:" + uri.getAuthority())
            val projection = arrayOf<String?>(MediaStore.Images.Media.DATA)
            val loader = CursorLoader(
                context, uri, projection, null,
                null, null
            )
            val cursor = loader.loadInBackground()
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        filePath = cursor.getString(cursor.getColumnIndexOrThrow(projection[0]))
                    }
                } finally {
                    cursor.close()
                }
            }
            if (filePath == null) {
                filePath = uri.getPath()
            }
            return filePath
        }

        @JvmStatic
        fun createImageFile(context: Context): File? {
            var mFile: File? = null
            try {
                val timeStamp =
                    SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
                val imageName = String.format("aw_%s.jpg", timeStamp)
                mFile = createFileByName(context, imageName, true)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return mFile
        }

        @JvmStatic
        fun createVideoFile(context: Context): File? {
            var mFile: File? = null
            try {
                val timeStamp =
                    SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
                val imageName = String.format("aw_%s.mp4", timeStamp) //默认生成mp4
                mFile = createFileByName(context, imageName, true)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return mFile
        }

        @JvmStatic
        fun closeIO(closeable: Closeable?) {
            try {
                if (closeable != null) {
                    closeable.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @TargetApi(19)
        fun getFileAbsolutePath(context: Activity?, fileUri: Uri?): String? {
            if (context == null || fileUri == null) {
                return null
            }
            //		LogUtils.i(TAG, "getAuthority:" + fileUri.getAuthority() + "  getHost:" + fileUri.getHost() + "   getPath:" + fileUri.getPath() + "  getScheme:" + fileUri.getScheme() + "  query:" + fileUri.getQuery());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, fileUri)) {
                if (isExternalStorageDocument(fileUri)) {
                    val docId = DocumentsContract.getDocumentId(fileUri)
                    val split: Array<String?> = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    }
                } else if (isDownloadsDocument(fileUri)) {
                    val id = DocumentsContract.getDocumentId(fileUri)
                    val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id.toLong())
                    return getDataColumn(context, contentUri, null, null)
                } else if (isMediaDocument(fileUri)) {
                    val docId = DocumentsContract.getDocumentId(fileUri)
                    val split: Array<String?> = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]

                    var contentUri: Uri? = null
                    if ("image" == type) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else if ("video" == type) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else if ("audio" == type) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val selection = MediaStore.Images.Media._ID + "=?"
                    val selectionArgs = arrayOf<String?>(split[1])
                    return Companion.getDataColumn(context, contentUri!!, selection, selectionArgs)
                } else {
                }
            } // MediaStore (and general)
            else if (fileUri.getAuthority().equals(context.getPackageName() + ".WebForgeFileProvider", ignoreCase = true)) {
                val path = fileUri.getPath()
                val index = path!!.lastIndexOf("/")
                return getWebForgeFilePath(context) + File.separator + path.substring(index + 1, path.length)
            } else if ("content".equals(fileUri.getScheme(), ignoreCase = true)) {
                // Return the remote address
                if (isGooglePhotosUri(fileUri)) {
                    return fileUri.getLastPathSegment()
                }
                return getDataColumn(context, fileUri, null, null)
            } else if ("file".equals(fileUri.getScheme(), ignoreCase = true)) {
                return fileUri.getPath()
            }
            return null
        }

        fun getDataColumn(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String?>?): String? {
            var cursor: Cursor? = null
            val projection = arrayOf<String?>(MediaStore.Images.Media.DATA)
            try {
                cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    return cursor.getString(index)
                }
            } finally {
                if (cursor != null) {
                    cursor.close()
                }
            }
            return null
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is ExternalStorageProvider.
         */
        fun isExternalStorageDocument(uri: Uri): Boolean {
            return "com.android.externalstorage.documents" == uri.getAuthority()
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is DownloadsProvider.
         */
        fun isDownloadsDocument(uri: Uri): Boolean {
            return "com.android.providers.downloads.documents" == uri.getAuthority()
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is MediaProvider.
         */
        fun isMediaDocument(uri: Uri): Boolean {
            return "com.android.providers.media.documents" == uri.getAuthority()
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is Google Photos.
         */
        fun isGooglePhotosUri(uri: Uri): Boolean {
            return "com.google.android.apps.photos.content" == uri.getAuthority()
        }

        @JvmStatic
        fun getCommonFileIntentCompat(context: Context, file: File): Intent {
            val mIntent = Intent().setAction(Intent.ACTION_VIEW)
            setIntentDataAndType(context, mIntent, getMIMEType(file), file, false)
            return mIntent
        }

        @JvmStatic
        fun getIntentCaptureCompat(context: Context, file: File): Intent {
            val mIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val mUri: Uri? = getUriFromFile(context, file)
            mIntent.addCategory(Intent.CATEGORY_DEFAULT)
            mIntent.putExtra(MediaStore.EXTRA_OUTPUT, mUri)
            return mIntent
        }

        @JvmStatic
        fun getIntentVideoCompat(context: Context, file: File): Intent {
            val mIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            val mUri: Uri? = getUriFromFile(context, file)
            mIntent.addCategory(Intent.CATEGORY_DEFAULT)
            mIntent.putExtra(MediaStore.EXTRA_OUTPUT, mUri)
            return mIntent
        }

        @get:JvmStatic
        val isUIThread: Boolean
            get() = Looper.myLooper() == Looper.getMainLooper()

        @JvmStatic
        fun isEmptyCollection(collection: MutableCollection<*>?): Boolean {
            return collection == null || collection.isEmpty()
        }

        private var mToast: Toast? = null
        @JvmStatic
        fun toastShowShort(context: Context, msg: String?) {
            if (mToast == null) {
                mToast = Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT)
            } else {
                mToast!!.setText(msg)
            }
            mToast!!.show()
        }

        @JvmStatic
        fun hasPermission(context: Context, vararg permissions: String): Boolean {
            return hasPermission(context, Arrays.asList<String?>(*permissions))
        }

        @JvmStatic
        fun hasPermission(context: Context, permissions: List<String>): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return true
            }
            for (permission in permissions) {
                var result = ContextCompat.checkSelfPermission(context, permission)
                if (result == PackageManager.PERMISSION_DENIED) {
                    return false
                }
                val op = AppOpsManagerCompat.permissionToOp(permission)
                if (TextUtils.isEmpty(op)) {
                    continue
                }
                result = AppOpsManagerCompat.noteProxyOp(context, op!!, context.getPackageName())
                if (result != AppOpsManagerCompat.MODE_ALLOWED) {
                    return false
                }
            }
            return true
        }

        @JvmStatic
        fun getDeniedPermissions(activity: Activity, permissions: Array<String>?): MutableList<String> {
            if (permissions == null || permissions.size == 0) {
                return ArrayList()
            }
            val deniedPermissions: MutableList<String> = ArrayList(permissions.size)
            for (i in permissions.indices) {
                if (!Companion.hasPermission(activity, permissions[i])) {
                    deniedPermissions.add(permissions[i])
                }
            }
            return deniedPermissions
        }

        /**
         * 主动申请运行时权限（例如 H5 使用录音、通话时调用）。
         *
         * @param activity   发起申请的 Activity，需要是 FragmentActivity
         * @param permissions 需要申请的权限
         * @param listener   申请结果回调，可为 null
         * @return true 表示已经发起申请（或者无需申请，回调会立即执行），false 表示无法发起申请
         */
        @JvmStatic
        fun requestPermissions(
            activity: Activity, permissions: Array<String>?,
            listener: WebForgeActionFragment.PermissionListener?
        ): Boolean {
            if (activity == null || permissions == null || permissions.size == 0) {
                return false
            }
            val deniedPermissions: MutableList<String> = getDeniedPermissions(activity, permissions)
            if (deniedPermissions.isEmpty()) {
                //已经授权，直接回调，业务方可以立即执行后续逻辑
                if (listener != null) {
                    listener.onRequestPermissionsResult(permissions, buildGrantResults(permissions.size), Bundle())
                }
                return true
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                //6.0 以下没有运行时权限，可直接使用
                if (listener != null) {
                    listener.onRequestPermissionsResult(permissions, buildGrantResults(permissions.size), Bundle())
                }
                return true
            }
            if (activity !is FragmentActivity) {
                //没有 FragmentActivity 无法使用 WebForge 的权限申请，交给业务方自行申请
                return false
            }
            val requestPermissions = deniedPermissions.toTypedArray()
            val action = Action.createPermissionsAction(requestPermissions)
            action.permissionListener = listener
            try {
                start(activity, action)
            } catch (ignore: Throwable) {
                if (LogUtils.isDebug()) {
                    ignore.printStackTrace()
                }
                return false
            }
            return true
        }

        /**
         * 构造全部授予的授权结果，用于无需弹窗时的立即回调。
         */
        private fun buildGrantResults(length: Int): IntArray {
            val grantResults = IntArray(length)
            for (i in 0 until length) {
                grantResults[i] = PackageManager.PERMISSION_GRANTED
            }
            return grantResults
        }

        @JvmStatic
        fun getWebForgeUIControllerByWebView(webView: WebView): PageUiController? {
            val mWebParentLayout: WebParentLayout = getWebParentLayoutByWebView(webView)
            return mWebParentLayout.controller
        }

        //获取应用的名称
        @JvmStatic
        fun getApplicationName(context: Context): String {
            var packageManager: PackageManager? = null
            var applicationInfo: ApplicationInfo? = null
            try {
                packageManager = context.getApplicationContext().getPackageManager()
                applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0)
            } catch (e: PackageManager.NameNotFoundException) {
                applicationInfo = null
            }
            val applicationName =
                packageManager!!.getApplicationLabel(applicationInfo!!) as String
            return applicationName
        }

        fun getWebParentLayoutByWebView(webView: WebView): WebParentLayout {
            var parent = webView.getParent()
            while (parent is ViewGroup) {
                if (parent is WebParentLayout) {
                    return parent
                }
                parent = parent.getParent()
            }
            throw IllegalStateException("WebView is outside its WebPage layout")
        }

        @JvmStatic
        fun runInUiThread(runnable: Runnable) {
            if (mHandler == null) {
                mHandler = Handler(Looper.getMainLooper())
            }
            mHandler!!.post(runnable)
        }

        @JvmStatic
        @SuppressLint("NewApi")
        fun showFileChooserCompat(
            activity: Activity?,
            webView: WebView?,
            valueCallbacks: ValueCallback<Array<Uri>>?,
            fileChooserParams: WebChromeClient.FileChooserParams?,
            permissionInterceptor: PermissionInterceptor?,
            valueCallback: ValueCallback<*>?,
            mimeType: String?,
            jsChannelCallback: Handler.Callback?
        ): Boolean {
            try {
                // 文件选择器已内置，直接调用避免反射开销和宿主混淆后的类名失效。
                val builder = newBuilder(activity, webView)
                    .setPermissionInterceptor(permissionInterceptor)
                if (valueCallbacks != null) {
                    builder.setUriValueCallbacks(valueCallbacks)
                }
                if (fileChooserParams != null) {
                    builder.setFileChooserParams(fileChooserParams)
                }
                if (valueCallback != null) {
                    @Suppress("UNCHECKED_CAST")
                    builder.setUriValueCallback(valueCallback as ValueCallback<Uri?>)
                }
                if (!TextUtils.isEmpty(mimeType)) {
                    builder.setAcceptType(mimeType)
                }
                if (jsChannelCallback != null) {
                    builder.setJsChannelCallback(jsChannelCallback)
                }
                builder.build().openFileChooser()
            } catch (error: RuntimeException) {
                if (LogUtils.isDebug()) {
                    error.printStackTrace()
                }
                if (valueCallbacks != null) {
                    return false
                }
                if (valueCallback != null) {
                    valueCallback.onReceiveValue(null)
                }
            }
            return true
        }
    }
}
