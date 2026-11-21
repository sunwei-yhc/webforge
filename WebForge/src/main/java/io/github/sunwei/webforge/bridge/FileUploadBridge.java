package io.github.sunwei.webforge.bridge;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;

import java.lang.ref.WeakReference;

import io.github.sunwei.webforge.WebPage;
import io.github.sunwei.webforge.permission.PermissionInterceptor;
import io.github.sunwei.webforge.util.WebForgeUtils;

/** 实现 H5 的 window.webForge.uploadFile()。 */
public final class FileUploadBridge {
    private final WeakReference<WebPage> pageReference;
    private final WeakReference<Activity> activityReference;
    private final PermissionInterceptor permissionInterceptor;

    public FileUploadBridge(WebPage page, Activity activity, PermissionInterceptor interceptor) {
        pageReference = new WeakReference<>(page);
        activityReference = new WeakReference<>(activity);
        permissionInterceptor = interceptor;
    }

    @JavascriptInterface
    public void uploadFile() {
        uploadFile("*/*");
    }

    @JavascriptInterface
    public void uploadFile(String acceptType) {
        Activity activity = activityReference.get();
        if (activity != null) {
            activity.runOnUiThread(() -> openPicker(activity, acceptType));
        }
    }

    private void openPicker(Activity activity, String acceptType) {
        WebPage page = pageReference.get();
        WebView view = page == null ? null : page.getView();
        if (view == null || activity.isFinishing()) {
            return;
        }
        WebForgeUtils.showFileChooserCompat(activity, view, null, null,
                permissionInterceptor, null, acceptType, message -> {
                    WebPage current = pageReference.get();
                    if (current != null && !current.isClosed()) {
                        JSONArray files = null;
                        if (message.obj instanceof String) {
                            try {
                                files = new JSONArray((String) message.obj);
                            } catch (JSONException ignored) {
                                // 无法解析时按选择失败处理。
                            }
                        }
                        current.callJavaScript("uploadFileResult", files);
                    }
                    return true;
                });
    }
}
