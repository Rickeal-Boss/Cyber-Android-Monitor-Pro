# ============================================================
#  ProGuard / R8 混淆规则 — Device Info Viewer
#  minifyEnabled true + shrinkResources true 的运行时保护
# ============================================================

# ===== Kotlin 协程 =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ===== Compose 运行时 (防止动画/重组类被剥离) =====
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ===== Koin DI (依赖反射) =====
-keep class org.koin.** { *; }
-keep class * extends org.koin.core.module.Module { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <fields>;
    @org.koin.core.annotation.* <methods>;
}

# ===== R8 保留所有 ViewModel 构造器 (Koin viewModel{} 需要) =====
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ===== Gson =====
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===== 保留项目自身数据模型 (Gson 反序列化 + LiveData 反射) =====
-keep class com.example.deviceinfoviewer.data.model.** { *; }
-keep class com.example.deviceinfoviewer.AppSettings { *; }
-keep class com.example.deviceinfoviewer.FormatUtils { *; }

# ===== 保留 BuildConfig =====
-keep class com.example.deviceinfoviewer.BuildConfig { *; }

# ===== 保留 Crash 日志 =====
-keep class com.example.deviceinfoviewer.DeviceApplication { *; }

# ===== 不混淆枚举 =====
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# ===== 保留 R 类内部类 (防止 shrinkResources 误删) =====
-keepclassmembers class **.R$* { public static <fields>; }

# ===== WebView (如有使用) =====
-keepclassmembers class * extends android.webkit.WebView {
   public <init>(android.content.Context);
   public <init>(android.content.Context, android.util.AttributeSet);
   public <init>(android.content.Context, android.util.AttributeSet, int);
}
