package io.github.sunwei.webforge.filechooser

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.Parcelable
import android.os.SystemClock
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import io.github.sunwei.webforge.R
import io.github.sunwei.webforge.config.WebForgeConfig
import io.github.sunwei.webforge.filechooser.FileCompressor.Companion.instance
import io.github.sunwei.webforge.permission.Action
import io.github.sunwei.webforge.permission.PermissionInterceptor
import io.github.sunwei.webforge.permission.WebForgeActionFragment
import io.github.sunwei.webforge.permission.WebForgePermissions
import io.github.sunwei.webforge.ui.PageUiController
import io.github.sunwei.webforge.util.WebForgeUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.Arrays
import java.util.Queue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor

/**
 */
open class FileChooser(builder: Builder) {
    /**
     * Activity
     */
    private lateinit var mActivity: Activity

    /**
     * ValueCallback
     */
    private var mUriValueCallback: ValueCallback<Uri?>?

    /**
     * ValueCallback<Uri></Uri>[]> After LOLLIPOP
     */
    private var mUriValueCallbacks: ValueCallback<Array<Uri>>?

    /**
     * WebChromeClient.FileChooserParams 封装了 Intent ，mAcceptType  等参数
     */
    private val mFileChooserParams: WebChromeClient.FileChooserParams?

    /**
     * 如果是通过 JavaScript 打开文件选择器 ，那么 mJsChannelCallback 不能为空
     */
    private var mJsChannelCallback: JsChannelCallback? = null

    /**
     * 是否为Js Channel
     */
    private var mJsChannel = false

    /**
     * 当前 WebView
     */
    private lateinit var mWebView: WebView

    /**
     * 是否为 Camera State
     */
    private var mCameraState = false

    /**
     * 是否调用摄像头后  调用的是摄像模式  默认是拍照
     */
    private var mVideoState = false

    /**
     * 权限拦截
     */
    private val mPermissionInterceptor: PermissionInterceptor?

    /**
     * FROM_INTENTION_CODE 用于表示当前Action
     */
    private val FROM_INTENTION_CODE = 21

    /**
     * 当前 PageUiController
     */
    private var mWebForgeUIController: WeakReference<PageUiController?>? = null

    /**
     * 选择文件类型
     */
    private var mAcceptType: String? = "*/*"
    private var mOpened = false
    open fun openFileChooser() {
        if (!WebForgeUtils.isUIThread) {
            WebForgeUtils.runInUiThread(object : Runnable {
                override fun run() {
                    openFileChooser()
                }
            })
            return
        }
        if (mOpened) {
            return
        }
        mOpened = true
        if (mActivity == null || mActivity.isFinishing() || mWebView == null || (!mJsChannel && mUriValueCallback == null && mUriValueCallbacks == null)) {
            cancel()
            return
        }
        openFileChooserInternal()
    }

    private fun fileChooser() {
        // Storage Access Framework grants access to the selected document
        // itself. Asking for READ_EXTERNAL_STORAGE here breaks uploads on
        // Android 13+, where that permission no longer applies to media.
        chooserAction()
    }

    private fun chooserAction() {
        val mAction = Action()
        mAction.action = Action.ACTION_FILE
        mAction.chooserListener = this.chooserListener
        try {
            mAction.intent = this.fileChooserIntent
            WebForgeActionFragment.start(mActivity, mAction)
        } catch (throwable: Throwable) {
            if (WebForgeConfig.DEBUG) {
                throwable.printStackTrace()
            }
            cancel()
        }
    }

    private val fileChooserIntent: Intent?
        get() {
            val params = mFileChooserParams
            val createdIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                params?.createIntent()
            } else {
                null
            }
            if (createdIntent != null) {
                // 多选
                if (params!!.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                    createdIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }

                //			mIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (params.getAcceptTypes() != null && params.getAcceptTypes().size > 1) {
                    createdIntent.putExtra(Intent.EXTRA_MIME_TYPES, params.getAcceptTypes())
                }
                if (createdIntent.getAction() == Intent.ACTION_GET_CONTENT) {
                    createdIntent.setAction(Intent.ACTION_OPEN_DOCUMENT)
                }
                return createdIntent
            }

            val i = Intent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                i.setAction(Intent.ACTION_OPEN_DOCUMENT)
            } else {
                i.setAction(Intent.ACTION_GET_CONTENT)
            }
            i.addCategory(Intent.CATEGORY_OPENABLE)
            if (TextUtils.isEmpty(this.mAcceptType)) {
                i.setType("*/*")
            } else {
                i.setType(this.mAcceptType)
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return Intent.createChooser(i, "")
        }

    private val chooserListener: WebForgeActionFragment.ChooserListener
        get() = object : WebForgeActionFragment.ChooserListener {
            override fun onChoiceResult(requestCode: Int, resultCode: Int, data: Intent?) {
                onIntentResult(requestCode, resultCode, data)
            }
        }


    private fun openFileChooserInternal() {
        var needCamera = false
        var needVideo = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && this.mFileChooserParams != null && this.mFileChooserParams.getAcceptTypes() != null) {
            val types = this.mFileChooserParams.getAcceptTypes()
            for (typeTmp in types) {
                if (TextUtils.isEmpty(typeTmp)) {
                    continue
                }
                if (typeTmp!!.contains("*/") || typeTmp.contains("image/")) {
                    needCamera = true
                }
                if (typeTmp.contains("video/")) {
                    mVideoState = true
                    needVideo = true
                }
            }
            if (this.mFileChooserParams.isCaptureEnabled()
                && (needCamera || needVideo)
            ) {
                mCameraState = true
                mVideoState = needVideo && !needCamera
                onCameraAction()
                return
            }
        }

        if (this.mWebForgeUIController!!.get() != null) {
            this.mWebForgeUIController!!
                .get()!!
                .onSelectItemsPrompt(
                    this.mWebView, mWebView!!.getUrl(),
                    arrayOf<String>(
                        mActivity!!.getString(R.string.webforge_camera),
                        mActivity.getString(R.string.webforge_file_chooser)
                    ), this.callBack
                )
        } else {
            // A file chooser must always complete its callback, even when a
            // business provides no custom UI controller.
            chooserAction()
        }
    }


    private val callBack: Handler.Callback
        get() = object : Handler.Callback {
            override fun handleMessage(msg: Message): Boolean {
                when (msg.what) {
                    0 -> {
                        mCameraState = true
                        onCameraAction()
                    }

                    1 -> {
                        mCameraState = false
                        fileChooser()
                    }

                    else -> cancel()
                }
                return true
            }
        }


    private fun onCameraAction() {
        if (mActivity == null) {
            return
        }

        if (mPermissionInterceptor != null) {
            if (mPermissionInterceptor.intercept(this@FileChooser.mWebView!!.getUrl(), WebForgePermissions.CAMERA, "camera")) {
                cancel()
                return
            }
        }
        val mAction = Action()
        val deniedPermissions = checkNeedPermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deniedPermissions.isNotEmpty()) {
            mAction.action = Action.ACTION_PERMISSION
            mAction.setPermissions(deniedPermissions.toTypedArray())
            mAction.fromIntention = FROM_INTENTION_CODE shr 3
            mAction.permissionListener = this.mPermissionListener
            WebForgeActionFragment.start(mActivity, mAction)
        } else {
            openCameraAction()
        }
    }

    private fun checkNeedPermission(): MutableList<String> {
        val deniedPermissions: MutableList<String> = ArrayList()

        if (!WebForgeUtils.hasPermission(mActivity, WebForgePermissions.CAMERA.asList())) {
            deniedPermissions.add(WebForgePermissions.CAMERA[0])
        }
        return deniedPermissions
    }

    private fun openCameraAction() {
        val mAction = Action()
        if (mVideoState) {  //调用摄像
            mAction.action = Action.ACTION_VIDEO
        } else {
            mAction.action = Action.ACTION_CAMERA
        }
        mAction.chooserListener = this.chooserListener
        WebForgeActionFragment.start(mActivity, mAction)
    }

    private val mPermissionListener: WebForgeActionFragment.PermissionListener by lazy {
        WebForgeActionFragment.PermissionListener { permissions: Array<String>, grantResults: IntArray, extras: Bundle? ->
            permissionResult(
                WebForgeUtils.hasPermission(mActivity, permissions.asList()),
                extras?.getInt(WebForgeActionFragment.KEY_FROM_INTENTION) ?: 0
            )
        }
    }

    init {
        this.mActivity = requireNotNull(builder.mActivity) { "FileChooser requires an Activity" }
        this.mUriValueCallback = builder.mUriValueCallback
        this.mUriValueCallbacks = builder.mUriValueCallbacks
        this.mJsChannel = builder.mJsChannel
        this.mFileChooserParams = builder.mFileChooserParams
        if (this.mJsChannel) {
            this.mJsChannelCallback = JsChannelCallback.create(builder.mJsChannelCallback)
        }
        this.mWebView = requireNotNull(builder.mWebView) { "FileChooser requires a WebView" }
        this.mPermissionInterceptor = builder.mPermissionInterceptor
        this.mAcceptType = builder.mAcceptType
        this.mWebForgeUIController = WeakReference(WebForgeUtils.getWebForgeUIControllerByWebView(this.mWebView))
    }


    private fun permissionResult(grant: Boolean, fromIntention: Int) {
        if (fromIntention == FROM_INTENTION_CODE shr 2) {
            if (grant) {
                chooserAction()
            } else {
                cancel()

                if (null != mWebForgeUIController!!.get()) {
                    mWebForgeUIController!!
                        .get()!!
                        .onPermissionsDeny(
                            WebForgePermissions.MEDIA,
                            WebForgePermissions.ACTION_MEDIA,
                            "Open file chooser"
                        )
                }
            }
        } else if (fromIntention == FROM_INTENTION_CODE shr 3) {
            if (grant) {
                openCameraAction()
            } else {
                cancel()
                if (null != mWebForgeUIController!!.get()) {
                    mWebForgeUIController!!
                        .get()!!
                        .onPermissionsDeny(
                            WebForgePermissions.CAMERA,
                            WebForgePermissions.ACTION_CAMERA,
                            "Take photo"
                        )
                }
            }
        }
    }

    open fun onIntentResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (REQUEST_CODE != requestCode) {
            return
        }

        //用户已经取消
        if (resultCode == Activity.RESULT_CANCELED || data == null) {
            cancel()
            return
        }

        if (resultCode != Activity.RESULT_OK) {
            cancel()
            return
        }

        //通过Js获取文件
        if (mJsChannel) {
            val callback = mJsChannelCallback
            mJsChannelCallback = null
            convertFileAndCallback(
                if (mCameraState) arrayOf(data.getParcelableExtra<Uri>(WebForgeActionFragment.KEY_URI) ?: Uri.EMPTY) else processData(data),
                callback
            )
            return
        }

        //5.0以上系统通过input标签获取文件
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val callback = mUriValueCallbacks
            mUriValueCallbacks = null
            aboveLollipopCheckFilesAndCallback(
                if (mCameraState) arrayOf(data.getParcelableExtra<Uri>(WebForgeActionFragment.KEY_URI) ?: Uri.EMPTY) else processData(data),
                mCameraState,
                callback
            )
            return
        }


        //4.4以下系统通过input标签获取文件
        if (mUriValueCallback == null) {
            cancel()
            return
        }

        if (mCameraState) {
//            mUriValueCallback.onReceiveValue((Uri) data.getParcelableExtra(KEY_URI));
            fileCompressAndValuesCallback(data.getParcelableExtra<Parcelable?>(WebForgeActionFragment.KEY_URI) as Uri?, mUriValueCallback)
            mUriValueCallback = null
        } else {
            belowLollipopUriCallback(data)
        }


        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            aboveLollipopCheckFilesAndCallback(mCameraState ? new Uri[]{data.getParcelableExtra(KEY_URI)} : processData(data));
        else if (mJsChannel)
            convertFileAndCallback(mCameraState ? new Uri[]{data.getParcelableExtra(KEY_URI)} : processData(data));
        else {
            if (mCameraState && mUriValueCallback != null)
                mUriValueCallback.onReceiveValue((Uri) data.getParcelableExtra(KEY_URI));
            else
                belowLollipopUriCallback(data);
        }*/
    }

    private fun cancel() {
        val jsChannelCallback = mJsChannelCallback
        mJsChannelCallback = null
        if (jsChannelCallback != null) {
            jsChannelCallback.call(null)
        }
        if (mUriValueCallback != null) {
            try {
                mUriValueCallback!!.onReceiveValue(null)
                mUriValueCallback = null
            } catch (ignored: Throwable) {
                if (WebForgeConfig.DEBUG) {
                    ignored.printStackTrace()
                }
            }
        }
        if (mUriValueCallbacks != null) {
            try {
                mUriValueCallbacks!!.onReceiveValue(null)
                mUriValueCallbacks = null
            } catch (ignored: Throwable) {
                if (WebForgeConfig.DEBUG) {
                    ignored.printStackTrace()
                }
            }
        }
        return
    }


    private fun belowLollipopUriCallback(data: Intent?) {
        if (data == null) {
            if (mUriValueCallback != null) {
                mUriValueCallback!!.onReceiveValue(Uri.EMPTY)
                mUriValueCallback = null
            }
            return
        }
        val mUri = data.getData()
        if (mUriValueCallback != null) {
//            mUriValueCallback.onReceiveValue(mUri);
            fileCompressAndValuesCallback(mUri, mUriValueCallback)
            mUriValueCallback = null
        }
    }

    private fun processData(data: Intent?): Array<Uri>? {
        if (data == null) {
            return null
        }
        val target = data.getDataString()
        if (!TextUtils.isEmpty(target)) {
            return arrayOf(Uri.parse(target))
        }
        var mClipData: ClipData? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mClipData = data.getClipData()
        }
        if (mClipData != null && mClipData.getItemCount() > 0) {
            val datas = Array(mClipData.getItemCount()) { Uri.EMPTY }
            for (i in 0 until mClipData.getItemCount()) {
                val mItem = mClipData.getItemAt(i)
                datas[i] = mItem.getUri() ?: Uri.EMPTY
            }
            return datas
        }
        return null
    }

    private fun convertFileAndCallback(
        uris: Array<Uri>?,
        jsChannelCallback: JsChannelCallback?
    ) {
        var paths: Array<String?>? = null
        if (uris == null || uris.size == 0 || (WebForgeUtils.uriToPath(mActivity, uris).also { paths = it }) == null || paths!!.size == 0) {
            if (jsChannelCallback != null) {
                jsChannelCallback.call(null)
            }
            return
        }
        instance.fileCompress("customize", uris, object : ValueCallback<Array<Uri>> {
            override fun onReceiveValue(value: Array<Uri>?) {
                val compressFilePath = WebForgeUtils.uriToPath(mActivity, value)
                if (compressFilePath == null || compressFilePath.size == 0) {
                    if (jsChannelCallback != null) {
                        jsChannelCallback.call(null)
                    }
                    return
                }
                var sum: Long = 0
                for (path in compressFilePath) {
                    if (TextUtils.isEmpty(path)) {
                        continue
                    }
                    val mFile = File(path)
                    if (!mFile.exists()) {
                        continue
                    }
                    sum += mFile.length()
                }

                if (sum > WebForgeConfig.MAX_FILE_LENGTH) {
                    if (mWebForgeUIController!!.get() != null) {
                        mWebForgeUIController!!.get()!!.onShowMessage(
                            mActivity!!.getString(R.string.webforge_max_file_length_limit, (WebForgeConfig.MAX_FILE_LENGTH / 1024 / 1024).toString() + ""),
                            "convertFileAndCallback"
                        )
                    }
                    if (jsChannelCallback != null) {
                        jsChannelCallback.call(null)
                    }
                    return
                }

                if (jsChannelCallback != null) {
                    AsyncTask.THREAD_POOL_EXECUTOR.execute(
                        CovertFileThread(jsChannelCallback, compressFilePath)
                    )
                }
            }
        })
    }

    /**
     * 经过多次的测试，在小米 MIUI ， 华为 ，多部分为 Android 6.0 左右系统相机获取到的文件
     * length为0 ，导致前端 ，获取到的文件， 作预览的时候不正常 ，等待5S左右文件又正常了 ， 所以这里做了阻塞等待处理，
     *
     * @param datas
     * @param isCamera
     */
    private fun aboveLollipopCheckFilesAndCallback(
        datas: Array<Uri>?, isCamera: Boolean,
        valueCallback: ValueCallback<Array<Uri>>?
    ) {
        if (valueCallback == null) {
            cancel()
            return
        }
        if (null != datas && datas.size > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val contentResolver = mActivity!!.getContentResolver()
            val takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            for (i in datas.indices) {
                try {
                    contentResolver.takePersistableUriPermission(datas[i]!!, takeFlags)
                } catch (throwable: Throwable) {
                    if (WebForgeConfig.DEBUG) {
                        throwable.printStackTrace()
                    }
                }
            }
        }
        if (!isCamera) {
            fileCompressAndValuesCallback(
                if (datas == null) emptyArray() else datas, valueCallback
            )
            return
        }

        if (mWebForgeUIController!!.get() == null) {
            valueCallback.onReceiveValue(null)
            return
        }
        val paths = WebForgeUtils.uriToPath(mActivity, datas)
        if (paths == null || paths.size == 0) {
            valueCallback.onReceiveValue(null)
            return
        }
        val path = paths[0]
        mWebForgeUIController!!.get()!!.onLoading(mActivity!!.getString(R.string.webforge_loading))
        AsyncTask.THREAD_POOL_EXECUTOR.execute(
            WaitPhotoRunnable(
                path, AboveLCallback(valueCallback, datas, mWebForgeUIController)
            )
        )
    }

    private class AboveLCallback(private val mValueCallback: ValueCallback<Array<Uri>>?, private val mUris: Array<Uri>?, private val controller: WeakReference<PageUiController?>?) :
        Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            WebForgeUtils.runInUiThread(object : Runnable {
                override fun run() {
                    this@AboveLCallback.safeHandleMessage(msg)
                }
            })
            return false
        }

        fun safeHandleMessage(msg: Message) {
            if (mValueCallback != null) {
                fileCompressAndValuesCallback(mUris, mValueCallback)
                //                mValueCallback.onReceiveValue(mUris);
            }
            if (controller != null && controller.get() != null) {
                controller.get()!!.onCancelLoading()
            }
        }
    }

    private class WaitPhotoRunnable(private var path: String?, private var mCallback: Handler.Callback?) : Runnable {
        override fun run() {
            if (TextUtils.isEmpty(path) || !File(path).exists()) {
                if (mCallback != null) {
                    mCallback!!.handleMessage(Message.obtain(null, -1))
                }
                return
            }
            var ms = 0

            while (ms <= MAX_WAIT_PHOTO_MS) {
                ms += 300
                SystemClock.sleep(300)
                val mFile = File(path)
                if (mFile.length() > 0) {
                    if (mCallback != null) {
                        mCallback!!.handleMessage(Message.obtain(null, 1))
                        mCallback = null
                    }
                    break
                }
            }

            if (ms > MAX_WAIT_PHOTO_MS) {
                if (mCallback != null) {
                    mCallback!!.handleMessage(Message.obtain(null, -1))
                }
            }
            mCallback = null
            path = null
        }
    }

    internal class EncodeFileRunnable(private val filePath: String, private val mQueue: Queue<FileParcel>, private val mCountDownLatch: CountDownLatch, private val id: Int) : Runnable {
        override fun run() {
            var `is`: InputStream? = null
            var os: ByteArrayOutputStream? = null
            try {
                val mFile = File(filePath)
                Log.e(TAG, "encode file:" + mFile.length())
                if (mFile.exists()) {
                    `is` = FileInputStream(mFile)
                    if (`is` == null) {
                        return
                    }
                    os = ByteArrayOutputStream()
                    val b = ByteArray(1024)
                    var len: Int
                    while ((`is`.read(b, 0, 1024).also { len = it }) != -1) {
                        os.write(b, 0, len)
                    }
                    mQueue.offer(FileParcel(id, mFile.getAbsolutePath(), Base64.encodeToString(os.toByteArray(), Base64.DEFAULT)))
                } else {
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                WebForgeUtils.closeIO(`is`)
                WebForgeUtils.closeIO(os)
                mCountDownLatch.countDown()
            }
        }
    }

    internal class CovertFileThread internal constructor(private val callback: JsChannelCallback, private val paths: Array<String?>?) : Runnable {
        override fun run() {
            val name = Thread.currentThread().getName()
            Thread.currentThread().setName("webforge-thread")
            try {
                val mQueue: Queue<FileParcel>? = convertFile(paths)
                val result: String? = convertFileParcelObjectsToJson(mQueue)
                callback.call(result)
            } catch (e: Exception) {
                callback.call(null)
                e.printStackTrace()
            } finally {
                Thread.currentThread().setName(name)
            }
        }
    }

    internal class JsChannelCallback(private var callback: Handler.Callback?) {
        fun call(value: String?) {
            val result = callback
            callback = null
            if (result != null) {
                val message = Message.obtain(null, "JsChannelCallback".hashCode(), value)
                try {
                    result.handleMessage(message)
                } finally {
                    message.recycle()
                }
            }
        }

        companion object {
            fun create(callback: Handler.Callback?): JsChannelCallback {
                return JsChannelCallback(callback)
            }
        }
    }

    class Builder {
        internal var mActivity: Activity? = null
        internal var mUriValueCallback: ValueCallback<Uri?>? = null
        internal var mUriValueCallbacks: ValueCallback<Array<Uri>>? = null
        internal var mFileChooserParams: WebChromeClient.FileChooserParams? = null
        internal var mJsChannel = false
        internal var mWebView: WebView? = null
        internal var mPermissionInterceptor: PermissionInterceptor? = null
        internal var mAcceptType: String? = "*/*"
        internal var mJsChannelCallback: Handler.Callback? = null

        fun setAcceptType(acceptType: String?): Builder {
            this.mAcceptType = acceptType
            return this
        }

        fun setPermissionInterceptor(permissionInterceptor: PermissionInterceptor?): Builder {
            mPermissionInterceptor = permissionInterceptor
            return this
        }

        fun setActivity(activity: Activity?): Builder {
            mActivity = activity
            return this
        }

        fun setUriValueCallback(uriValueCallback: ValueCallback<Uri?>?): Builder {
            mUriValueCallback = uriValueCallback
            mJsChannel = false
            mUriValueCallbacks = null
            return this
        }

        fun setUriValueCallbacks(uriValueCallbacks: ValueCallback<Array<Uri>>?): Builder {
            mUriValueCallbacks = uriValueCallbacks
            mUriValueCallback = null
            mJsChannel = false
            return this
        }


        fun setFileChooserParams(fileChooserParams: WebChromeClient.FileChooserParams?): Builder {
            mFileChooserParams = fileChooserParams
            return this
        }

        fun setJsChannelCallback(jsChannelCallback: Handler.Callback?): Builder {
            this.mJsChannelCallback = jsChannelCallback
            mJsChannel = true
            mUriValueCallback = null
            mUriValueCallbacks = null
            return this
        }


        fun setWebView(webView: WebView?): Builder {
            mWebView = webView
            return this
        }


        fun build(): FileChooser {
            return FileChooser(this)
        }
    }


    companion object {
        /**
         * Activity Request Code
         */
        const val REQUEST_CODE: Int = 0x254

        /**
         * TAG
         */
        private val TAG: String = FileChooser::class.java.getSimpleName()

        /**
         * 修复某些特定手机拍照后，立刻获取照片为空的情况
         */
        @JvmField
        var MAX_WAIT_PHOTO_MS: Int = 8 * 1000


        private fun fileCompressAndValuesCallback(datas: Array<Uri>?, valueCallback: ValueCallback<Array<Uri>>?) {
            instance.fileCompress("system", datas, object : ValueCallback<Array<Uri>> {
                override fun onReceiveValue(value: Array<Uri>?) {
                    if (valueCallback != null) {
                        valueCallback.onReceiveValue(value)
                    }
                }
            })
        }

        private fun fileCompressAndValuesCallback(datas: Uri?, valueCallback: ValueCallback<Uri?>?) {
            if (datas == null) {
                valueCallback?.onReceiveValue(null)
                return
            }
            instance.fileCompress("system", arrayOf(datas), object : ValueCallback<Array<Uri>> {
                override fun onReceiveValue(value: Array<Uri>?) {
                    if (valueCallback != null) {
                        if (value != null && value.size > 0) {
                            valueCallback.onReceiveValue(value[0])
                        } else {
                            valueCallback.onReceiveValue(Uri.EMPTY)
                        }
                    }
                }
            })
        }

        // 必须执行在子线程, 会阻塞直到文件转换完成;
        @JvmStatic
        @Throws(Exception::class)
        fun convertFile(paths: Array<String?>?): Queue<FileParcel>? {
            if (paths == null || paths.size == 0) {
                return null
            }
            val tmp = Runtime.getRuntime().availableProcessors() + 1
            val result = if (paths.size > tmp) tmp else paths.size
            val mExecutor: Executor = Executors.newFixedThreadPool(result)
            val mQueue: Queue<FileParcel> = LinkedBlockingQueue()
            val mCountDownLatch = CountDownLatch(paths.size)

            var i = 1
            for (path in paths) {
                if (TextUtils.isEmpty(path)) {
                    mCountDownLatch.countDown()
                    continue
                }

                mExecutor.execute(EncodeFileRunnable(path!!, mQueue, mCountDownLatch, i++))
            }
            mCountDownLatch.await()

            if (!(mExecutor as ThreadPoolExecutor).isShutdown()) {
                mExecutor.shutdownNow()
            }
            return mQueue
        }


        fun convertFileParcelObjectsToJson(collection: MutableCollection<FileParcel>?): String? {
            if (collection == null || collection.size == 0) {
                return null
            }
            val mFileParcels = collection.iterator()
            val mJSONArray = JSONArray()
            try {
                while (mFileParcels.hasNext()) {
                    val jo = JSONObject()
                    val mFileParcel = mFileParcels.next()
                    jo.put("contentPath", mFileParcel.contentPath)
                    jo.put("fileBase64", mFileParcel.fileBase64)
                    jo.put("mId", mFileParcel.id)
                    mJSONArray.put(jo)
                }
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            }
            return mJSONArray.toString() + ""
        }

        @JvmStatic
        fun newBuilder(activity: Activity?, webView: WebView?): Builder {
            return Builder().setActivity(activity).setWebView(webView)
        }
    }
}
