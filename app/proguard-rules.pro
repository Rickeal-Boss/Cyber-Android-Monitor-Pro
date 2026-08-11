# ============================================================
#  ProGuard / R8 规则 — Device Info Viewer
#  策略: 缩而不混 (shrink only, no obfuscation)
#  - 项目内反射仅作用于 framework 类，故仅精确 keep 运行时目标
#    (Gson 模型 + 入口组件)，其余项目代码交由 R8 缩减
#  - 第三方库信任 AAR 自带规则，仅补最小必要 keep
#  - -dontobfuscate: 禁用类名混淆，保护反射调用
# ============================================================

# ===== Kotlin 协程 =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ===== Kotlin 元数据 (kotlin.reflect 全量 keep 已移除) =====
# 本项目未使用 kotlin-reflect (Koin 走 Java 反射, 隐藏 API 反射作用于 framework 类)。
# 仅显式保留 kotlin.Metadata —— R8 据此保留 Kotlin 类的类型/签名信息, 供序列化与反射判断。
-keep class kotlin.Metadata { *; }

# ===== Compose 运行时 (收窄: 仅保留 runtime 包) =====
# 旧规则 -keep class androidx.compose.** 过宽, 挡住 ui/ui-graphics/ui-text/material3 的缩减。
# runtime 包是 recomposition 与编译器基础设施, 必须保留; 其余 compose 类由可达性 + AAR 规则保留。
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ===== Koin DI (收窄: 移除 org.koin.** 全量 keep, 仅钉死注册入口 + Module 子类 + 注解成员) =====
# 旧规则 -keep class org.koin.** 过宽, 挡住 Koin 库自身缩减。
# 注册入口 appModule (AppModule.kt 顶层属性, 编译为 AppModuleKt) 显式保留;
# Module 子类与 @Koin 注解注入点由下两条规则保留; Koin 库其余部分由可达性缩减。
-keep class com.rb.cybermonitorpro.di.AppModuleKt { *; }
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

# ===== 运行时反射目标（精确 keep，替代整体 keep 以恢复项目代码缩减）=====
# 严格审查结论 (2026-07-21): 项目内反射仅作用于 framework 类
# (SystemProperties/BatteryManager/Sensor/CameraCharacteristics/SignalStrength/
# GnssStatus 等，运行时恒在)，不作用于项目自身类；故无需整体 keep。
# 真正需 keep 的运行时目标只有两类:
# 1) Gson 序列化模型 — ExportHelper 对 CpuInfo/GpuInfo/BatteryInfo/MemoryInfo/
#    StorageInfo/SystemInfo 等做 toJson，运行时反射字段，须保留类与成员
-keep class com.rb.cybermonitorpro.data.model.** { *; }
# 2) 入口组件 — R8 读 manifest 已自动保留，显式声明以防边界情况
-keep class com.rb.cybermonitorpro.**.*Activity { *; }
-keep class com.rb.cybermonitorpro.**.*Service { *; }
-keep class com.rb.cybermonitorpro.**.*Receiver { *; }
-keep class com.rb.cybermonitorpro.**.*Provider { *; }
# 其余项目代码 (UI/DataSource/Repository/ViewModel) 交由 R8 正常缩减。
# ⚠️ 构建后须真机冒烟验证: 导出(JSON)/Koin 初始化/隐藏 API 反射采集。

# ===== JNI — Vulkan 探针 (2026-08-07) =====
# native 符号名 = Java_<包名>_<类名>_<方法名>, 硬编码在 libcybervulkan.so 里。
# 本项目虽 -dontobfuscate, 但 R8 的横向类合并 / 方法搬迁仍会改变归属类,
# 导致运行时 UnsatisfiedLinkError。必须显式钉死类名与 native 方法名。
-keep class com.rb.cybermonitorpro.data.source.VulkanProbe {
    native <methods>;
}

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
