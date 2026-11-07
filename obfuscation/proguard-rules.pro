# ==================== 保护核心类 ====================
-keep class com.linyu.mcx19hook.** { *; }
-keepclassmembers class com.linyu.mcx19hook.** { *; }

# ==================== LSPosed API ====================
-keep class org.lsposed.** { *; }
-keepclassmembers class org.lsposed.** { *; }
-dontwarn org.lsposed.**

# ==================== Gson ====================
-keep class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# ==================== Kotlin 标准库 ====================
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }
-dontwarn kotlin.**

# ==================== 协程 ====================
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ==================== Timber ====================
-keep class timber.log.** { *; }
-keepclassmembers class timber.log.** { *; }
-dontwarn timber.log.**

# ==================== Guava ====================
-keep class com.google.common.** { *; }
-keepclassmembers class com.google.common.** { *; }
-dontwarn com.google.common.**

# ==================== Native 方法保护 ====================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ==================== 反射使用的类/字段/方法 ====================
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}

# ==================== 混淆优化 ====================
-obfuscationdictionary obfuscation/obfuscation_dict.txt
-classobfuscationdictionary obfuscation/obfuscation_dict.txt
-packageobfuscationdictionary obfuscation/obfuscation_dict.txt

-useuniqueclassmembernames
-overloadaggressively
-repackageclasses ''
-flattenpackagehierarchy ''
-allowaccessmodification
-mergeinterfacesaggressively

# ==================== 移除日志 ====================
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
}
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# ==================== 保持注解 ====================
-keepattributes *Annotation*,EnclosingMethod,Signature,InnerClasses,SourceFile,LineNumberTable

# ==================== 字符串加密 (Glue 插件配合) ====================
# 已在 build.gradle.kts 中启用 glue 插件