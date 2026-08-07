# ═══════════════════════════════════════════════════════════════
#  Vulkan 探针 — 真实扩展枚举 (2026-08-07)
#
#  为什么需要 native:
#    Android 平台没有任何 Java/Kotlin 层的 Vulkan 绑定, 也没有 java.lang.foreign
#    (Panama FFI 不在 Android API 面上)。要拿到真实的 VkExtensionProperties
#    列表和物理设备数, 只能走 JNI。sysprop (ro.hardware.vulkan) 只有版本号,
#    ICD JSON (/vendor/etc/vulkan/icd.d/*.json) 只有驱动路径, 都拿不到扩展列表。
# ═══════════════════════════════════════════════════════════════
cmake_minimum_required(VERSION 3.22.1)

project(cybervulkan LANGUAGES CXX)

add_library(cybervulkan SHARED vulkan_probe.cpp)

# ★ 关键: 不链接 libvulkan.so, 只链 dl + log
#   依据: developer.android.com/games/develop/vulkan/native-engine-support
#         官方原文 "don't link against the libvulkan.so shared library in your
#         build scripts", 改为运行时动态解析函数指针 (核验 2026-08-07)
#   对本项目的额外好处: 项目 minSdk 21, 而 Vulkan 最低 API 24。若链接期依赖
#   libvulkan.so, 在 API 21~23 设备上加载 .so 会直接失败; dlopen 方式下
#   API < 24 只是返回 null, 可以干净降级。
target_link_libraries(cybervulkan
        dl
        log)

# 显式声明语言标准，不依赖不同 NDK 版本的 clang 默认值
target_compile_features(cybervulkan PRIVATE cxx_std_17)

target_compile_options(cybervulkan PRIVATE
        -Wall
        -Os                      # 探针体积优先, 无热路径
        -fvisibility=hidden)     # 仅导出 JNIEXPORT 标注的符号

# ★ 16 KB page size 合规
#   targetSdk 36 (>=35) + 携带 native 代码 ⇒ 自 2025-11-01 起为 Play 强制要求。
#   NDK r28+ 已默认 16KB 对齐, 此处显式声明作为双保险, 不依赖工具链默认值。
target_link_options(cybervulkan PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384")
