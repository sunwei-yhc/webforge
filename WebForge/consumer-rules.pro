# H5 通过方法名调用桥接接口，宿主开启混淆时必须保留这些方法。
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
