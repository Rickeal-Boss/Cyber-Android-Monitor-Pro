# ============================================================
#  ProGuard / R8 规则 — Device Info Viewer
#  策略: 缩而不混 (shrink only, no obfuscation)
#  - 项目包整体 keep (反射密集型: Hidden API + Koin + Compose lambda 元数据)
#  - 第三方库信任 AAR 自带规则，仅补最小必要 keep
#  - -dontobfuscate: 禁用类名混淆，保护反射调用
# ============================================================

# ===== Kotlin 协程 =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ===== Kotlin 反射 (Koin DI + Hidden API 反射核心依赖) =====
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlin.reflect.** { *; }

# ===== Compose 运行时 =====
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ===== Koin DI =====
-keep class org.koin.** { *; }
-keep class * extends org.koin.core.module.Module { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <fields>;
    @org.koin.core.annotation.* <methods>;
}

# ===== Gson (信任 AAR 自带规则 + 最小补丁) =====
-dontwarn com.google.gson.**
-keepclassmembers class * implements com.google.gson.TypeAdapterFactory {
    <init>(...);
}
-keepclassmembers class * implements com.google.gson.JsonSerializer {
    <init>(...);
}
-keepclassmembers class * implements com.google.gson.JsonDeserializer {
    <init>(...);
}

# ===== 保留整个项目包 =====
# 项目大量使用反射 (SystemProperties/BatteryManager hidden field/GnssStatus/
# VMRuntime/MobileNetwork getter 等) + Compose Composable lambda 元数据,
# 整体 keep 避免 shrink 误删。R8 仍可缩减第三方库死代码。
-keep class com.example.deviceinfoviewer.** { *; }
-keepclassmembers class com.example.deviceinfoviewer.** { *; }

# ===== 不混淆枚举 =====
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# ===== 保留 R 类内部类 =====
-keepclassmembers class **.R$* { public static <fields>; }

# ===== WebView =====
-keepclassmembers class * extends android.webkit.WebView {
   public <init>(android.content.Context);
   public <init>(android.content.Context, android.util.AttributeSet);
   public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ===== 缩而不混 + 保留运行时元数据 =====
-dontobfuscate
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
