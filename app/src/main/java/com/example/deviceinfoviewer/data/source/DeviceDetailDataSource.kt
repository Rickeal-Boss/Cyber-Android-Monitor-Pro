package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.example.deviceinfoviewer.data.model.DeviceDetailInfo
import java.util.UUID

/**
 * 设备详细信息数据源 — 全部通过公开 Android API，无需 root / ADB
 */
class DeviceDetailDataSource(private val context: Context) {

    companion object {
        private const val TAG = "DeviceDetailDS"
        private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    }

    fun collect(): DeviceDetailInfo {
        val info = DeviceDetailInfo()
        collectDisplay(info)
        collectGraphics(info)
        collectVulkan(info)
        collectCodecs(info)
        collectDrm(info)
        collectTelephony(info)
        collectBluetooth(info)
        collectCamera(info)
        collectMisc(info)
        return info
    }

    // ── Display ──
    private fun collectDisplay(info: DeviceDetailInfo) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            info.resolution = "${metrics.widthPixels} × ${metrics.heightPixels}"
            info.densityDpi = metrics.densityDpi
            info.density = metrics.density

            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.let { display ->
                val mode = display.mode
                info.refreshRateHz = mode?.refreshRate ?: 0f
            }

            // HDR — Display.getHdrCapabilities() API 33+
            info.hdrCapabilities = emptyList()

            // Physical size
            val xdpi = metrics.xdpi
            val ydpi = metrics.ydpi
            if (xdpi > 0 && ydpi > 0) {
                val w = metrics.widthPixels / xdpi
                val h = metrics.heightPixels / ydpi
                info.physicalSizeInches = kotlin.math.sqrt(w * w + h * h).toFloat()
            }
        } catch (_: Throwable) { Log.w(TAG, "Display collection failed") }
    }

    // ── OpenGL ES（纯 Java 安全采集，不调用 native 层）──
    private fun collectGraphics(info: DeviceDetailInfo) {
        try {
            val pm = context.packageManager
            val reqGlVersion = try {
                pm.systemAvailableFeatures
                    .filter { it.name?.startsWith("android.hardware.opengles") == true }
                    .maxOfOrNull { f ->
                        f.name?.removePrefix("android.hardware.opengles.aep")?.toIntOrNull() ?: 0
                    } ?: 2
            } catch (_: Throwable) { 2 }
            info.glEsVersion = "OpenGL ES $reqGlVersion"

            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val ci = am?.deviceConfigurationInfo
                info.glVendor = ci?.glEsVersion ?: ""
                info.glRenderer = ""
            } catch (_: Throwable) {}
            info.glExtensions = emptyList()
        } catch (_: Throwable) {}
    }

    // ── Vulkan + 光线追踪 ──
    private fun collectVulkan(info: DeviceDetailInfo) {
        try {
            val pm = context.packageManager

            // Vulkan 版本检测 (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val features = pm.systemAvailableFeatures
                // 查找 Vulkan 硬件版本
                for (f in features) {
                    val name = f.name ?: continue
                    when {
                        name == "android.hardware.vulkan.version" -> {
                            // 从版本号提取 Vulkan API 版本
                            info.vulkanVersion = extractVulkanVersion(f)
                        }
                        name == "android.hardware.vulkan.level" -> {
                            info.vulkanApiLevel = extractVulkanLevel(f)
                        }
                        name.contains("vulkan") && name.contains("ray") -> {
                            info.rayTracingSupported = true
                        }
                    }
                }
            }

            // 系统属性补充
            if (info.vulkanVersion.isEmpty()) {
                val vkProp = SysFsReader.readProp("ro.hardware.vulkan")
                if (vkProp.isNotEmpty()) info.vulkanVersion = vkProp
            }
            if (info.vulkanApiLevel.isEmpty()) {
                // Vulkan 级别: 0=无, 1=Vulkan 1.0, 2=Vulkan 1.1, 3=Vulkan 1.3
                val level = SysFsReader.readPropInt("ro.vulkan.api.level")
                if (level > 0) info.vulkanApiLevel = when (level) {
                    1 -> "Vulkan 1.0"
                    2 -> "Vulkan 1.1"
                    3 -> "Vulkan 1.3"
                    else -> "Level $level"
                }
            }

            // 光线追踪检测：检查 Vulkan 光追扩展
            if (!info.rayTracingSupported) {
                info.rayTracingSupported = pm.hasSystemFeature(
                    "android.hardware.vulkan.ray_tracing"
                )
            }
            // 备用：sysfs 属性
            if (!info.rayTracingSupported) {
                val rtProp = SysFsReader.readProp("ro.vendor.gpu.ray_tracing")
                if (rtProp == "1" || rtProp == "true") info.rayTracingSupported = true
            }
        } catch (_: Throwable) { Log.w(TAG, "Vulkan collection failed") }
    }

    private fun extractVulkanVersion(feature: android.content.pm.FeatureInfo): String {
        return try {
            // version 字段是整数，编码了 Vulkan 版本 (major.minor)
            val ver = feature.version
            val major = ver shr 22
            val minor = (ver shr 12) and 0x3FF
            if (major > 0) "Vulkan $major.$minor" else ""
        } catch (_: Throwable) { "" }
    }

    private fun extractVulkanLevel(feature: android.content.pm.FeatureInfo): String {
        return try {
            when (feature.version) {
                0 -> "No Vulkan"
                1 -> "Vulkan 1.0"
                2 -> "Vulkan 1.1"
                3 -> "Vulkan 1.3"
                else -> "Level ${feature.version}"
            }
        } catch (_: Throwable) { "" }
    }

    // ── Media Codecs ──
    private fun collectCodecs(info: DeviceDetailInfo) {
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val audio = mutableListOf<String>()
            val video = mutableListOf<String>()
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue
                val name = codecInfo.name
                when {
                    codecInfo.isAudioCodec() -> audio.add(name)
                    codecInfo.isVideoCodec() -> video.add(name)
                }
            }
            info.audioCodecs = audio
            info.videoCodecs = video
        } catch (_: Throwable) { Log.w(TAG, "Codec collection failed") }
    }

    private fun MediaCodecInfo.isAudioCodec(): Boolean {
        return try {
            supportedTypes.any { it.startsWith("audio/") }
        } catch (_: Throwable) { false }
    }

    private fun MediaCodecInfo.isVideoCodec(): Boolean {
        return try {
            supportedTypes.any { it.startsWith("video/") }
        } catch (_: Throwable) { false }
    }

    // ── DRM / Widevine ──
    private fun collectDrm(info: DeviceDetailInfo) {
        try {
            val drm = MediaDrm(WIDEVINE_UUID)
            val props = drm.getPropertyString("securityLevel")
            info.widevineLevel = props ?: ""
            drm.release()
        } catch (_: Throwable) { info.widevineLevel = "不支持" }

        // All supported DRM schemes
        val schemes = mutableListOf<String>()
        try {
            if (android.media.MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID))
                schemes.add("Widevine")
            if (android.media.MediaDrm.isCryptoSchemeSupported(UUID.fromString("9a04f079-9840-4286-ab92-e65be0885f95")))
                schemes.add("PlayReady")
            info.drmSchemes = schemes
        } catch (_: Throwable) {}
    }

    // ── Telephony / SIM ──
    private fun collectTelephony(info: DeviceDetailInfo) {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            info.simOperator = tm.simOperatorName ?: ""
            info.simMccMnc = "${tm.simOperator}"
            info.networkCountryIso = tm.networkCountryIso ?: ""

            val phoneType = when (tm.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                else -> "未知"
            }
            info.phoneType = phoneType

            // Dual SIM detection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    val count = tm.phoneCount
                    info.isDualSim = count > 1
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) { Log.w(TAG, "Telephony collection failed") }
    }

    // ── Bluetooth ──
    private fun collectBluetooth(info: DeviceDetailInfo) {
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                info.bluetoothSupported = true
                info.bluetoothName = adapter.name ?: ""
                @Suppress("MissingPermission")
                info.bluetoothAddress = try { adapter.address } catch (_: SecurityException) { "" }
            }
        } catch (_: Throwable) {}
    }

    // ── Camera ──
    private fun collectCamera(info: DeviceDetailInfo) {
        try {
            val cm = context.packageManager
            val hasFlash = cm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
            info.cameraIds = mutableListOf<String>().apply {
                if (cm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) add("后置")
                if (cm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) add("前置")
                if (hasFlash) add("闪光灯")
            }
        } catch (_: Throwable) {}
    }

    // ── Miscellaneous ──
    private fun collectMisc(info: DeviceDetailInfo) {
        val pm = context.packageManager
        info.hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        info.hasKeyboard = context.resources.configuration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS

        // Touchscreen
        info.touchscreenType = when {
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> "5指以上多点触控"
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> "多点触控"
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> "多点触控(基础)"
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) -> "支持"
            else -> "不支持"
        }

        // USB
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            info.usbConnected = (plugged == BatteryManager.BATTERY_PLUGGED_USB)
        } catch (_: Throwable) {}
    }
}
