// ═══════════════════════════════════════════════════════════════
//  Vulkan 真实扩展枚举探针 (2026-08-07)
//
//  官方依据 (核验 2026-08-07):
//    · developer.android.com/ndk/guides/stable_apis
//      "Vulkan 库存在于所有 API 24+ 设备上, 但应用需在运行时检查 GPU 硬件支持;
//       不支持 Vulkan 的设备 vkEnumeratePhysicalDevices 返回零个设备"
//    · developer.android.com/games/develop/vulkan/native-engine-support
//      "don't link against the libvulkan.so shared library in your build scripts"
//      → 全部函数指针在运行时经 vkGetInstanceProcAddr 解析
//
//  minSdk 21 安全性:
//    本 .so 不链接 libvulkan.so。API 21~23 设备上 dlopen 返回 null,
//    探针直接报告不可用, Kotlin 侧回退到原有 OEM 提示判据链。
// ═══════════════════════════════════════════════════════════════
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <vulkan/vulkan.h>

#include <cstdint>
#include <cstdio>
#include <mutex>
#include <set>
#include <string>
#include <vector>

#define LOG_TAG "VulkanProbe"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

// 防御性上限: 避免驱动返回异常大的计数导致巨量分配
constexpr uint32_t kMaxExtensions = 1024;

struct ProbeResult {
    bool available = false;
    uint32_t deviceCount = 0;
    std::string apiVersion;
    std::string gpuName;
    std::set<std::string> extensions;   // set 天然去重 + 字典序
};

// ★ 句柄故意常驻, 不做 dlclose:
//   部分 OEM Vulkan 驱动在 dlclose 时因 TLS / atexit 残留而崩溃。
//   保持常驻是通行做法, 内存开销可忽略。
void* gVulkanLib = nullptr;

bool loadVulkanLibrary() {
    if (gVulkanLib != nullptr) return true;
    gVulkanLib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (gVulkanLib == nullptr) {
        // API < 24 或设备无 Vulkan 驱动 — 属正常降级路径, 非错误
        const char* err = dlerror();
        LOGI("libvulkan.so unavailable (%s) — fallback to OEM hints",
             err != nullptr ? err : "unknown");
        return false;
    }
    return true;
}

// 自行实现版本拆解, 不依赖 VK_VERSION_* / VK_API_VERSION_* 宏
// (不同 NDK 版本中前者已标记 deprecated, -Wall 下会告警)
inline uint32_t vkMajor(uint32_t v) { return (v >> 22) & 0x7FU; }
inline uint32_t vkMinor(uint32_t v) { return (v >> 12) & 0x3FFU; }
inline uint32_t vkPatch(uint32_t v) { return v & 0xFFFU; }

std::string formatVersion(uint32_t v) {
    char buf[32];
    snprintf(buf, sizeof(buf), "%u.%u.%u", vkMajor(v), vkMinor(v), vkPatch(v));
    return std::string(buf);
}

void collectExtensions(const std::vector<VkExtensionProperties>& props, ProbeResult& out) {
    for (const auto& p : props) {
        if (p.extensionName[0] != '\0') out.extensions.insert(std::string(p.extensionName));
    }
}

void probeOnce(ProbeResult& out) {
    if (!loadVulkanLibrary()) return;

    auto pfnGetInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
            dlsym(gVulkanLib, "vkGetInstanceProcAddr"));
    if (pfnGetInstanceProcAddr == nullptr) {
        LOGW("vkGetInstanceProcAddr missing — abort probe");
        return;
    }

    auto pfnCreateInstance = reinterpret_cast<PFN_vkCreateInstance>(
            pfnGetInstanceProcAddr(VK_NULL_HANDLE, "vkCreateInstance"));
    auto pfnEnumInstExt = reinterpret_cast<PFN_vkEnumerateInstanceExtensionProperties>(
            pfnGetInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceExtensionProperties"));
    if (pfnCreateInstance == nullptr) {
        LOGW("vkCreateInstance unresolvable — abort probe");
        return;
    }

    // ── 1. Instance 级扩展 (无需创建 instance 即可查询) ──
    if (pfnEnumInstExt != nullptr) {
        uint32_t n = 0;
        if (pfnEnumInstExt(nullptr, &n, nullptr) == VK_SUCCESS && n > 0 && n <= kMaxExtensions) {
            std::vector<VkExtensionProperties> props(n);
            if (pfnEnumInstExt(nullptr, &n, props.data()) == VK_SUCCESS) {
                collectExtensions(props, out);
            }
        }
    }

    // ── 2. 创建最小 instance ──
    //   apiVersion 固定请求 1.0。官方要求不得请求高于设备支持的版本,
    //   而 1.0 恒被接受; 真实版本随后从 VkPhysicalDeviceProperties.apiVersion 读取,
    //   因此无需先探 vkEnumerateInstanceVersion。
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "CyberMonitorProbe";
    appInfo.applicationVersion = 1;
    appInfo.pEngineName = "none";
    appInfo.engineVersion = 1;
    appInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VkInstance instance = VK_NULL_HANDLE;
    const VkResult ir = pfnCreateInstance(&createInfo, nullptr, &instance);
    if (ir != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        LOGW("vkCreateInstance failed (VkResult=%d)", static_cast<int>(ir));
        // instance 级扩展可能已采到, 仍标记可用
        out.available = !out.extensions.empty();
        return;
    }

    auto pfnEnumPhysical = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(
            pfnGetInstanceProcAddr(instance, "vkEnumeratePhysicalDevices"));
    auto pfnGetProps = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(
            pfnGetInstanceProcAddr(instance, "vkGetPhysicalDeviceProperties"));
    auto pfnEnumDevExt = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
            pfnGetInstanceProcAddr(instance, "vkEnumerateDeviceExtensionProperties"));
    auto pfnDestroyInstance = reinterpret_cast<PFN_vkDestroyInstance>(
            pfnGetInstanceProcAddr(instance, "vkDestroyInstance"));

    if (pfnEnumPhysical != nullptr) {
        uint32_t devCount = 0;
        // 官方: 不支持 Vulkan 的硬件此处返回 0 个设备
        if (pfnEnumPhysical(instance, &devCount, nullptr) == VK_SUCCESS
                && devCount > 0 && devCount <= 16) {
            out.deviceCount = devCount;
            std::vector<VkPhysicalDevice> devices(devCount);
            if (pfnEnumPhysical(instance, &devCount, devices.data()) == VK_SUCCESS) {
                // 主 GPU 取首个物理设备 (Android 手机恒为 1 个)
                if (pfnGetProps != nullptr) {
                    VkPhysicalDeviceProperties props{};
                    pfnGetProps(devices[0], &props);
                    out.apiVersion = formatVersion(props.apiVersion);
                    out.gpuName = std::string(props.deviceName);
                }
                // ── 3. Device 级扩展 (光追扩展在这一层) ──
                if (pfnEnumDevExt != nullptr) {
                    for (uint32_t d = 0; d < devCount; ++d) {
                        uint32_t n = 0;
                        if (pfnEnumDevExt(devices[d], nullptr, &n, nullptr) == VK_SUCCESS
                                && n > 0 && n <= kMaxExtensions) {
                            std::vector<VkExtensionProperties> props(n);
                            if (pfnEnumDevExt(devices[d], nullptr, &n, props.data()) == VK_SUCCESS) {
                                collectExtensions(props, out);
                            }
                        }
                    }
                }
            }
        }
    }

    if (pfnDestroyInstance != nullptr) pfnDestroyInstance(instance, nullptr);

    out.available = true;
    LOGI("probe ok: devices=%u api=%s ext=%zu",
         out.deviceCount, out.apiVersion.c_str(), out.extensions.size());
}

// ★ 进程级一次性探测: collect() 会被刷新逻辑重复调用,
//   而 vkCreateInstance 有 ~50-200ms 开销, 必须缓存。
const ProbeResult& getProbeResult() {
    static ProbeResult sResult;
    static std::once_flag sOnce;
    std::call_once(sOnce, []() { probeOnce(sResult); });
    return sResult;
}

}  // namespace

// 返回 String[]，布局:
//   [0] "count=<物理设备数>"
//   [1] "api=<major.minor.patch>"
//   [2] "gpu=<设备名>"
//   [3..] 扩展名 (均以 "VK_" 开头, 与前三个头部字段天然可区分)
// 不可用时返回 null。
// 注: Kotlin `object VulkanProbe` 中的 external fun 编译为实例方法,
//     故第二参为 jobject (而非 jclass)。此处不使用该参数。
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_rb_cybermonitorpro_data_source_VulkanProbe_nativeProbe(JNIEnv* env, jobject /*thiz*/) {
    const ProbeResult& r = getProbeResult();
    if (!r.available) return nullptr;

    jclass strClass = env->FindClass("java/lang/String");
    if (strClass == nullptr) return nullptr;

    std::vector<std::string> lines;
    lines.reserve(r.extensions.size() + 3);
    lines.push_back("count=" + std::to_string(r.deviceCount));
    lines.push_back("api=" + r.apiVersion);
    lines.push_back("gpu=" + r.gpuName);
    for (const auto& e : r.extensions) lines.push_back(e);

    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(lines.size()), strClass, nullptr);
    if (arr == nullptr) return nullptr;

    for (size_t i = 0; i < lines.size(); ++i) {
        jstring s = env->NewStringUTF(lines[i].c_str());
        if (s == nullptr) continue;
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);   // 避免超出默认 512 局部引用上限
    }
    return arr;
}
