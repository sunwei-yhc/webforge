# WebForge 使用文档

用 `WebPage.open(host, container, url)` 打开网页。需要调整默认行为时，再用 `WebPage.builder(host, container)`。

- [添加依赖](#添加依赖)
- [打开网页](#打开网页)
- [管理页面](#管理页面)
- [按需配置](#按需配置)
- [文件选择与权限](#文件选择与权限)
- [JavaScript 桥接](#javascript-桥接)
- [常见问题](#常见问题)
- [从旧版迁移](#从旧版迁移)

## 添加依赖

使用 AndroidX。库声明的最低版本为 Android 4.1（API 16），部分能力受系统 WebView 版本限制。构建当前仓库使用 JDK 17，库模块使用 Android SDK 33。

**在本仓库内**，在应用模块的 `build.gradle` 中添加依赖。示例应用 `web` 已完成配置。

```groovy
dependencies {
    implementation project(':WebForge')
}
```

Kotlin DSL 写法为 `implementation(project(":WebForge"))`。

**在其他项目中**，先在本仓库根目录把当前源码构建到本地 Maven 仓库。GitHub Packages 的 `0.1.4` 使用旧 Java API。下面的命令将当前源码构建到独立目录，便于区分同坐标的产物。

```bash
./gradlew -Dmaven.repo.local="$PWD/build/webforge-repo" :WebForge:publishReleasePublicationToMavenLocal
```

把以下仓库合并到宿主项目的 `settings.gradle`，换成自己的绝对路径。如果项目在其他位置集中声明依赖仓库，就加到对应位置。JitPack 用于解析下载组件的依赖。

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri('/absolute/path/to/perfactwebview/build/webforge-repo')
            content { includeModule('io.github.sunwei', 'webforge') }
        }
        maven { url = uri('https://jitpack.io') }
    }
}
```

在宿主应用的 `build.gradle` 添加依赖，并把这个本地仓库放在任何会解析相同坐标的远程仓库之前。

```groovy
dependencies {
    implementation 'io.github.sunwei:webforge:0.1.6'
}
```

<details>
<summary>Kotlin DSL 写法</summary>

在 `settings.gradle.kts` 的依赖仓库配置中添加本地仓库和 JitPack。

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("/absolute/path/to/perfactwebview/build/webforge-repo")
            content { includeModule("io.github.sunwei", "webforge") }
        }
        maven { url = uri("https://jitpack.io") }
    }
}
```

在应用的 `build.gradle.kts` 中引用构建产物。

```kotlin
dependencies {
    implementation("io.github.sunwei:webforge:0.1.6")
}
```

</details>

库的 Manifest 会合并网络权限、文件选择用的 FileProvider 和授权组件。涉及相机、麦克风和定位时，仍按系统流程授权。

## 打开网页

将下面的容器保存为应用的 `res/layout/screen_web.xml`。

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/web_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### Activity（Java）

在应用自己的包下新建 `WebActivity.java`，将示例放在对应的 `package` 声明之后。这个示例继承 `AppCompatActivity`，需要使用 AppCompat 或其派生主题。

```java
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import io.github.sunwei.webforge.WebPage;

public class WebActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_web);
        WebPage.open(this, findViewById(R.id.web_container), "https://example.com");
    }
}
```

在应用的 `AndroidManifest.xml` 的 `<application>` 中注册这个 Activity。

```xml
<activity android:name=".WebActivity" android:exported="false" />
```

### Fragment（Kotlin）

同样在应用包下新建 `WebFragment.kt`，由宿主通过 Fragment 事务显示。

```kotlin
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import io.github.sunwei.webforge.WebPage

class WebFragment : Fragment(R.layout.screen_web) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = view.findViewById<FrameLayout>(R.id.web_container)
        WebPage.open(this, container, "https://example.com")
    }
}
```

需要自带标题和菜单的页面时，可以把 `ToolbarWebFragment.newInstance(url)` 交给宿主的 Fragment 事务。

## 管理页面

WebForge 随 Activity 或 Fragment 的当前视图暂停、恢复和销毁 WebView。Fragment 视图重建后，`onViewCreated()` 会创建新页面并加载初始地址。返回键优先退出全屏视频，然后回退网页历史，最后交回宿主。宿主自行处理返回键时，使用 `handleBack(false)`，按需调用 `page.goBack()`。

只有在后续要控制网页时，才需要保留 `WebPage.open(...)` 返回的实例。同一容器换网页地址用 `page.load(url)`。要替换整个页面实例，先关闭原实例。如果 Fragment 把页面存为字段，在 `onDestroyView()` 清空引用，释放由 WebForge 处理。

| 用途 | 调用 |
| --- | --- |
| 打开另一个地址 | `page.load(url)` |
| 加载 HTML 字符串 | `page.loadHtml(html, baseUrl)`，`baseUrl` 用于解析相对路径，可传 `null` |
| 提交表单 | `page.post(url, body)`，传入 URL 编码的表单字节 |
| 刷新 / 停止加载 | `page.reload()` / `page.stop()` |
| 处理返回 | `page.goBack()`，退出全屏或回退历史后返回 `true` |
| 获取底层 WebView | `page.getView()`，关闭后返回 `null` |
| 提前释放 | `page.close()`，可重复调用 |
| 清理浏览数据 | `page.clearBrowserData()` 清理应用 WebView 的 Cookie 和缓存，并清理当前页面的历史和表单记录 |

在主线程创建页面、手动返回、注册桥接或访问底层 WebView。加载、刷新、停止、执行 JS 和关闭可从后台线程调用，页面会将操作调度到主线程。关闭时会撤销待执行的操作并丢弃晚到的 JS 结果。

## 按需配置

例如，需要关闭进度条或放大网页文字时，在打开页面前设置对应选项。

```java
WebPage page = WebPage.builder(this, container)
        .progress(false)
        .configureSettings(settings -> settings.setTextZoom(110))
        .open("https://example.com");
```

| 需求 | Builder 用法 |
| --- | --- |
| 定制进度条 | `progress(color, heightDp)` 或 `progress(indicatorView)` |
| 定制加载错误页 | `errorLayout(layoutRes, retryViewId)` 绑定重试按钮，`errorView(view)` 使用自定义 View |
| 设置缓存、Cookie、缩放或媒体播放 | `options(WebOptions.builder()…build())`。也可以用 `configureSettings(...)` 在这些选项应用后补充设置 |
| 监听导航、错误或资源加载 | `navigationCallbacks(new NavigationCallbacks() { … })` |
| 监听标题、进度或控制台 | `chromeCallbacks(new ChromeCallbacks() { … })` |
| 定制文件选择面板、对话框或权限提示 | `uiController(new PageUiController() { … })` |
| 拦截唤起第三方应用的网页链接 | `externalLinks(ExternalLinks.BLOCK)`，作用于外部 scheme |
| 全屏视频保持宿主方向 | `landscapeVideo(false)` |
| 复用已有 WebView | `webView(view)`，由 WebForge 接管客户端和销毁。View 须尚未挂载，或者位于同时交给 `content(layout)` 的未挂载布局中 |
| 显示渲染器退出后的恢复入口 | `onRendererGone(listener)`。API 26 起，原页面清理完毕后在主线程回调，通过新建 `WebPage` 恢复 |

覆盖回调时，调用 `super` 以继续执行 WebForge 的进度、错误页等默认处理；自行处理某个行为时，按对应的 Android 回调约定返回结果。回调对象在同一时间只绑定一个页面，每次创建页面时可传入新实例。

### 给导航添加请求头

```java
import java.util.Collections;

WebPage page = WebPage.builder(this, container)
        .headers("https://example.com", Collections.singletonMap("X-Client", "Android"))
        .open("https://example.com/home");
```

`headers(url, map)` 作用于 `open(url)` 和 `page.load(url)` 发起的同源导航，按协议、主机和端口匹配。网页内的请求、子资源和重定向由 WebView 处理，登录状态宜走服务端会话和 Cookie。只为一次导航指定请求头，使用 `page.load(url, map)`。

### 外部应用链接

默认处理电话、短信、邮件、地图等常用外链，应用市场链接通常会提示确认。如果业务允许网页唤起外部应用，但希望电话链接仅打开拨号盘，可在应用启动时调用 `WebNavigationClient.setCallNeedConfirm(true)`。否则，有通话权限时 `tel:` 链接会直接拨号。

## 文件选择与权限

标准文件上传用 HTML 的文件表单即可。系统选择器会将所选文件交回 H5，提交由网页处理。图片或视频字段可通过 `accept` 指定类型，拍照和录像取决于设备提供的能力。

```html
<input type="file" name="attachments" multiple>
<input type="file" name="photo" accept="image/*" capture="environment">
```

网页使用 `getUserMedia()` 的相机和麦克风授权由库处理。部署时使用 HTTPS，定位也用 HTTPS。 [Android WebChromeClient 定位回调](https://developer.android.com/reference/android/webkit/WebChromeClient#onGeolocationPermissionsShowPrompt(java.lang.String,android.webkit.GeolocationPermissions.Callback))

需要自定义部分权限策略时，用 `permissionInterceptor((url, permissions, action) -> ...)`。返回 `true` 会拦截，返回 `false` 会继续默认流程。该回调覆盖标准 WebView 媒体和定位请求、文件选择器拍摄及下载，`action` 分别为 `onPermissionRequest`、`location`、`camera` 和 `download`。下载触发时，`url` 是下载地址。

网页触发系统 WebView 的下载回调后，WebForge 默认通过下载组件处理，并附带对应地址的 WebView Cookie。服务端文件可用常规 HTTPS 下载链接。

## JavaScript 桥接

### Android 调用 H5

等网页定义好函数后再调用。字符串参数由 `callJavaScript` 自动转义，也可传入布尔值、数字、`JSONObject`、`JSONArray` 或 `null`。

```java
page.callJavaScript("bridge.onSaved", true, "保存成功");
page.evaluateJavaScript("document.title", json -> android.util.Log.d("WebPage", json));
```

`evaluateJavaScript(script, callback)` 从 API 19 起在主线程返回 JSON 格式字符串。比如标题为 `Home` 时，收到的字符串内容带引号，即 `"Home"`。

### H5 调用 Android

给可信 H5 注册业务方法。以下是 Java 示例，可将桥接类放在 Activity 内。

```java
public static final class AppBridge {
    @android.webkit.JavascriptInterface
    public String getPlatform() {
        return "Android";
    }
}
```

在 Activity 中把下面的代码放到首次加载的位置。示例页面保存于应用的 `src/main/assets/index.html`。

```java
WebPage.builder(this, container)
        .bridge("AppBridge", new AppBridge())
        .open("file:///android_asset/index.html");
```

```html
<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<p id="platform"></p>
<script>
    document.getElementById("platform").textContent = window.AppBridge.getPlatform();
</script>
```

桥接对象可被页面中的 iframe 调用，启用桥接的 WebView 只加载可信内容。桥接方法在 WebView 的后台线程执行，更新 Android 界面时切回主线程。通过 `page.addJavaScriptBridge(name, object)` 后加的对象，在下一次加载或刷新后对 H5 可见。 [Android WebView 桥接文档](https://developer.android.com/reference/android/webkit/WebView#addJavascriptInterface(java.lang.Object,java.lang.String))

### 兼容已有 H5 的内置桥接

对使用 `window.Android` 或 `window.webForge` 的可信页面，在打开前调用 `enableBuiltInBridges()`。

```java
WebPage.builder(this, container)
        .enableBuiltInBridges()
        .open("file:///android_asset/index.html");
```

- `window.Android` 保留 `getWebForgeVersion()`、`getNetworkType()`、`reload()`、`toast(message)`、`copyText(text)`、`openBrowser(url)`、`exitWebview()`、`finishPagebyHistory()` 等方法。权限查询用 `hasPermission(type)`，权限请求用 `requestPermission(type, callbackName)`。H5 的回调接收布尔值，支持的类型包括 `Camera`、`Audio`、`Call`、`Location` 和 `Media`。
- `window.webForge.uploadFile(acceptType)` 打开选择器，通过全局函数 `uploadFileResult(files)` 返回结果。文件对象包含 `mId`、`contentPath` 和 `fileBase64`。取消或选择失败时返回 `null`。需要直接上传文件时优先使用标准文件表单，避免把整个文件转成 Base64。

```javascript
window.uploadFileResult = function (files) {
    if (files) {
        console.log("已选择 " + files.length + " 个文件");
    }
};

// 绑定到 H5 按钮的点击事件
function chooseImage() {
    window.webForge.uploadFile("image/*");
}
```

[示例 Activity](../web/src/main/java/android/sanshisan/web/LoadwebActivity.java)加载了仓库里的 [H5 能力测试页](../web/src/main/assets/webview_h5_test.html)。

## 常见问题

| 现象 | 检查方法 |
| --- | --- |
| 编译时找不到 `WebPage` | 确认已依赖当前源码模块或本地 Maven 产物，并核对同坐标的远程依赖是否被优先解析 |
| Fragment 报视图生命周期异常 | 将页面创建移到当前 Fragment 的 `onViewCreated()` |
| H5 提示 `window.Android` 是 `undefined` | 确认在 `open(url)` 前调用 `enableBuiltInBridges()`，并确认执行 JS 时网页已加载 |
| 选择文件后拿不到结果 | 使用标准表单时监听 `input` 的 `change`，使用旧桥接时提前定义 `window.uploadFileResult` |
| 网页有历史，返回键却直接退出 | 检查宿主是否另注册了优先处理返回键的回调，按需让它先调用 `page.goBack()` |
| 页面空白或进入错误页 | 先检查 URL、网络和证书。本地 H5 用 `file:///android_asset/`，并核对文件名和 assets 路径 |
