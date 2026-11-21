
package io.github.sunwei.webforge.filechooser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.permission.Action;
import io.github.sunwei.webforge.permission.PermissionInterceptor;
import io.github.sunwei.webforge.permission.WebForgeActionFragment;
import io.github.sunwei.webforge.permission.WebForgePermissions;
import io.github.sunwei.webforge.ui.AbsWebForgeUIController;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static io.github.sunwei.webforge.permission.WebForgeActionFragment.KEY_FROM_INTENTION;
import static io.github.sunwei.webforge.permission.WebForgeActionFragment.KEY_URI;
import static io.github.sunwei.webforge.permission.WebForgeActionFragment.start;

/**
 */
public class FileChooser {
    /**
     * Activity
     */
    private final Activity mActivity;
    /**
     * ValueCallback
     */
    private ValueCallback<Uri> mUriValueCallback;
    /**
     * ValueCallback<Uri[]> After LOLLIPOP
     */
    private ValueCallback<Uri[]> mUriValueCallbacks;
    /**
     * Activity Request Code
     */
    public static final int REQUEST_CODE = 0x254;
    /**
     * WebChromeClient.FileChooserParams 封装了 Intent ，mAcceptType  等参数
     */
    private final WebChromeClient.FileChooserParams mFileChooserParams;
    /**
     * 如果是通过 JavaScript 打开文件选择器 ，那么 mJsChannelCallback 不能为空
     */
    private JsChannelCallback mJsChannelCallback;
    /**
     * 是否为Js Channel
     */
    private boolean mJsChannel = false;
    /**
     * TAG
     */
    private static final String TAG = FileChooser.class.getSimpleName();
    /**
     * 当前 WebView
     */
    private final WebView mWebView;
    /**
     * 是否为 Camera State
     */
    private boolean mCameraState = false;
    /**
     * 是否调用摄像头后  调用的是摄像模式  默认是拍照
     */
    private boolean mVideoState = false;
    /**
     * 权限拦截
     */
    private final PermissionInterceptor mPermissionInterceptor;
    /**
     * FROM_INTENTION_CODE 用于表示当前Action
     */
    private final int FROM_INTENTION_CODE = 21;
    /**
     * 当前 AbsWebForgeUIController
     */
    private WeakReference<AbsWebForgeUIController> mWebForgeUIController = null;
    /**
     * 选择文件类型
     */
    private String mAcceptType = "*/*";
    private boolean mOpened = false;
    /**
     * 修复某些特定手机拍照后，立刻获取照片为空的情况
     */
    public static int MAX_WAIT_PHOTO_MS = 8 * 1000;


    public FileChooser(Builder builder) {

        this.mActivity = builder.mActivity;
        this.mUriValueCallback = builder.mUriValueCallback;
        this.mUriValueCallbacks = builder.mUriValueCallbacks;
        this.mJsChannel = builder.mJsChannel;
        this.mFileChooserParams = builder.mFileChooserParams;
        if (this.mJsChannel) {
            this.mJsChannelCallback = JsChannelCallback.create(builder.mJsChannelCallback);
        }
        this.mWebView = builder.mWebView;
        this.mPermissionInterceptor = builder.mPermissionInterceptor;
        this.mAcceptType = builder.mAcceptType;
        this.mWebForgeUIController = new WeakReference<AbsWebForgeUIController>(WebForgeUtils.getWebForgeUIControllerByWebView(this.mWebView));

    }


    public void openFileChooser() {
        if (!WebForgeUtils.isUIThread()) {
            WebForgeUtils.runInUiThread(new Runnable() {
                @Override
                public void run() {
                    openFileChooser();
                }
            });
            return;
        }
        if (mOpened) {
            return;
        }
        mOpened = true;
        if (mActivity == null || mActivity.isFinishing() || mWebView == null
                || (!mJsChannel && mUriValueCallback == null && mUriValueCallbacks == null)) {
            cancel();
            return;
        }
        openFileChooserInternal();
    }

    private void fileChooser() {
        // Storage Access Framework grants access to the selected document
        // itself. Asking for READ_EXTERNAL_STORAGE here breaks uploads on
        // Android 13+, where that permission no longer applies to media.
        chooserAction();
    }

    private void chooserAction() {
        Action mAction = new Action();
        mAction.setAction(Action.ACTION_FILE);
        mAction.setChooserListener(getChooserListener());
        try {
            mAction.setIntent(getFileChooserIntent());
            WebForgeActionFragment.start(mActivity, mAction);
        } catch (Throwable throwable) {
            if (WebForgeConfig.DEBUG) {
                throwable.printStackTrace();
            }
            cancel();
        }

    }

    private Intent getFileChooserIntent() {
        Intent mIntent = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mFileChooserParams != null && (mIntent = mFileChooserParams.createIntent()) != null) {
            // 多选
            if (mFileChooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                mIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
}
            //			mIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            if (mFileChooserParams.getAcceptTypes() != null && mFileChooserParams.getAcceptTypes().length > 1) {
                mIntent.putExtra(Intent.EXTRA_MIME_TYPES, mFileChooserParams.getAcceptTypes());
            }
            if (Objects.equals(mIntent.getAction(), Intent.ACTION_GET_CONTENT)) {
                mIntent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            }
            return mIntent;
        }

        Intent i = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            i.setAction(Intent.ACTION_OPEN_DOCUMENT);
        } else {
            i.setAction(Intent.ACTION_GET_CONTENT);
        }
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if (TextUtils.isEmpty(this.mAcceptType)) {
            i.setType("*/*");
        } else {
            i.setType(this.mAcceptType);
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return mIntent = Intent.createChooser(i, "");
    }

    private WebForgeActionFragment.ChooserListener getChooserListener() {
        return new WebForgeActionFragment.ChooserListener() {
            @Override
            public void onChoiceResult(int requestCode, int resultCode, Intent data) {

                onIntentResult(requestCode, resultCode, data);
            }
        };
    }


    private void openFileChooserInternal() {
        boolean needCamera = false;
        boolean needVideo = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && this.mFileChooserParams != null && this.mFileChooserParams.getAcceptTypes() != null) {
            String[] types = this.mFileChooserParams.getAcceptTypes();
            for (String typeTmp : types) {
                if (TextUtils.isEmpty(typeTmp)) {
                    continue;
                }
                if (typeTmp.contains("*/") || typeTmp.contains("image/")) {
                    needCamera = true;
                }
                if (typeTmp.contains("video/")) {
                    mVideoState = true;
                    needVideo = true;
                }
            }
            if (this.mFileChooserParams.isCaptureEnabled()
                    && (needCamera || needVideo)) {
                mCameraState = true;
                mVideoState = needVideo && !needCamera;
                onCameraAction();
                return;
            }
        }

        if (this.mWebForgeUIController.get() != null) {
            this.mWebForgeUIController
                    .get()
                    .onSelectItemsPrompt(this.mWebView, mWebView.getUrl(),
                            new String[]{mActivity.getString(io.github.sunwei.webforge.R.string.webforge_camera),
                                    mActivity.getString(io.github.sunwei.webforge.R.string.webforge_file_chooser)}, getCallBack());
        } else {
            // A file chooser must always complete its callback, even when a
            // business provides no custom UI controller.
            chooserAction();
        }

    }


    private Handler.Callback getCallBack() {
        return new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case 0:
                        mCameraState = true;
                        onCameraAction();
                        break;
                    case 1:
                        mCameraState = false;
                        fileChooser();
                        break;
                    default:
                        cancel();
                        break;
                }
                return true;
            }
        };
    }


    private void onCameraAction() {
        if (mActivity == null) {
            return;
        }

        if (mPermissionInterceptor != null) {
            if (mPermissionInterceptor.intercept(FileChooser.this.mWebView.getUrl(), WebForgePermissions.CAMERA, "camera")) {
                cancel();
                return;
            }
        }
        Action mAction = new Action();
        List<String> deniedPermissions = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !(deniedPermissions = checkNeedPermission()).isEmpty()) {
            mAction.setAction(Action.ACTION_PERMISSION);
            mAction.setPermissions(deniedPermissions.toArray(new String[]{}));
            mAction.setFromIntention(FROM_INTENTION_CODE >> 3);
            mAction.setPermissionListener(this.mPermissionListener);
            start(mActivity, mAction);
        } else {
            openCameraAction();
        }

    }

    private List<String> checkNeedPermission() {

        List<String> deniedPermissions = new ArrayList<>();

        if (!WebForgeUtils.hasPermission(mActivity, WebForgePermissions.CAMERA)) {
            deniedPermissions.add(WebForgePermissions.CAMERA[0]);
        }
        return deniedPermissions;
    }

    private void openCameraAction() {
        Action mAction = new Action();
        if (mVideoState) {  //调用摄像
            mAction.setAction(Action.ACTION_VIDEO);
        } else {
            mAction.setAction(Action.ACTION_CAMERA);
        }
        mAction.setChooserListener(this.getChooserListener());
        WebForgeActionFragment.start(mActivity, mAction);
    }

    private WebForgeActionFragment.PermissionListener mPermissionListener = new WebForgeActionFragment.PermissionListener() {

        @Override
        public void onRequestPermissionsResult(@NonNull String[] permissions, @NonNull int[] grantResults, Bundle extras) {

            boolean tag = true;
            tag = WebForgeUtils.hasPermission(mActivity, Arrays.asList(permissions)) ? true : false;
            permissionResult(tag, extras.getInt(KEY_FROM_INTENTION));

        }
    };

    private void permissionResult(boolean grant, int fromIntention) {
        if (fromIntention == FROM_INTENTION_CODE >> 2) {
            if (grant) {
                chooserAction();
            } else {
                cancel();

                if (null != mWebForgeUIController.get()) {
                    mWebForgeUIController
                            .get()
                            .onPermissionsDeny(
                                    WebForgePermissions.MEDIA,
                                    WebForgePermissions.ACTION_MEDIA,
                                    "Open file chooser");
                }
            }
        } else if (fromIntention == FROM_INTENTION_CODE >> 3) {
            if (grant) {
                openCameraAction();
            } else {
                cancel();
                if (null != mWebForgeUIController.get()) {
                    mWebForgeUIController
                            .get()
                            .onPermissionsDeny(
                                    WebForgePermissions.CAMERA,
                                    WebForgePermissions.ACTION_CAMERA,
                                    "Take photo");
                }
            }
        }


    }

    public void onIntentResult(int requestCode, int resultCode, Intent data) {

        if (REQUEST_CODE != requestCode) {
            return;
        }

        //用户已经取消
        if (resultCode == Activity.RESULT_CANCELED || data == null) {
            cancel();
            return;
        }

        if (resultCode != Activity.RESULT_OK) {
            cancel();
            return;
        }

        //通过Js获取文件
        if (mJsChannel) {
            final JsChannelCallback callback = mJsChannelCallback;
            mJsChannelCallback = null;
            convertFileAndCallback(
                    mCameraState ? new Uri[]{data.getParcelableExtra(KEY_URI)} : processData(data),
                    callback);
            return;
        }

        //5.0以上系统通过input标签获取文件
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final ValueCallback<Uri[]> callback = mUriValueCallbacks;
            mUriValueCallbacks = null;
            aboveLollipopCheckFilesAndCallback(
                    mCameraState ? new Uri[]{data.getParcelableExtra(KEY_URI)} : processData(data),
                    mCameraState,
                    callback);
            return;
        }


        //4.4以下系统通过input标签获取文件
        if (mUriValueCallback == null) {
            cancel();
            return;
        }

        if (mCameraState) {
//            mUriValueCallback.onReceiveValue((Uri) data.getParcelableExtra(KEY_URI));
            fileCompressAndValuesCallback((Uri) data.getParcelableExtra(KEY_URI), mUriValueCallback);
            mUriValueCallback = null;
        } else {
            belowLollipopUriCallback(data);
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

    private void cancel() {
        JsChannelCallback jsChannelCallback = mJsChannelCallback;
        mJsChannelCallback = null;
        if (jsChannelCallback != null) {
            jsChannelCallback.call(null);
        }
        if (mUriValueCallback != null) {
            try {
                mUriValueCallback.onReceiveValue(null);
                mUriValueCallback = null;
            } catch (Throwable ignored) {
                if (WebForgeConfig.DEBUG) {
                    ignored.printStackTrace();
                }
            }
        }
        if (mUriValueCallbacks != null) {
            try {
                mUriValueCallbacks.onReceiveValue(null);
                mUriValueCallbacks = null;
            } catch (Throwable ignored) {
                if (WebForgeConfig.DEBUG) {
                    ignored.printStackTrace();
                }
            }

        }
        return;
    }


    private void belowLollipopUriCallback(Intent data) {


        if (data == null) {
            if (mUriValueCallback != null) {
                mUriValueCallback.onReceiveValue(Uri.EMPTY);
                mUriValueCallback = null;
            }
            return;
        }
        Uri mUri = data.getData();
        if (mUriValueCallback != null) {
//            mUriValueCallback.onReceiveValue(mUri);
            fileCompressAndValuesCallback(mUri, mUriValueCallback);
            mUriValueCallback = null;
        }

    }

    private Uri[] processData(Intent data) {

        Uri[] datas = null;
        if (data == null) {
            return datas;
        }
        String target = data.getDataString();
        if (!TextUtils.isEmpty(target)) {
            return datas = new Uri[]{Uri.parse(target)};
        }
        ClipData mClipData = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            mClipData = data.getClipData();
        }
        if (mClipData != null && mClipData.getItemCount() > 0) {
            datas = new Uri[mClipData.getItemCount()];
            for (int i = 0; i < mClipData.getItemCount(); i++) {

                ClipData.Item mItem = mClipData.getItemAt(i);
                datas[i] = mItem.getUri();

            }
        }
        return datas;


    }

    private void convertFileAndCallback(final Uri[] uris,
                                        final JsChannelCallback jsChannelCallback) {

        String[] paths = null;
        if (uris == null || uris.length == 0 || (paths = WebForgeUtils.uriToPath(mActivity, uris)) == null || paths.length == 0) {
            if (jsChannelCallback != null) {
                jsChannelCallback.call(null);
            }
            return;
        }
        FileCompressor.getInstance().fileCompress("customize", uris, new ValueCallback<Uri[]>() {
            @Override
            public void onReceiveValue(Uri[] value) {
                String[] compressFilePath = WebForgeUtils.uriToPath(mActivity, value);
                if (compressFilePath == null || compressFilePath.length == 0) {
                    if (jsChannelCallback != null) {
                        jsChannelCallback.call(null);
                    }
                    return;
                }
                int sum = 0;
                for (String path : compressFilePath) {
                    if (TextUtils.isEmpty(path)) {
                        continue;
                    }
                    File mFile = new File(path);
                    if (!mFile.exists()) {
                        continue;
                    }
                    sum += mFile.length();
                }

                if (sum > WebForgeConfig.MAX_FILE_LENGTH) {
                    if (mWebForgeUIController.get() != null) {
                        mWebForgeUIController.get().onShowMessage(mActivity.getString(io.github.sunwei.webforge.R.string.webforge_max_file_length_limit, (WebForgeConfig.MAX_FILE_LENGTH / 1024 / 1024) + ""), "convertFileAndCallback");
                    }
                    if (jsChannelCallback != null) {
                        jsChannelCallback.call(null);
                    }
                    return;
                }

                if (jsChannelCallback != null) {
                    AsyncTask.THREAD_POOL_EXECUTOR.execute(
                            new CovertFileThread(jsChannelCallback, compressFilePath));
                }
            }
        });


    }

    private static void fileCompressAndValuesCallback(final Uri[] datas, final ValueCallback<Uri[]> valueCallback) {
        FileCompressor.getInstance().fileCompress("system", datas, new ValueCallback<Uri[]>() {

            @Override
            public void onReceiveValue(Uri[] value) {
                if (valueCallback != null) {
                    valueCallback.onReceiveValue(value);
                }
            }
        });
    }

    private static void fileCompressAndValuesCallback(final Uri datas, final ValueCallback<Uri> valueCallback) {
        FileCompressor.getInstance().fileCompress("system", new Uri[]{datas}, new ValueCallback<Uri[]>() {

            @Override
            public void onReceiveValue(Uri[] value) {
                if (valueCallback != null) {
                    if (value != null && value.length > 0) {
                        valueCallback.onReceiveValue(value[0]);
                    } else {
                        valueCallback.onReceiveValue(Uri.EMPTY);
                    }
                }
            }
        });
    }

    /**
     * 经过多次的测试，在小米 MIUI ， 华为 ，多部分为 Android 6.0 左右系统相机获取到的文件
     * length为0 ，导致前端 ，获取到的文件， 作预览的时候不正常 ，等待5S左右文件又正常了 ， 所以这里做了阻塞等待处理，
     *
     * @param datas
     * @param isCamera
     */
    private void aboveLollipopCheckFilesAndCallback(final Uri[] datas, boolean isCamera,
                                                    final ValueCallback<Uri[]> valueCallback) {
        if (valueCallback == null) {
            cancel();
            return;
        }
        if (null != datas && datas.length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ContentResolver contentResolver = mActivity.getContentResolver();
            final int takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            for (int i = 0; i < datas.length; i++) {
                try {
                    contentResolver.takePersistableUriPermission(datas[i], takeFlags);
                } catch (Throwable throwable) {
                    if (WebForgeConfig.DEBUG) {
                        throwable.printStackTrace();
                    }
                }
            }
        }
        if (!isCamera) {
            fileCompressAndValuesCallback(
                    datas == null ? new Uri[]{} : datas, valueCallback);
            return;
        }

        if (mWebForgeUIController.get() == null) {
            valueCallback.onReceiveValue(null);
            return;
        }
        String[] paths = WebForgeUtils.uriToPath(mActivity, datas);
        if (paths == null || paths.length == 0) {
            valueCallback.onReceiveValue(null);
            return;
        }
        final String path = paths[0];
        mWebForgeUIController.get().onLoading(mActivity.getString(io.github.sunwei.webforge.R.string.webforge_loading));
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new WaitPhotoRunnable(
                path, new AboveLCallback(valueCallback, datas, mWebForgeUIController)));

    }

    private static final class AboveLCallback implements Handler.Callback {
        private ValueCallback<Uri[]> mValueCallback;
        private Uri[] mUris;
        private WeakReference<AbsWebForgeUIController> controller;

        private AboveLCallback(ValueCallback<Uri[]> valueCallbacks, Uri[] uris, WeakReference<AbsWebForgeUIController> controller) {
            this.mValueCallback = valueCallbacks;
            this.mUris = uris;
            this.controller = controller;
        }

        @Override
        public boolean handleMessage(final Message msg) {

            WebForgeUtils.runInUiThread(new Runnable() {
                @Override
                public void run() {
                    FileChooser.AboveLCallback.this.safeHandleMessage(msg);
                }
            });
            return false;
        }

        private void safeHandleMessage(Message msg) {
            if (mValueCallback != null) {
                fileCompressAndValuesCallback(mUris, mValueCallback);
//                mValueCallback.onReceiveValue(mUris);
            }
            if (controller != null && controller.get() != null) {
                controller.get().onCancelLoading();
            }
        }
    }

    private static final class WaitPhotoRunnable implements Runnable {
        private String path;
        private Handler.Callback mCallback;

        private WaitPhotoRunnable(String path, Handler.Callback callback) {
            this.path = path;
            this.mCallback = callback;
        }

        @Override
        public void run() {


            if (TextUtils.isEmpty(path) || !new File(path).exists()) {
                if (mCallback != null) {
                    mCallback.handleMessage(Message.obtain(null, -1));
                }
                return;
            }
            int ms = 0;

            while (ms <= MAX_WAIT_PHOTO_MS) {

                ms += 300;
                SystemClock.sleep(300);
                File mFile = new File(path);
                if (mFile.length() > 0) {

                    if (mCallback != null) {
                        mCallback.handleMessage(Message.obtain(null, 1));
                        mCallback = null;
                    }
                    break;
                }

            }

            if (ms > MAX_WAIT_PHOTO_MS) {
                if (mCallback != null) {
                    mCallback.handleMessage(Message.obtain(null, -1));
                }
            }
            mCallback = null;
            path = null;

        }
    }

    // 必须执行在子线程, 会阻塞直到文件转换完成;
    public static Queue<FileParcel> convertFile(String[] paths) throws Exception {

        if (paths == null || paths.length == 0) {
            return null;
        }
        int tmp = Runtime.getRuntime().availableProcessors() + 1;
        int result = paths.length > tmp ? tmp : paths.length;
        Executor mExecutor = Executors.newFixedThreadPool(result);
        final Queue<FileParcel> mQueue = new LinkedBlockingQueue<>();
        CountDownLatch mCountDownLatch = new CountDownLatch(paths.length);

        int i = 1;
        for (String path : paths) {

            if (TextUtils.isEmpty(path)) {
                mCountDownLatch.countDown();
                continue;
            }

            mExecutor.execute(new EncodeFileRunnable(path, mQueue, mCountDownLatch, i++));

        }
        mCountDownLatch.await();

        if (!((ThreadPoolExecutor) mExecutor).isShutdown()) {
            ((ThreadPoolExecutor) mExecutor).shutdownNow();
        }
        return mQueue;
    }


    static class EncodeFileRunnable implements Runnable {

        private String filePath;
        private Queue<FileParcel> mQueue;
        private CountDownLatch mCountDownLatch;
        private int id;

        public EncodeFileRunnable(String filePath, Queue<FileParcel> queue, CountDownLatch countDownLatch, int id) {
            this.filePath = filePath;
            this.mQueue = queue;
            this.mCountDownLatch = countDownLatch;
            this.id = id;
        }


        @Override
        public void run() {
            InputStream is = null;
            ByteArrayOutputStream os = null;
            try {
                File mFile = new File(filePath);
                Log.e(TAG, "encode file:" + mFile.length());
                if (mFile.exists()) {

                    is = new FileInputStream(mFile);
                    if (is == null) {
                        return;
                    }
                    os = new ByteArrayOutputStream();
                    byte[] b = new byte[1024];
                    int len;
                    while ((len = is.read(b, 0, 1024)) != -1) {
                        os.write(b, 0, len);
                    }
                    mQueue.offer(new FileParcel(id, mFile.getAbsolutePath(), Base64.encodeToString(os.toByteArray(), Base64.DEFAULT)));
                } else {
                }

            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                WebForgeUtils.closeIO(is);
                WebForgeUtils.closeIO(os);
                mCountDownLatch.countDown();
            }


        }
    }

    static String convertFileParcelObjectsToJson(Collection<FileParcel> collection) {

        if (collection == null || collection.size() == 0) {
            return null;
        }
        Iterator<FileParcel> mFileParcels = collection.iterator();
        JSONArray mJSONArray = new JSONArray();
        try {
            while (mFileParcels.hasNext()) {
                JSONObject jo = new JSONObject();
                FileParcel mFileParcel = mFileParcels.next();
                jo.put("contentPath", mFileParcel.getContentPath());
                jo.put("fileBase64", mFileParcel.getFileBase64());
                jo.put("mId", mFileParcel.getId());
                mJSONArray.put(jo);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return mJSONArray + "";
    }

    static class CovertFileThread implements Runnable {

        private WeakReference<JsChannelCallback> mJsChannelCallback;
        private String[] paths;

        private CovertFileThread(JsChannelCallback JsChannelCallback, String[] paths) {
            this.mJsChannelCallback = new WeakReference<JsChannelCallback>(JsChannelCallback);
            this.paths = paths;
        }

        @Override
        public void run() {

            String name = Thread.currentThread().getName();
            Thread.currentThread().setName("webforge-thread");
            try {
                Queue<FileParcel> mQueue = convertFile(paths);
                String result = convertFileParcelObjectsToJson(mQueue);
                if (mJsChannelCallback != null && mJsChannelCallback.get() != null) {
                    mJsChannelCallback.get().call(result);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Thread.currentThread().setName(name);
            }
        }
    }

    static class JsChannelCallback {
        WeakReference<Handler.Callback> callback = null;

        JsChannelCallback(Handler.Callback callback) {
            this.callback = new WeakReference<Handler.Callback>(callback);
        }

        public static JsChannelCallback create(Handler.Callback callback) {
            return new JsChannelCallback(callback);
        }

        void call(String value) {
            if (this.callback != null && this.callback.get() != null) {
                this.callback.get().handleMessage(Message.obtain(null, "JsChannelCallback".hashCode(), value));
            }
        }
    }

    public static Builder newBuilder(Activity activity, WebView webView) {
        return new Builder().setActivity(activity).setWebView(webView);
    }

    public static final class Builder {

        private Activity mActivity;
        private ValueCallback<Uri> mUriValueCallback;
        private ValueCallback<Uri[]> mUriValueCallbacks;
        private WebChromeClient.FileChooserParams mFileChooserParams;
        private boolean mJsChannel = false;
        private WebView mWebView;
        private PermissionInterceptor mPermissionInterceptor;
        private String mAcceptType = "*/*";
        private Handler.Callback mJsChannelCallback;

        public Builder setAcceptType(String acceptType) {
            this.mAcceptType = acceptType;
            return this;
        }

        public Builder setPermissionInterceptor(PermissionInterceptor permissionInterceptor) {
            mPermissionInterceptor = permissionInterceptor;
            return this;
        }

        public Builder setActivity(Activity activity) {
            mActivity = activity;
            return this;
        }

        public Builder setUriValueCallback(ValueCallback<Uri> uriValueCallback) {
            mUriValueCallback = uriValueCallback;
            mJsChannel = false;
            mUriValueCallbacks = null;
            return this;
        }

        public Builder setUriValueCallbacks(ValueCallback<Uri[]> uriValueCallbacks) {
            mUriValueCallbacks = uriValueCallbacks;
            mUriValueCallback = null;
            mJsChannel = false;
            return this;
        }


        public Builder setFileChooserParams(WebChromeClient.FileChooserParams fileChooserParams) {
            mFileChooserParams = fileChooserParams;
            return this;
        }

        public Builder setJsChannelCallback(Handler.Callback jsChannelCallback) {
            this.mJsChannelCallback = jsChannelCallback;
            mJsChannel = true;
            mUriValueCallback = null;
            mUriValueCallbacks = null;
            return this;
        }


        public Builder setWebView(WebView webView) {
            mWebView = webView;
            return this;
        }


        public FileChooser build() {
            return new FileChooser(this);
        }
    }


}
