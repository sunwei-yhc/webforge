package io.github.sunwei.webforge.bridge;

import android.app.Activity;
import android.webkit.JavascriptInterface;

import java.lang.ref.WeakReference;

import io.github.sunwei.webforge.WebForge;
import io.github.sunwei.webforge.util.WebForgeUtils;

/** 实现 H5 的 window.webForge.uploadFile()。 */
public final class FileUploadJsBridge {
    private final WeakReference<WebForge> webForgeRef;
    private final WeakReference<Activity> activityRef;

    public FileUploadJsBridge(WebForge webForge, Activity activity) {
        webForgeRef = new WeakReference<>(webForge);
        activityRef = new WeakReference<>(activity);
    }

    @JavascriptInterface
    public void uploadFile() {
        uploadFile("*/*");
    }

    @JavascriptInterface
    public void uploadFile(String acceptType) {
        Activity activity = activityRef.get();
        WebForge webForge = webForgeRef.get();
        if (activity == null || webForge == null) {
            return;
        }
        WebForgeUtils.showFileChooserCompat(activity, webForge.getWebCreator().getWebView(),
                null, null, webForge.getPermissionInterceptor(), null, acceptType, message -> {
                    WebForge current = webForgeRef.get();
                    if (current != null) {
                        String result = message.obj instanceof String ? (String) message.obj : null;
                        current.getJsAccessEntrace().quickCallJs("uploadFileResult", result);
                    }
                    return true;
                });
    }
}
