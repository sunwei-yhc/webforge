# WebForge

面向移动端 H5 的 Android WebView 封装，内置文件上传、拍照/录像、
录音录像授权、定位、下载、外链、全屏视频、Cookie、JS 桥接和生命周期管理。

## 名称与接入

- 库名称、目录及 Gradle 模块：`WebForge` / `:WebForge`。
- namespace 与基础包名：`io.github.sunwei.webforge`。
- Maven 坐标：`io.github.sunwei:webforge:1.0.0`（发布配置，尚未上传）。
- Release 产物：`WebForge/build/outputs/aar/WebForge-release.aar`。
- 版本统一在 `build.gradle` 的 `project.version` 中维护，运行时返回 `WebForge/1.0.0`。

工程内使用：

```groovy
implementation project(':WebForge')
```

## 目录分层

所有 Java 文件的目录与 package 保持一致；测试按被测职责放入相同的包层级。

```text
io.github.sunwei.webforge/
├── WebForge.java       # 统一入口、页面组装与 Builder
├── host/               # 基础 Activity、Fragment 与带工具栏的页面
├── core/               # WebView 创建、加载、生命周期和任务调度
├── config/             # WebSettings、Cookie、缓存与全局配置
├── bridge/             # 内置 H5 桥接、JS 执行与接口注册
├── client/             # WebViewClient、WebChromeClient 与中间件
├── permission/         # 权限定义、拦截与系统授权页面
├── filechooser/        # 文件选择、文件处理与 FileProvider
├── download/           # 下载实现与延迟初始化
├── security/           # 旧版系统 WebView 内置 JS 接口清理
├── ui/                 # 对话框、错误页、视频与页面容器
│   └── indicator/      # 进度条及绘制组件
├── widget/             # WebView 控件与系统版本适配
└── util/               # 日志、文件、设备、地图和状态栏工具
```

入口改用 `WebForge`，基础页面改用 `BaseWebForgeActivity` / `BaseWebForgeFragment`，
带工具栏的页面改用 `WebForgeFragment`；H5 版本接口为
`window.Android.getWebForgeVersion()`，内置文件桥接对象为 `window.webForge`。

`WebForge` 负责页面组装。URL 加载、JS 执行和生命周期共用每个页面自己的
`WebViewTaskDispatcher`。宿主通过 `getJsInterfaceHolder()` 注册 Java 对象，
由桥接模块统一检查接口并完成注入。

直接引用具体实现类的宿主需要更新类名。

| 原实现类 | 当前实现类 |
| --- | --- |
| `DefaultWebLifeCycleImpl` / `UrlLoaderImpl` | `WebViewLifecycle` / `WebViewUrlLoader` |
| `EventHandlerImpl` / `WebForgeSettingsImpl` | `WebBackHandler` / `DefaultWebForgeSettings` |
| `JsAccessEntraceImpl` / `JsInterfaceHolderImpl` | `WebViewJsExecutor` / `WebViewJsInterfaces` |
| `WebForgeJsInterfaceCompat` | `FileUploadJsBridge` |

Builder 提供 `setEventHandler(...)`、`setInterceptUnknownUrl(...)` 和
`setLandscapeFullscreenVideo(...)`，旧方法名可作为过渡。全屏视频默认横屏，
可用 `setLandscapeFullscreenVideo(false)` 维持宿主方向。

## 默认能力

- 响应式 H5：默认开启 `useWideViewPort`、`loadWithOverviewMode`，保留 H5 的 viewport。
- 本地存储：开启 DOM Storage、Database 和常用 WebView 磁盘缓存。
- 媒体：允许 H5 音视频播放，支持全屏视频；`getUserMedia` 会申请相机/录音权限。
- 文件：支持 `<input type="file">` 单选、多选、拍照和录像。
- 导航：支持 `tel:`、`sms:`、`mailto:`、`geo:`、`intent://`、微信支付和支付宝 scheme。
- 窗口：`target="_blank"` / `window.open()` 默认转入当前 WebForge 页面。
- Cookie：开启第三方 Cookie，适用于常见的跨域登录和支付流程。
- 安全：关闭 `file://` 跨域读取；SSL 证书错误默认拒绝。

## 使用方式

继承 `BaseWebForgeActivity` 或 `BaseWebForgeFragment` 时，模块会自动注入
`window.Android`，并统一处理 WebView 的 pause/resume/destroy。

可以通过配置覆盖默认值：

```java
import io.github.sunwei.webforge.config.WebForgeSettingsConfig;

@Nullable
@Override
protected WebForgeSettingsConfig getWebForgeSettingsConfig() {
    return WebForgeSettingsConfig.builder()
            .setResponsiveLayout(true)
            .setZoomEnabled(false)
            .setMediaPlaybackRequiresUserGesture(false)
            .setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE)
            .setCacheMode(WebSettings.LOAD_DEFAULT)
            .setThirdPartyCookiesEnabled(true)
            .setSafeBrowsingEnabled(true)
            .setUserAgentSuffix("MyApp/1.0")
            .build();
}
```

直接使用 `WebForge` 时可以调用：

```java
import io.github.sunwei.webforge.WebForge;
import io.github.sunwei.webforge.config.WebForgeSettingsConfig;

WebForge.with(this)
        .setWebForgeParent(parent, new ViewGroup.LayoutParams(-1, -1))
        .useDefaultIndicator()
        .setWebForgeSettingsConfig(WebForgeSettingsConfig.defaults())
        .createWebForge()
        .ready()
        .go(url);
```

直接使用 `WebForge` 时，如果需要 `window.Android`，请在 `ready()` 之后、首次
`go(url)` 之前注入：

```java
WebForge.PreWebForge pre = builder.createWebForge().ready();
WebForge webForge = pre.get();
webForge.getJsInterfaceHolder().addJavaObject(
        "Android", new AndroidInterface(webForge, this));
pre.go(url);
```

如果应用自己注入了名为 `Android` 的对象，请让 `getAndroidInterface()` 返回 `null`，
避免和模块默认桥接对象重复。

## H5 接口

```javascript
// 导航
window.Android.exitWebview();
window.Android.finishPagebyHistory();
window.Android.reload();
window.Android.openBrowser("https://example.com");
window.Android.openGaode(slon, slat, sName, dlon, dlat, dName);

// 页面能力
window.Android.toast("保存成功");
window.Android.copyText("需要复制的文本");
const network = window.Android.getNetworkType(); // none|wifi|4g|3g|2g|unknown
const sdkVersion = window.Android.getWebForgeVersion();

// 状态栏
window.Android.setStatusBarColor("FFFFFF", 1);
window.Android.setTransparentForWindow(1);

// 权限，类型支持 Camera | Audio | Call | Location | Media
const granted = window.Android.hasPermission("audio,call");
window.Android.requestPermission("camera,audio");
window.bridge = {
  onPermissionResult(granted) {
    console.log("权限结果:", granted);
  }
};
window.Android.requestPermission("audio", "bridge.onPermissionResult");
```

`tel:` 会先申请通话权限。默认授权后直接拨出；如需授权后只打开拨号盘：

```java
DefaultWebClient.setCallNeedConfirm(true);
```

## 文件上传、拍照与录音

普通文件上传不需要额外声明存储权限，模块使用系统文件选择器（SAF）：

```html
<input type="file" accept="image/*">
<input type="file" multiple>
```

带 `capture` 的输入会直接打开相机或录像：

```html
<input type="file" accept="image/*" capture="environment">
<input type="file" accept="video/*" capture="environment">
```

H5 录音、音视频通话使用标准 API，不需要私有桥接：

```javascript
const stream = await navigator.mediaDevices.getUserMedia({
  audio: true,
  video: false
});
```

生产环境的录音和摄像头页面必须使用 HTTPS（`localhost` 除外）。Android 13 及以上
的文件选择使用 SAF/照片选择器，不再申请已经失效的 `READ_EXTERNAL_STORAGE`。

## 性能建议

1. 在启动页空闲阶段调用一次 WebView 预热，避免业务页首次创建 WebView 的冷启动开销：

```java
WebForgeConfig.warmUp(getApplicationContext());
```

2. 静态资源继续使用 H5 的 HTTP 缓存、Service Worker 和压缩资源；WebView 默认使用
   `LOAD_DEFAULT`，遵循服务端缓存头。只有确实需要离线旧页面时才改为
   `LOAD_CACHE_ELSE_NETWORK`。
3. 页面不可见时调用 `getWebLifeCycle().onPause()`，恢复时调用 `onResume()`，
   销毁时调用 `WebForge.destroy()`。继承 Base 类时已自动处理。
4. 进度条回调在进度未变化时不会重复刷新 UI，减少主线程无效重绘。

## 安全配置

默认不会接受无效 SSL 证书。只有在内网联调、自签名证书等受控调试环境中才可以临时开启：

```java
// 禁止在生产包中启用
WebForgeConfig.setAcceptSslCertificateErrors(true);
```

生产环境建议保持 `MIXED_CONTENT_COMPATIBILITY_MODE` 或更严格的模式，不要使用
`MIXED_CONTENT_ALWAYS_ALLOW`。同时应把 `window.Android` 注入到可信域名页面，
不要在不受信任的第三方页面中暴露业务 JS 接口。

## 生命周期与线程约定

`WebForge` 的创建、配置和直接 WebView 操作在主线程执行。`getUrlLoader()`、
`getJsAccessEntrace()` 和 `getWebLifeCycle()` 的方法可以从后台线程调用，内部调度到主线程。
`HttpHeaders` 的配置及返回的可变 Map 由主线程管理；显式传入加载接口的 Map 和 POST 数据
会在调用时复制，入队后修改原值不会改变已提交的请求。

内置文件选择器改为直接调用，不再依赖类名/方法名反射，减少调用开销与混淆失效风险。

每个页面拥有独立任务队列。`destroy()` 与 `getWebLifeCycle().onDestroy()` 共用销毁状态，
可以重复调用；销毁时撤销尚未执行的任务，忽略后续 URL/JS 操作和晚到的 JS/授权回调。
不取消其他页面或主线程的任务。直接使用 WebView 的宿主仍需自行停止持有的回调。

`BaseWebForgeFragment` 在 `onDestroyView()` 销毁页面，返回栈恢复时创建新 WebView，
不保留旧页面历史。`onPause()` / `onResume()` 只暂停/恢复当前 WebView，不调用会影响所有
WebView 的 `pauseTimers()` / `resumeTimers()`；如需进程级暂停 JS，由应用统一管理。
行为依据：[Android WebView 文档](https://developer.android.com/reference/android/webkit/WebView#pauseTimers())。

扩展使用 `WebForgeSettingsConfig`、`IWebForgeSettings`、`PermissionInterceptor`、
`WebViewClient`、`WebChromeClient` 和中间件，无需修改内核。`quickCallJs()` 保留 JSON
对象/数组参数的原有语义，并转义普通字符串中的引号、换行和反斜杠；Java null 传为 JS null。

## 构建与验证

本项目使用 Gradle 7.6 / AGP 7.3.1，建议使用 JDK 17。不要使用 JDK 25 重新编译 Gradle 脚本。

```bash
./gradlew :WebForge:testDebugUnitTest :WebForge:assembleRelease :web:assembleDebug
```

回归测试使用 Robolectric，覆盖线程调度、页面销毁、任务隔离、请求数据快照、JS 参数转义、
接口注册、授权取消、全屏视频退出和 Fragment 视图重建。测试不代表真实设备上的上传、媒体和网页性能测量；
上线前仍需使用业务 H5 在目标设备上验证。

Maven 发布使用 Android release 组件，生成包含运行依赖的 POM 和源码包。
仅生成 POM 或 AAR 不会上传发布。
