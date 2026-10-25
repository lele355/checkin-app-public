# kotlinx.serialization 的 @Serializable 已由插件生成，无需额外规则。
# 保留文件是为了 release 构建配置里 proguardFiles 引用不报错。
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
