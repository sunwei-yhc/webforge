package io.github.sunwei.webforge.bridge;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.core.util.Consumer;

import java.lang.ref.WeakReference;

import io.github.sunwei.webforge.WebPage;
import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.permission.WebForgePermissions;
import io.github.sunwei.webforge.util.MapUtil;
import io.github.sunwei.webforge.util.StatusBarUtil;
import io.github.sunwei.webforge.util.WebForgeUtils;

/** 供可信 H5 调用的 Android 原生能力。 */
public class AndroidBridge {
    private static final String TAG = "AndroidBridge";
    private final WeakReference<WebPage> pageReference;
    private final WeakReference<Activity> activityReference;

    public AndroidBridge(WebPage page, Activity activity) {
        this.pageReference = new WeakReference<>(page);
        this.activityReference = new WeakReference<>(activity);
    }

    /** 关闭承载 WebView 的 Activity。 */
    @JavascriptInterface
    public void closePage() {
        runOnUiThread(Activity::finish);
    }

    /** 先退出视频全屏或返回上一页，已无历史时关闭 Activity。 */
    @JavascriptInterface
    public void goBackOrClose() {
        runOnUiThread(activity -> {
            WebPage page = pageReference.get();
            if (page != null && !page.isClosed() && !page.goBack()) {
                activity.finish();
            }
        });
    }

    /** 用系统处理程序打开 HTTP 或 HTTPS 地址。 */
    @JavascriptInterface
    public void openExternalBrowser(String url) {
        Uri uri = parseWebUri(url);
        if (uri == null) {
            showToast("请输入有效的 HTTP 或 HTTPS 地址");
            return;
        }
        startExternalActivity(new Intent(Intent.ACTION_VIEW, uri), "无法打开浏览器");
    }

    /** 按经度、纬度、地点名的顺序传入起点和终点，打开高德驾车导航。 */
    @JavascriptInterface
    public void openAmapRoute(String startLongitude, String startLatitude, String startName,
                              String endLongitude, String endLatitude, String endName) {
        if (TextUtils.isEmpty(startLongitude) || TextUtils.isEmpty(startLatitude)
                || TextUtils.isEmpty(endLongitude) || TextUtils.isEmpty(endLatitude)) {
            return;
        }
        runOnUiThread(activity -> MapUtil.route(activity, startLongitude, startLatitude,
                startName, endLongitude, endLatitude, endName));
    }

    /**
     * 设置状态栏颜色，支持 RRGGBB 或 AARRGGBB，可带 # 前缀。
     * whiteIcons 为 1 时使用白色图标，其余值使用深色图标。
     */
    @JavascriptInterface
    public void setStatusBarColor(String color, int whiteIcons) {
        if (TextUtils.isEmpty(color) || !color.matches("(?i)#?[0-9a-f]{6}([0-9a-f]{2})?")) {
            return;
        }
        int parsedColor = Color.parseColor(color.startsWith("#") ? color : "#" + color);
        runOnUiThread(activity -> {
            if (whiteIcons == 1) {
                StatusBarUtil.setLightMode(activity);
            } else {
                StatusBarUtil.setDarkMode(activity);
            }
            StatusBarUtil.setColor(activity, parsedColor);
        });
    }

    /** 1 清除透明状态栏，其他值启用透明状态栏。 */
    @JavascriptInterface
    public void setTransparentForWindow(int isShowWindow) {
        runOnUiThread(activity -> {
            if (isShowWindow == 1) {
                StatusBarUtil.clearFlag(activity);
            } else {
                StatusBarUtil.setTransparentForWindow(activity);
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
        final Activity activity = activityReference.get();
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

    /** 申请权限，结果通过指定的 JavaScript 函数返回布尔值。 */
    @JavascriptInterface
    public void requestPermissionsWithCallback(String type, String callbackName) {
        String[] permissions = WebForgePermissions.of(type);
        if (!isActivityActive(activityReference.get()) || permissions.length == 0) {
            invokePermissionCallback(callbackName, false);
            return;
        }
        runOnUiThread(activity -> {
            boolean requesting = WebForgeUtils.requestPermissions(activity, permissions,
                    callbackName == null ? null : (resultPermissions, grantResults, extras) ->
                            invokePermissionCallback(callbackName,
                                    WebForgeUtils.hasPermission(activity, permissions)));
            if (!requesting && callbackName != null) {
                invokePermissionCallback(callbackName,
                        WebForgeUtils.hasPermission(activity, permissions));
            }
        });
    }

    @JavascriptInterface
    public void requestPermission(String type, String callbackName) {
        requestPermissionsWithCallback(type, callbackName);
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

    /** 刷新当前网页。 */
    @JavascriptInterface
    public void reloadPage() {
        WebPage page = pageReference.get();
        if (page != null && isActivityActive(activityReference.get())) {
            page.reload();
        }
    }

    /** 显示短时原生提示。 */
    @JavascriptInterface
    public void showToast(String message) {
        if (!TextUtils.isEmpty(message)) {
            runOnUiThread(activity -> Toast.makeText(activity.getApplicationContext(),
                    message, Toast.LENGTH_SHORT).show());
        }
    }

    /** 将纯文本写入系统剪贴板。 */
    @JavascriptInterface
    public void copyToClipboard(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        runOnUiThread(activity -> {
            ClipboardManager clipboard =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("webview", text));
            }
        });
    }

    /** 打开系统分享面板，分享纯文本。 */
    @JavascriptInterface
    public void shareText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        startExternalActivity(Intent.createChooser(intent, null), "无法打开系统分享");
    }

    /** 打开拨号器并预填号码，由用户确认拨打。 */
    @JavascriptInterface
    public void openDialer(String number) {
        if (number == null || !number.trim().matches("[+]?[0-9][0-9 ()-]*")) {
            showToast("请输入有效的电话号码");
            return;
        }
        Uri uri = Uri.fromParts("tel", number.trim(), null);
        startExternalActivity(new Intent(Intent.ACTION_DIAL, uri), "无法打开拨号器");
    }

    /** 读取宿主应用的 versionName，读取失败时返回空字符串。 */
    @JavascriptInterface
    public String getAppVersionName() {
        Activity activity = activityReference.get();
        if (activity == null) {
            return "";
        }
        try {
            String versionName = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
            return versionName == null ? "" : versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    @Deprecated
    @JavascriptInterface
    public void exitWebview() {
        closePage();
    }

    @Deprecated
    @JavascriptInterface
    public void finishPagebyHistory() {
        goBackOrClose();
    }

    @Deprecated
    @JavascriptInterface
    public void openBrowser(String url) {
        openExternalBrowser(url);
    }

    @Deprecated
    @JavascriptInterface
    public void openGaode(String slon, String slat, String sName,
                          String dlon, String dlat, String dName) {
        openAmapRoute(slon, slat, sName, dlon, dlat, dName);
    }

    @Deprecated
    @JavascriptInterface
    public void reload() {
        reloadPage();
    }

    @Deprecated
    @JavascriptInterface
    public void toast(String message) {
        showToast(message);
    }

    @Deprecated
    @JavascriptInterface
    public void copyText(String text) {
        copyToClipboard(text);
    }

    /**
     * Returns none|wifi|4g|3g|2g|unknown.
     */
    @JavascriptInterface
    public String getNetworkType() {
        final Activity activity = activityReference.get();
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

    private void startExternalActivity(Intent intent, String failureMessage) {
        runOnUiThread(activity -> {
            try {
                activity.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, failureMessage, e);
                Toast.makeText(activity.getApplicationContext(),
                        failureMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void runOnUiThread(Consumer<Activity> action) {
        Activity activity = activityReference.get();
        if (isActivityActive(activity)) {
            activity.runOnUiThread(() -> {
                if (isActivityActive(activity)) {
                    action.accept(activity);
                }
            });
        }
    }

    private static boolean isActivityActive(Activity activity) {
        return activity != null && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                || !activity.isDestroyed());
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
