# 桥接方法的混淆规则统一由 consumer-rules.pro 提供给宿主。
# WebForge 的公开 API 需要保留在发布 AAR 中，供宿主直接继承或调用。
-keep public class io.github.sunwei.webforge.** {
    *;
}
