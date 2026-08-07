package com.rb.cybermonitorpro.data.source

import android.util.Log

/**
 * Vulkan 真实扩展枚举探针 — Kotlin 侧安全封装 (2026-08-07)
 *
 * 背景: Android 平台没有 Java/Kotlin 层的 Vulkan 绑定, 也没有 java.lang.foreign
 * (Panama FFI 不在 Android API 面上)。要拿到真实的 VkExtensionProperties 列表和
 * 物理设备数, 唯一途径是 JNI + native。本对象把 native 侧的所有失败面
 * (so 缺失 / API<24 / 驱动异常) 统一收敛为 `probe() == null`, 调用方无需 try。
 *
 * 官方依据 (核验 2026-08-07):
 *   · developer.android.com/ndk/guides/stable_apis
 *     Vulkan 库自 API 24 起存在于所有设备, 无 GPU 支持时
 *     vkEnumeratePhysicalDevices 返回 0 个设备
 *   · developer.android.com/games/develop/vulkan/native-engine-support
 *     不得在构建脚本中链接 libvulkan.so, 改为运行时解析函数指针
 *
 * ABI: 仅打包 arm64-v8a。其它 ABI 设备上 System.loadLibrary 抛
 * UnsatisfiedLinkError, 被捕获后静默降级到 OEM 提示判据链。
 */
object VulkanProbe {

    private const val TAG = "VulkanProbe"

    /** native 探针一次成功枚举的结果快照 */
    data class Result(
        /** 物理设备数 (Android 手机恒为 1; 0 表示有 libvulkan 但无 GPU 支持) */
        val deviceCount: Int,
        /** VkPhysicalDeviceProperties.apiVersion, 形如 "1.3.128"; 可能为空 */
        val apiVersion: String,
        /** VkPhysicalDeviceProperties.deviceName, 形如 "Adreno (TM) 750"; 可能为空 */
        val gpuName: String,
        /** instance 级 + device 级扩展并集, 已去重并按字典序排列 */
        val extensions: List<String>
    )

    /**
     * 光追判据扩展名。
     * 只认这三个 —— VK_KHR_acceleration_structure 是前两者的依赖项,
     * 单独存在不足以证明可跑光追, 故不列入。
     */
    private val RAY_TRACING_EXTENSIONS = setOf(
        "VK_KHR_ray_tracing_pipeline",
        "VK_KHR_ray_query",
        "VK_NV_ray_tracing"
    )

    /** so 是否加载成功。lazy 保证只尝试一次, 失败后不重复抛异常。 */
    private val libLoaded: Boolean by lazy {
        try {
            System.loadLibrary("cybervulkan")
            true
        } catch (t: Throwable) {
            // 非 arm64-v8a 设备 / so 被裁剪 — 属预期降级路径, 非错误
            Log.i(TAG, "libcybervulkan 不可用, 回退 OEM 提示判据: ${t.javaClass.simpleName}")
            false
        }
    }

    @Volatile private var cached: Result? = null
    @Volatile private var probeDone = false
    private val lock = Any()

    /**
     * 执行 (或读取缓存的) Vulkan 枚举。
     *
     * native 侧已有进程级 once_flag, 此处再加 Kotlin 层缓存是为了省掉
     * 重复的 JNI 数组封送 —— DeviceDetailDataSource.collect() 每次下拉刷新都会调用。
     *
     * @return 枚举结果; native 不可用或枚举失败时返回 null (调用方走 OEM 兜底)
     */
    fun probe(): Result? {
        if (probeDone) return cached
        synchronized(lock) {
            if (probeDone) return cached
            cached = runProbe()
            probeDone = true
            return cached
        }
    }

    private fun runProbe(): Result? {
        if (!libLoaded) return null
        val raw = try {
            nativeProbe()
        } catch (t: Throwable) {
            // native 侧理论上不抛异常, 但驱动 crash 兜底
            Log.w(TAG, "nativeProbe 异常", t)
            null
        } ?: return null

        // 协议: [0]="count=N" [1]="api=x.y.z" [2]="gpu=名称" [3..]=扩展名
        if (raw.size < 3) return null
        var count = 0
        var api = ""
        var gpu = ""
        val exts = ArrayList<String>(raw.size)
        for (line in raw) {
            when {
                line == null -> {}
                line.startsWith("count=") -> count = line.substring(6).toIntOrNull() ?: 0
                line.startsWith("api=") -> api = line.substring(4)
                line.startsWith("gpu=") -> gpu = line.substring(4)
                line.startsWith("VK_") -> exts.add(line)
            }
        }
        // 三个头部字段全空 + 零扩展 = 实质无信息, 视为失败让 OEM 判据接管
        if (count == 0 && exts.isEmpty()) return null
        return Result(count, api, gpu, exts)
    }

    /** 真扩展列表是否证明支持硬件光追 */
    fun isRayTracingCapable(result: Result?): Boolean {
        val exts = result?.extensions ?: return false
        return exts.any { it in RAY_TRACING_EXTENSIONS }
    }

    /**
     * JNI 入口。Kotlin object 的 external fun 编译为实例方法,
     * 故 native 侧签名第二参为 jobject 而非 jclass。
     */
    private external fun nativeProbe(): Array<String?>?
}
