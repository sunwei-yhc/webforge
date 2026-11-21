
package io.github.sunwei.webforge.client;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import io.github.sunwei.webforge.ui.AbsWebForgeUIController;
import io.github.sunwei.webforge.ui.IVideo;
import io.github.sunwei.webforge.ui.indicator.IndicatorController;
import io.github.sunwei.webforge.util.LogUtils;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 */
public class DefaultChromeClient extends MiddlewareWebChromeBase {
    /**
     * Activity
     */
    private WeakReference<Activity> mActivityWeakReference = null;
    /**
     * DefaultChromeClient 's TAG
     */
    private String TAG = DefaultChromeClient.class.getSimpleName();
    /**
     * Android WebChromeClient path ，用于反射，用户是否重写来该方法
     */
    public static final String ANDROID_WEBCHROMECLIENT_PATH = "android.webkit.WebChromeClient";
    /**
     * WebChromeClient
     */
    private WebChromeClient mWebChromeClient;
    /**
     * 包装Flag
     */
    private boolean mIsWrapper = false;
    /**
     * Video 处理类
     */
    private IVideo mIVideo;
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
     * AbsWebForgeUIController
     */
    private WeakReference<AbsWebForgeUIController> mWebForgeUIController = null;
    /**
     * IndicatorController 进度条控制器
     */
    private IndicatorController mIndicatorController;
    /**
     * 文件选择器
     */
    private final Set<PendingPermissionRequest> mPendingRequests = new HashSet<>();
    private boolean mDestroyed;
    private int mLastProgress = -1;

    public DefaultChromeClient(Activity activity,
                        IndicatorController indicatorController,
                        WebChromeClient chromeClient,
                        @Nullable IVideo iVideo,
                        PermissionInterceptor permissionInterceptor, WebView webView) {
        super(chromeClient);
        this.mIndicatorController = indicatorController;
        mIsWrapper = chromeClient != null ? true : false;
        this.mWebChromeClient = chromeClient;
        mActivityWeakReference = new WeakReference<Activity>(activity);
        this.mIVideo = iVideo;
        this.mPermissionInterceptor = permissionInterceptor;
        this.mWebView = webView;
        mWebForgeUIController = new WeakReference<AbsWebForgeUIController>(WebForgeUtils.getWebForgeUIControllerByWebView(webView));
    }


    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        if (mIndicatorController != null && newProgress != mLastProgress) {
            mLastProgress = newProgress;
            mIndicatorController.progress(view, newProgress);
        }
    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        if (mIsWrapper) {
            super.onReceivedTitle(view, title);
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
                AbsWebForgeUIController controller = mWebForgeUIController.get();
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
    public void destroy() {
        if (mDestroyed) {
            return;
        }
        mDestroyed = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (PendingPermissionRequest pending : new ArrayList<>(mPendingRequests)) {
                pending.deny();
            }
        }
        if (mCallback != null) {
            mCallback.invoke(mOrigin, false, false);
            mCallback = null;
            mOrigin = null;
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

    /**
     * Keeps target="_blank" and window.open() inside the current WebForge
     * instance. The temporary WebView is used only to receive the popup
     * request and is destroyed as soon as its URL is transferred.
     */
    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                  Message resultMsg) {
        if (view == null || resultMsg == null
                || !(resultMsg.obj instanceof WebView.WebViewTransport)) {
            return false;
        }
        final WebView target = view;
        final WebView popup = new WebView(view.getContext());
        final AtomicBoolean transferred = new AtomicBoolean(false);
        popup.getSettings().setJavaScriptEnabled(true);
        popup.setWebViewClient(new WebViewClient() {
            private void transferToCurrentWebView(String url) {
                if (TextUtils.isEmpty(url) || "about:blank".equals(url)
                        || !transferred.compareAndSet(false, true)) {
                    return;
                }
                target.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!TextUtils.isEmpty(url)) {
                            target.loadUrl(url);
                        }
                        popup.destroy();
                    }
                });
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public boolean shouldOverrideUrlLoading(WebView child, android.webkit.WebResourceRequest request) {
                transferToCurrentWebView(request.getUrl().toString());
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView child, String url) {
                transferToCurrentWebView(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView child, String url, Bitmap favicon) {
                super.onPageStarted(child, url, favicon);
                transferToCurrentWebView(url);
            }
        });
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(popup);
        resultMsg.sendToTarget();
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (transferred.compareAndSet(false, true)) {
                    popup.destroy();
                }
            }
        }, 15000);
        return true;
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
        if (valueCallbacks == null) {
            return false;
        }
        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity == null || mActivity.isFinishing()) {
            return false;
        }
        return WebForgeUtils.showFileChooserCompat(mActivity,
                mWebView,
                valueCallbacks,
                fileChooserParams,
                this.mPermissionInterceptor,
                null,
                null,
                null
        );
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
        if (mActivity == null || mActivity.isFinishing()) {
            valueCallback.onReceiveValue(null);
            return;
        }
        WebForgeUtils.showFileChooserCompat(mActivity,
                mWebView,
                null,
                null,
                this.mPermissionInterceptor,
                valueCallback,
                mimeType,
                null
        );
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        super.onConsoleMessage(consoleMessage);
        return true;
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (mIVideo != null) {
            mIVideo.onShowCustomView(view, callback);
        }
    }

    @Override
    public void onHideCustomView() {
        if (mIVideo != null) {
            mIVideo.onHideCustomView();
        }
    }
}
