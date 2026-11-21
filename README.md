# WebForge

面向移动端 H5 的 Android WebView 库，提供文件上传、媒体授权、下载、JS 桥接和生命周期管理。

- 库模块：`WebForge`，基础包名：`io.github.sunwei.webforge`。
- 示例应用：`web`，内置 H5 能力测试页。
- 接入方式、包分层与功能说明见 [WebForge 文档](WebForge/README.md)。

使用 JDK 17 构建：

```bash
./gradlew :WebForge:testDebugUnitTest :WebForge:assembleRelease :web:assembleDebug
```

AAR 输出为 `WebForge/build/outputs/aar/WebForge-release.aar`。
Maven 发布坐标配置为 `io.github.sunwei:webforge:0.1.4`。

从 GitHub 标签通过 JitPack 使用：

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.sunwei-yhc:webforge:v0.1.4'
}
```
