package com.rb.cybermonitorpro.ui.effects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * CyberNightlight TurboXDR 总开关与强度 — 仿电子表夜光（局部 HDR 增亮）。
 *
 * 设计：
 * - enabled/intensity 由 Compose State 驱动，全 App 即时生效（设置页切换 / 渲染层消费都读这里）。
 * - 初始值由 DeviceApplication.onCreate 从 AppSettings 注入（与 GlobalLightSwitch 同机制）。
 * - 默认关闭（false）—— 用户明确要求默认关；低版本（<API 35）设备开启后渲染层静默降级。
 *
 * 命名约定：真 HDR 全程以 `TurboXdr` 前缀标识，以区别于 CardGradientBorder 中占用的
 * `hdrHighlight`（"假 HDR" 高光，仅 SDR 内更亮的白色细线）。
 *
 * 双通道强度控制：
 * - intensity：CyberNightlight 边缘呼吸光晕强度 [0, 1]（原字段，不变）。
 * - patchIntensity：局部 HDR 元素增亮倍率 [1.0, 8.0]（新增，HdrPatchHost 消费）。
 *   两者独立滑块控制，叠加效果：边缘环境光 + 局部元素点亮。
 */
object CyberNightlightSwitch {
    var enabled by mutableStateOf(false)
    var intensity by mutableFloatStateOf(0.6f)

    /** 局部 HDR 增亮倍率（HdrPatchHost / PatchRenderer 消费），1.0=无增亮，8.0=最大。 */
    var patchIntensity by mutableFloatStateOf(2.0f)
}
