# WebForge

WebForge 是 AndroidX 项目的 WebView 库。它处理页面生命周期、返回键、加载进度、文件选择、媒体授权和下载，并支持给可信 H5 接入 JavaScript 桥接。

```java
import io.github.sunwei.webforge.WebPage;

// 在 Activity 的 onCreate() 或 Fragment 的 onViewCreated() 中调用
WebPage.open(this, container, "https://example.com");
```

[使用文档](WebForge/README.md)包含 Gradle 接入、完整的 Java 和 Kotlin 示例、H5 桥接以及旧版迁移步骤。

## 运行示例

使用 JDK 17，在仓库根目录执行以下命令。

```bash
./gradlew :WebForge:testDebugUnitTest :WebForge:assembleRelease :web:assembleDebug
```

- Android Studio 中运行 `web` 模块即可打开内置的 H5 测试页。
- 库的产物为 `WebForge/build/outputs/aar/WebForge-release.aar`。
- 示例 APK 在 `web/build/outputs/apk/debug/web-debug.apk`。

## 发布

推送 `v*` 标签后，[发布工作流](.github/workflows/publish-aar.yml)会构建库，将 AAR 和混淆映射附加到 GitHub Release，并发布到 GitHub Packages。
