package com.rb.cybermonitorpro.ui.effects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 顶部「夜光条」（CyberNightlightHost / HdrLumeSurfaceView 全屏边缘闪光）的独立开关。
 *
 * 与 CyberNightlightSwitch（TurboXDR 局部 HDR 贴片总开关）解耦：
 * - 夜光条关闭时，局部 HDR 描边/文字/图标仍可正常点亮；
 * - TurboXDR 关闭时，夜光条也不会亮起。
 *
 * 运行期状态在 DeviceApplication.onCreate 从 AppSettings 注入，设置页切换即时生效。
 */
object NightlightBarSwitch {
    var enabled by mutableStateOf(false)
}
