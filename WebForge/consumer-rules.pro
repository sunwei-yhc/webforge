# H5 通过方法名调用桥接接口，宿主开启混淆时必须保留这些方法。
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# WebForge 的公开 API 由宿主直接继承或调用，不能在发布 AAR 中被 R8 重命名或移除。
-keep public class io.github.sunwei.webforge.** {
    *;
}
