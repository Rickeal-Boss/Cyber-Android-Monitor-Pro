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

# ===== Kotlin 反射支持 (Koin DI 核心依赖) =====
-keep class kotlin.** { *; }
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlin.reflect.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

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

# ===== 保留所有 ViewModel 类本身 (Koin 反射实例化) =====
-keep class com.example.deviceinfoviewer.ui.**ViewModel { *; }

# ===== 保留 Repository 和 DataSource (单例/反射) =====
-keep class com.example.deviceinfoviewer.data.repository.** { *; }
-keep class com.example.deviceinfoviewer.data.source.** { *; }

# ===== 保留 Service 类 =====
-keep class com.example.deviceinfoviewer.service.** { *; }

# ===== 保留 UI 组件 (Compose 可能通过反射访问) =====
-keep class com.example.deviceinfoviewer.ui.components.** { *; }
-keep class com.example.deviceinfoviewer.ui.theme.** { *; }

# ===== 保留 util 工具类 =====
-keep class com.example.deviceinfoviewer.util.** { *; }

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

-dontshrink
