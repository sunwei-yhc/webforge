
package io.github.sunwei.webforge.client;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import io.github.sunwei.webforge.permission.Action;
import io.github.sunwei.webforge.permission.PermissionInterceptor;
import io.github.sunwei.webforge.permission.WebForgeActionFragment;
import io.github.sunwei.webforge.permission.WebForgePermissions;
import io.github.sunwei.webforge.ui.PageUiController;
import io.github.sunwei.webforge.ui.FullscreenVideo;
import io.github.sunwei.webforge.ui.indicator.BaseIndicatorView;
import io.github.sunwei.webforge.util.LogUtils;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 */
public class WebInteractionClient extends ChromeCallbacks {
    /**
     * Activity
     */
    private WeakReference<Activity> mActivityWeakReference = null;
    /**
     * WebInteractionClient 's TAG
     */
    private String TAG = WebInteractionClient.class.getSimpleName();
    /**
     * Android WebChromeClient path ，用于反射，用户是否重写来该方法
     */
    public static final String ANDROID_WEBCHROMECLIENT_PATH = "android.webkit.WebChromeClient";
    private final FullscreenVideo video;
    /**
     * PermissionInterceptor 权限拦截器
     */
    private PermissionInterceptor mPermissionInterceptor;
    /**
     * 当前 WebView
     */
    private WebView mWebView;
    /**
     * Web端触发的定位 mOrigin
     */
    private String mOrigin = null;
    /**
     * Web 端触发的定位 Callback 回调成功，或者失败
     */
    private GeolocationPermissions.Callback mCallback = null;
    /**
     * 标志位
     */
    public static final int FROM_CODE_INTENTION = 0x18;
    /**
     * 标识当前是获取定位权限
     */
    public static final int FROM_CODE_INTENTION_LOCATION = FROM_CODE_INTENTION << 2;
    /**
     * PageUiController
     */
    private WeakReference<PageUiController> mWebForgeUIController = null;
    private final BaseIndicatorView progress;
    /**
     * 文件选择器
     */
    private final Set<PendingPermissionRequest> mPendingRequests = new HashSet<>();
    private boolean mDestroyed;
    private int mLastProgress = -1;
    private PendingSelection<?> selection;
    private PopupWindow pendingPopup;

    public WebInteractionClient(Activity activity,
                        @Nullable BaseIndicatorView progress,
                        @NonNull FullscreenVideo video,
                        PermissionInterceptor permissionInterceptor, WebView webView) {
        this.progress = progress;
        mActivityWeakReference = new WeakReference<Activity>(activity);
        this.video = video;
        this.mPermissionInterceptor = permissionInterceptor;
        this.mWebView = webView;
        mWebForgeUIController = new WeakReference<PageUiController>(WebForgeUtils.getWebForgeUIControllerByWebView(webView));
    }


    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        if (progress == null || newProgress == mLastProgress) {
            return;
        }
        mLastProgress = newProgress;
        if (newProgress == 0) {
            progress.reset();
        } else if (newProgress < 100) {
            progress.show();
        }
        progress.setProgress(newProgress);
        if (newProgress == 100) {
            progress.hide();
        }
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        if (mWebForgeUIController.get() != null) {
            mWebForgeUIController.get().onJsAlert(view, url, message);
        }
        result.confirm();
        return true;
    }


    @Override
    public void onReceivedIcon(WebView view, Bitmap icon) {
        super.onReceivedIcon(view, icon);
    }

    @Override
    public void onGeolocationPermissionsHidePrompt() {
        mCallback = null;
        mOrigin = null;
        super.onGeolocationPermissionsHidePrompt();
    }

    //location
    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        onGeolocationPermissionsShowPromptInternal(origin, callback);
    }

    private void onGeolocationPermissionsShowPromptInternal(String origin, GeolocationPermissions.Callback callback) {
        if (mDestroyed) {
            callback.invoke(origin, false, false);
            return;
        }
        if (mCallback != null) {
            mCallback.invoke(mOrigin, false, false);
            mCallback = null;
            mOrigin = null;
        }
        if (mPermissionInterceptor != null) {
            if (mPermissionInterceptor.intercept(this.mWebView.getUrl(), WebForgePermissions.LOCATION, "location")) {
                callback.invoke(origin, false, false);
                return;
            }
        }
        Activity mActivity = mActivityWeakReference.get();
        if (mActivity == null) {
            callback.invoke(origin, false, false);
            return;
        }
        List<String> deniedPermissions = null;
        if ((deniedPermissions = WebForgeUtils.getDeniedPermissions(mActivity, WebForgePermissions.LOCATION)).isEmpty()) {
            LogUtils.i(TAG, "onGeolocationPermissionsShowPromptInternal:" + true);
            callback.invoke(origin, true, false);
        } else {
            Action mAction = Action.createPermissionsAction(deniedPermissions.toArray(new String[]{}));
            mAction.setFromIntention(FROM_CODE_INTENTION_LOCATION);
            mAction.setPermissionListener((permissions, grantResults, extras) -> {
                if (mDestroyed || mCallback != callback) {
                    return;
                }
                mCallback = null;
                mOrigin = null;
                Activity activity = mActivityWeakReference.get();
                boolean granted = activity != null && WebForgeUtils.hasPermission(activity, permissions);
                callback.invoke(origin, granted, false);
                PageUiController controller = mWebForgeUIController.get();
                if (!granted && controller != null) {
                    controller.onPermissionsDeny(WebForgePermissions.LOCATION,
                            WebForgePermissions.ACTION_LOCATION, "Location");
                }
            });
            this.mCallback = callback;
            this.mOrigin = origin;
            WebForgeActionFragment.start(mActivity, mAction);
        }
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        try {
            if (this.mWebForgeUIController.get() != null) {
                this.mWebForgeUIController.get().onJsPrompt(mWebView, url, message, defaultValue, result);
            }
        } catch (Exception e) {
            if (LogUtils.isDebug()) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        if (mWebForgeUIController.get() != null) {
            mWebForgeUIController.get().onJsConfirm(view, url, message, result);
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPermissionRequest(PermissionRequest request) {
        if (request == null) {
            return;
        }
        if (mDestroyed) {
            request.deny();
            return;
        }
        PendingPermissionRequest pending = new PendingPermissionRequest(request);
        mPendingRequests.add(pending);
        onPermissionRequestInternal(pending);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void onPermissionRequestInternal(PermissionRequest request) {
        final String[] resources = request.getResources();
        if (resources == null || resources.length <= 0) {
            request.deny();
            return;
        }
        Set<String> resourcesSet = new HashSet<>(Arrays.asList(resources));
        ArrayList<String> allowedResources = new ArrayList<>(2);
        ArrayList<String> permissions = new ArrayList<>(2);
        //录音、录像（H5 使用 getUserMedia / MediaRecorder 时触发）
        if (resourcesSet.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
            allowedResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
            permissions.add(Manifest.permission.CAMERA);
        }
        if (resourcesSet.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            allowedResources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (allowedResources.isEmpty()) {
            request.deny();
            return;
        }
        final String[] grantedResources = allowedResources.toArray(new String[]{});
        if (mPermissionInterceptor != null) {
            boolean intercept = mPermissionInterceptor.intercept(mWebView.getUrl(), permissions.toArray(new String[]{}), "onPermissionRequest");
            if (intercept) {
                request.deny();
                return;
            }
        }
        if (mWebForgeUIController.get() != null) {
            mWebForgeUIController.get().onPermissionRequest(request);
        } else {
            //没有 UI 控制器时兜底，主动申请录音/录像权限
            requestPermission(request, grantedResources, permissions);
        }
    }

    /**
     * 兜底处理 H5 的权限申请，没有 WebForgeUIController 时也能主动申请录音/通话等权限。
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void requestPermission(final PermissionRequest request, final String[] resources, List<String> permissions) {
        final Activity activity = mActivityWeakReference.get();
        if (activity == null) {
            request.deny();
            return;
        }
        if (permissions.isEmpty()) {
            request.grant(resources);
            return;
        }
        final String[] requestPermissions = permissions.toArray(new String[]{});
        boolean requesting = WebForgeUtils.requestPermissions(activity, requestPermissions,
                new WebForgeActionFragment.PermissionListener() {
                    @Override
                    public void onRequestPermissionsResult(@NonNull String[] permissions, @NonNull int[] grantResults, Bundle extras) {
                        if (WebForgeUtils.getDeniedPermissions(activity, permissions).isEmpty()) {
                            request.grant(resources);
                        } else {
                            request.deny();
                        }
                    }
                });
        if (requesting) {
            return;
        }
        if (WebForgeUtils.getDeniedPermissions(activity, requestPermissions).isEmpty()) {
            request.grant(resources);
        } else {
            request.deny();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPermissionRequestCanceled(PermissionRequest request) {
        for (PendingPermissionRequest pending : new ArrayList<>(mPendingRequests)) {
            if (pending.original == request) {
                pending.cancel();
            }
        }
        super.onPermissionRequestCanceled(request);
    }

    /** 页面销毁时结束待授权请求，系统权限弹窗的晚到结果不再触碰旧页面。 */
    public void release(boolean rendererAvailable) {
        if (mDestroyed) {
            return;
        }
        mDestroyed = true;
        if (pendingPopup != null) {
            pendingPopup.close();
        }
        try {
            if (selection != null) {
                if (rendererAvailable) {
                    selection.onReceiveValue(null);
                } else {
                    selection.takeResult();
                }
            }
        } finally {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !mPendingRequests.isEmpty()) {
                    for (PendingPermissionRequest pending : new ArrayList<>(mPendingRequests)) {
                        if (rendererAvailable) {
                            pending.deny();
                        } else {
                            pending.cancel();
                        }
                    }
                }
            } finally {
                GeolocationPermissions.Callback callback = mCallback;
                String origin = mOrigin;
                mCallback = null;
                mOrigin = null;
                if (rendererAvailable && callback != null) {
                    callback.invoke(origin, false, false);
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private final class PendingPermissionRequest extends PermissionRequest {
        private final PermissionRequest original;
        private boolean completed;

        PendingPermissionRequest(PermissionRequest original) {
            this.original = original;
        }

        @Override
        public Uri getOrigin() {
            return original.getOrigin();
        }

        @Override
        public String[] getResources() {
            return original.getResources();
        }

        @Override
        public void grant(String[] resources) {
            if (!completed) {
                cancel();
                original.grant(resources);
            }
        }

        @Override
        public void deny() {
            if (!completed) {
                cancel();
                original.deny();
            }
        }

        void cancel() {
            completed = true;
            mPendingRequests.remove(this);
        }
    }

    /** 把新窗口的 URL 交回当前页面，临时 WebView 随页面释放。 */
    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                  Message resultMsg) {
        if (mDestroyed || resultMsg == null
                || !(resultMsg.obj instanceof WebView.WebViewTransport)) {
            return false;
        }
        if (pendingPopup != null) {
            pendingPopup.close();
        }
        PopupWindow popup = new PopupWindow(view);
        pendingPopup = popup;
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(popup.view);
        resultMsg.sendToTarget();
        popup.handler.postDelayed(popup.timeout, 15_000);
        return true;
    }

    private final class PopupWindow {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable timeout = this::close;
        private final WebView target;
        private final WebView view;
        private boolean transferred;

        PopupWindow(WebView target) {
            this.target = target;
            view = new WebView(target.getContext());
            view.setWebViewClient(new WebViewClient() {
                @Override
                @RequiresApi(Build.VERSION_CODES.N)
                public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                    if (request.isForMainFrame()) {
                        transfer(request.getUrl().toString());
                    }
                    return true;
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    transfer(url);
                    return true;
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    transfer(url);
                }

                @Override
                @RequiresApi(Build.VERSION_CODES.O)
                public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                    close();
                    return true;
                }
            });
        }

        private void transfer(String url) {
            if (transferred || TextUtils.isEmpty(url) || "about:blank".equals(url)) {
                return;
            }
            transferred = true;
            handler.post(() -> {
                if (pendingPopup != this) {
                    return;
                }
                close();
                if (!mDestroyed) {
                    target.loadUrl(url);
                }
            });
        }

        private void close() {
            if (pendingPopup != this) {
                return;
            }
            pendingPopup = null;
            handler.removeCallbacksAndMessages(null);
            view.destroy();
        }
    }

    @Override
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long quota, long estimatedDatabaseSize, long totalQuota, WebStorage.QuotaUpdater quotaUpdater) {
        quotaUpdater.updateQuota(totalQuota * 2);
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        LogUtils.i(TAG, "openFileChooser>=5.0");
        return openFileChooserAboveL(webView, filePathCallback, fileChooserParams);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean openFileChooserAboveL(WebView webView, ValueCallback<Uri[]> valueCallbacks, FileChooserParams fileChooserParams) {
        if (mDestroyed || valueCallbacks == null) {
            return false;
        }
        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity == null || mActivity.isFinishing()) {
            return false;
        }
        PendingSelection<Uri[]> pending = startSelection(valueCallbacks);
        boolean handled = WebForgeUtils.showFileChooserCompat(mActivity,
                mWebView,
                pending,
                fileChooserParams,
                this.mPermissionInterceptor,
                null,
                null,
                null
        );
        if (!handled) {
            pending.takeResult();
        }
        return handled;
    }

    /**
     * Android  >= 4.1
     *
     * @param uploadFile ValueCallback ,  File URI callback
     * @param acceptType
     * @param capture
     */
    @Override
    public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {
        /*believe me , i never want to do this */
        LogUtils.i(TAG, "openFileChooser>=4.1");
        createAndOpenCommonFileChooser(uploadFile, acceptType);
    }

    //  Android < 3.0
    @Override
    public void openFileChooser(ValueCallback<Uri> valueCallback) {
        Log.i(TAG, "openFileChooser<3.0");
        createAndOpenCommonFileChooser(valueCallback, "*/*");
    }

    //  Android  >= 3.0
    @Override
    public void openFileChooser(ValueCallback valueCallback, String acceptType) {
        Log.i(TAG, "openFileChooser>3.0");
        createAndOpenCommonFileChooser(valueCallback, acceptType);
    }


    private void createAndOpenCommonFileChooser(ValueCallback valueCallback, String mimeType) {
        if (valueCallback == null) {
            return;
        }
        Activity mActivity = this.mActivityWeakReference.get();
        if (mDestroyed || mActivity == null || mActivity.isFinishing()) {
            valueCallback.onReceiveValue(null);
            return;
        }
        WebForgeUtils.showFileChooserCompat(mActivity,
                mWebView,
                null,
                null,
                this.mPermissionInterceptor,
                startSelection(valueCallback),
                mimeType,
                null
        );
    }

    private <T> PendingSelection<T> startSelection(ValueCallback<T> result) {
        if (selection != null) {
            selection.onReceiveValue(null);
        }
        PendingSelection<T> pending = new PendingSelection<>(result);
        selection = pending;
        return pending;
    }

    private final class PendingSelection<T> implements ValueCallback<T> {
        private ValueCallback<T> result;

        PendingSelection(ValueCallback<T> result) {
            this.result = result;
        }

        @Override
        public void onReceiveValue(T value) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                deliver(value);
            } else {
                WebForgeUtils.runInUiThread(() -> deliver(value));
            }
        }

        private void deliver(T value) {
            ValueCallback<T> callback = takeResult();
            if (callback != null) {
                callback.onReceiveValue(value);
            }
        }

        private ValueCallback<T> takeResult() {
            ValueCallback<T> callback = result;
            result = null;
            if (selection == this) {
                selection = null;
            }
            return callback;
        }
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        super.onConsoleMessage(consoleMessage);
        return true;
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (!mDestroyed) {
            video.onShowCustomView(view, callback);
        }
    }

    @Override
    public void onHideCustomView() {
        video.onHideCustomView();
    }
}
