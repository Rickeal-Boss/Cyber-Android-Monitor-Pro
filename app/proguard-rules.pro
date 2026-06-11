# Cyber Android Monitor Pro — R8 ProGuard Rules

# ── 数据模型 (LiveData/Gson 序列化) ──
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.example.deviceinfoviewer.data.model.** { *; }

# ── Koin DI ──
-keep class org.koin.** { *; }
-keep class org.koin.core.** { *; }
-dontwarn org.koin.**

# ── Kotlin Coroutines ──
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Compose ──
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Shell/反射 (SystemProperties / dumpsys) ──
-keepclassmembers class * {
    @android.os.SystemProperties <methods>;
}
-keep class com.example.deviceinfoviewer.data.source.SysFsReader { *; }
-keep class com.example.deviceinfoviewer.data.source.ShellCommandDataSource { *; }

# ── Material Icons ──
-keep class androidx.compose.material.icons.** { *; }

# ── General ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
