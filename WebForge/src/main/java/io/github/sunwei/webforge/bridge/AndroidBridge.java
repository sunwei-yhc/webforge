package io.github.sunwei.webforge.bridge;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import io.github.sunwei.webforge.WebPage;
import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.permission.WebForgePermissions;
import io.github.sunwei.webforge.util.MapUtil;
import io.github.sunwei.webforge.util.StatusBarUtil;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.lang.ref.WeakReference;

/**
 * @Author 萤火虫
 * @Date 2021/7/23
 * @Desc
 */
public class AndroidBridge {
    private static final String TAG = "AndroidBridge";
    private final WeakReference<WebPage> pageReference;
    private final WeakReference<Activity> activityWeakReference;

    public AndroidBridge(WebPage page, Activity activity) {
        this.pageReference = new WeakReference<>(page);
        this.activityWeakReference = new WeakReference<>(activity);
    }

    /**
     * 退出整个Webview页面
     */
    @JavascriptInterface
    public void exitWebview() {
        final Activity activity = activityWeakReference.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!activity.isFinishing()) {
                    activity.finish();
                }
            }
        });
    }

    /**
     * 根据webview的历史层级进行返回
     */
    @JavascriptInterface
    public void finishPagebyHistory() {
        Log.d(TAG, "finishPagebyHistory: ");
        final Activity activity = activityWeakReference.get();
        final WebPage page = pageReference.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (page != null && !page.isClosed() && !page.goBack()) {
                    if (!activity.isFinishing()) {
                        activity.finish();
                    }
                }
            }
        });
    }

    /**
     * 通过浏览器打开地址
     * 可以通过浏览器下载资源文件
     *
     * @param url 打开的地址
     */
    @JavascriptInterface
    public void openBrowser(String url) {
        Log.d(TAG, "openBrowser: " + url);
        final Activity activity = activityWeakReference.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        final Uri uri = parseWebUri(url);
        if (uri == null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity.getApplicationContext(),
                            url + " 该链接无法使用浏览器打开。", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setData(uri);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Log.d(TAG, "openBrowser: " + e.getMessage());
                    Toast.makeText(activity.getApplicationContext(),
                            "无法打开浏览器", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * 打开高德导航
     *
     * @param slon  起始点-经度
     * @param slat  起始点-维度
     * @param sName 起始点-地址名
     * @param dlon  终点-经度
     * @param dlat  终点-维度
     * @param dName 终点-地址名字
     */
    @JavascriptInterface
    public void openGaode(String slon, String slat, String sName, String dlon, String dlat, String dName) {
        final Activity activity = activityWeakReference.get();
        if (activity == null || activity.isFinishing()
                || TextUtils.isEmpty(slon) || TextUtils.isEmpty(slat)
                || TextUtils.isEmpty(dlon) || TextUtils.isEmpty(dlat)) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MapUtil.route(activity, slon, slat, sName, dlon, dlat, dName);
            }
        });
    }


//    /**
//     *
//     */
//    @JavascriptInterface
//    public void setLightMode() {
//        StatusBarUtil.setLightMode(activityWeakReference.get());
//    }
//
//    @JavascriptInterface
//    public void setDarkMode() {
//        StatusBarUtil.setDarkMode(activityWeakReference.get());
//    }

    /**
     * 设置WebView沉浸式状态栏的颜色
     *
     * @param color   例如 FFFFFF  不需要传#号
     * @param isWhite 沉浸式状态栏字体颜色是否是  1 白色 2/0 黑色
     */
    @JavascriptInterface
    public void setStatusBarColor(String color, int isWhite) {
        final Activity activity = activityWeakReference.get();
        if (activity == null || activity.isFinishing()
                || TextUtils.isEmpty(color) || !color.matches("(?i)^[0-9a-f]{6}([0-9a-f]{2})?$")) {
            return;
        }
        final int parsedColor;
        try {
            parsedColor = Color.parseColor("#" + color);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isWhite == 1) {
                    StatusBarUtil.setLightMode(activity);
                } else {
                    StatusBarUtil.setDarkMode(activity);
                }
                StatusBarUtil.setColor(activity, parsedColor);
            }
        });
    }

    /**
     * 是否显示原生沉浸式状态栏
     *
     * @param isShowWindow 1 显示沉浸式状态栏  0 不显示沉浸式状态栏
     */
    @JavascriptInterface
    public void setTransparentForWindow(int isShowWindow) {
        final Activity activity = activityWeakReference.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isShowWindow == 1) {
                    StatusBarUtil.clearFlag(activity);
                } else {
                    StatusBarUtil.setTransparentForWindow(activity);
                }
            }
        });
    }

    /**
     * 是否已经拥有指定类型的权限
     *
     * @param type 权限类型 Camera | Audio | Call | Location | Media ，支持多个类型使用 , 分隔
     */
    @JavascriptInterface
    public boolean hasPermission(String type) {
        final Activity activity = activityWeakReference.get();
        final String[] permissions = WebForgePermissions.of(type);
        if (activity == null || permissions.length == 0) {
            return false;
        }
        return WebForgeUtils.hasPermission(activity, permissions);
    }

    /**
     * 主动申请指定类型的权限，例如 H5 录音前调用 Android.requestPermission("audio")
     * 权限类型 Camera | Audio | Call | Location | Media ，支持多个类型使用 , 分隔 ，例如 "audio,call"
     *
     * @param type 权限类型
     */
    @JavascriptInterface
    public void requestPermission(String type) {
        requestPermission(type, null);
    }

    /**
     * Requests permissions and invokes window.&lt;callbackName&gt;(granted).
     *
     * @param type         Camera | Audio | Call | Location | Media
     * @param callbackName optional global JavaScript function name
     */
    @JavascriptInterface
    public void requestPermission(String type, final String callbackName) {
        final Activity activity = activityWeakReference.get();
        final String[] permissions = WebForgePermissions.of(type);
        Log.d(TAG, "requestPermission type:" + type + " permissions:" + TextUtils.join(",", permissions));
        if (activity == null || permissions.length == 0) {
            invokePermissionCallback(callbackName, false);
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing()) {
                    return;
                }
                boolean requesting = WebForgeUtils.requestPermissions(
                        activity,
                        permissions,
                        callbackName == null ? null
                                : new io.github.sunwei.webforge.permission.WebForgeActionFragment.PermissionListener() {
                                    @Override
                                    public void onRequestPermissionsResult(
                                            @androidx.annotation.NonNull String[] resultPermissions,
                                            @androidx.annotation.NonNull int[] grantResults,
                                            @androidx.annotation.NonNull Bundle extras) {
                                        invokePermissionCallback(callbackName,
                                                WebForgeUtils.hasPermission(activity, permissions));
                                    }
                                });
                if (!requesting && callbackName != null) {
                    invokePermissionCallback(callbackName,
                            WebForgeUtils.hasPermission(activity, permissions));
                }
            }
        });
    }

    private void invokePermissionCallback(final String callbackName, final boolean granted) {
        if (TextUtils.isEmpty(callbackName)
                || !callbackName.matches(
                "^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")) {
            return;
        }
        final WebPage page = pageReference.get();
        if (page == null) {
            return;
        }
        page.callJavaScript(callbackName, granted);
    }

    /**
     * Reloads the current H5 page.
     */
    @JavascriptInterface
    public void reload() {
        final WebPage page = pageReference.get();
        final Activity activity = activityWeakReference.get();
        if (page == null || activity == null || activity.isFinishing()) {
            return;
        }
        page.reload();
    }

    /**
     * Shows a short native toast.
     */
    @JavascriptInterface
    public void toast(final String message) {
        final Activity activity = activityWeakReference.get();
        if (activity == null || TextUtils.isEmpty(message)) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Copies plain text to the system clipboard.
     */
    @JavascriptInterface
    public void copyText(String text) {
        final Activity activity = activityWeakReference.get();
        if (activity == null || TextUtils.isEmpty(text)) {
            return;
        }
        final ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                clipboard.setPrimaryClip(ClipData.newPlainText("webview", text));
            }
        });
    }

    /**
     * Returns none|wifi|4g|3g|2g|unknown.
     */
    @JavascriptInterface
    public String getNetworkType() {
        final Activity activity = activityWeakReference.get();
        if (activity == null) {
            return "none";
        }
        switch (WebForgeUtils.checkNetworkType(activity)) {
            case 1:
                return "wifi";
            case 2:
                return "4g";
            case 3:
                return "3g";
            case 4:
                return "2g";
            default:
                return WebForgeUtils.checkNetwork(activity) ? "unknown" : "none";
        }
    }

    @JavascriptInterface
    public String getWebForgeVersion() {
        return WebForgeConfig.WEBFORGE_VERSION;
    }

    private static Uri parseWebUri(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null) {
            return null;
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        return TextUtils.isEmpty(uri.getHost()) ? null : uri;
    }
}
