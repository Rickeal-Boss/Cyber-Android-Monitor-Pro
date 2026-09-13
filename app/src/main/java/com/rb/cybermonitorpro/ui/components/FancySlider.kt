package com.rb.cybermonitorpro.ui.components

import com.rb.cybermonitorpro.AppSettings
import com.rb.cybermonitorpro.HapticUtils
import com.rb.cybermonitorpro.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.ui.theme.NeonMagentaPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleDeep
import com.rb.cybermonitorpro.ui.theme.NeonSteelBlue
import com.rb.cybermonitorpro.ui.theme.TextOnPrimary
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * FancySlider — 厚胶囊轨道 + 圆钮 (knob) thumb + 居中齿轮图标旋转。
 *
 * 使用 M3 原生 Slider 负责拖动/无障碍/语义，只替换 thumb 与 track 渲染：
 * - track：38dp 厚胶囊，已选段 NeonMagentaPurple / 未选段钢蓝 30%，NeonPurpleDeep 30% 描边；
 * - thumb（SLIDER-04）：三层圆钮 —— 柔光环 (knobColor 18% alpha, 半径外扩 3dp) /
 *   实心圆 (knobColor) / 2dp 描边 (knobBorderColor)，直径 knobDiameter (默认 = trackHeight)，
 *   中心 ic_cyber_settings 齿轮图标按进度旋转（默认 1080° = 3 整圈首尾同角回正），
 *   最左端圆钮完整可见（图标居中，不再左移半身位）。
 *
 * SLIDER-01：material3 1.3.2 的 track lambda 签名是 (SliderState) -> Unit，
 * SliderPositions.fraction 已废弃不可用，进度比由 value/valueRange 自行计算。
 * SLIDER-02：thumb 用 48dp Box 包裹保住触控目标（minimumInteractiveComponentSize 在当前依赖下解析失败，已弃用）。
 * SLIDER-03：松手归位不再硬切 —— 外部 value 变更经 Animatable + tween(LinearOutSlowInEasing)
 *   缓动滑到目标档位（snapAnimationMillis <= 0 时改 snapTo 直切，供未来实时值驱动调用方逃生）。
 *   拖拽期 onValueChange 走 snapTo 跟手，经 Animatable 的 MutatorMutex 自动抢占进行中的归位动画
 *   （同款先例 CyberJoystickSwitch.kt）。
 * SLIDER-05：过档震动 —— tickStops 三态（null = 按 steps 自动推导，M3 口径 steps+2 档含两端；
 *   emptyList() = 显式关闭；非空 = 显式档位），nearestStopIndex 变更即 HapticUtils.stepTick
 *   （内置 50ms 节流 + 震动开关判断）；effect 启动时 refreshSettings 同步缓存，
 *   修 cachedEnabled 初值恒 true、冷启动后缓存态错误的既有缺陷。
 * SLIDER-07：轨道绘制两端各外扩一个端帽半径（trackHeight/2）——M3 把 thumb 中心放在轨道槽位
 *   端点上，不外扩则圆钮半悬在胶囊之外；外扩后端帽圆心与两端圆钮圆心重合，圆钮嵌进圆角内
 *   与轨道视觉融合（对齐参考截图）。
 * SLIDER-08：已选段右边界 = 圆钮描边右缘（中心+半径）——已选色完整包裹圆钮、未选段自圆钮
 *   右缘开始；右端帽圆与圆钮圆重合，无缝隙无灰角。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FancySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    icon: Painter = painterResource(R.drawable.ic_cyber_settings),
    iconSize: Dp = 26.dp,
    trackHeight: Dp = 38.dp,
    // SLIDER-04: 圆钮直径默认 = 轨道高（Kotlin 默认参数引用前序参数）
    knobDiameter: Dp = trackHeight,
    knobColor: Color = NeonPurpleDeep,
    knobBorderColor: Color = Color.White.copy(alpha = 0.75f),
    rotationDegrees: Float = 1080f,
    // SLIDER-05: 显式档位；null = 按 steps 自动推导, emptyList() = 关闭过档震动
    tickStops: List<Float>? = null,
    // SLIDER-03: 归位缓动时长；<=0 时外部 value 变更直接 snapTo（逃生口）
    snapAnimationMillis: Int = 240,
    // P1→SLIDER-04: 图标色默认 TextOnPrimary（浅色主题 White / 暗色主题 #14161A，语义 = 圆钮中心图标色，
    // 在紫红 (NeonMagentaPurple) 轨道与圆钮上对比清晰; 实心底色改由 knobColor 承担）
    thumbColor: Color = TextOnPrimary,
    activeTrackColor: Color = NeonMagentaPurple,
    inactiveTrackColor: Color = NeonSteelBlue.copy(alpha = 0.3f),
    borderColor: Color = NeonPurpleDeep.copy(alpha = 0.3f),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    // SLIDER-03: 显示值走 Animatable —— 拖拽期 snapTo 跟手, 松手/外部变更时 tween 缓动归位
    val displayValue = remember { Animatable(value) }
    // 闭包捕获的 value/snapAnimationMillis 会陈旧（同 CyberJoystickSwitch 先例）——
    // 经 updatedState 保持实时, 否则 snapshotFlow 读不到新值, 归位动画永不触发
    val currentValue by rememberUpdatedState(value)
    val currentSnapMillis by rememberUpdatedState(snapAnimationMillis)

    LaunchedEffect(Unit) {
        snapshotFlow { currentValue }.collect { v ->
            if (dragging || displayValue.value == v) return@collect
            // tween 每次动画启动时构建一次, 廉价; LinearOutSlowInEasing 是单例
            if (currentSnapMillis <= 0) displayValue.snapTo(v)
            else displayValue.animateTo(
                v,
                tween(durationMillis = currentSnapMillis, easing = LinearOutSlowInEasing),
            )
        }
    }

    // SLIDER-05: 过档震动 —— displayValue.value 是 snapshot state, snapshotFlow 可观察
    val effectiveStops = remember(tickStops, steps, valueRange) {
        // M3 实证: stepsToTickFractions(steps) = steps+2 档(含两端), 与 FancySliderRotation.forSteps 注释口径一致
        tickStops ?: if (steps > 0) {
            List(steps + 2) { i ->
                valueRange.start + i * (valueRange.endInclusive - valueRange.start) / (steps + 1)
            }
        } else emptyList()
    }
    if (effectiveStops.isNotEmpty() && enabled) {
        LaunchedEffect(effectiveStops) {
            // 修既有缺陷: HapticUtils cachedEnabled 初值恒 true 且仅设置页才刷新, 冷启动后缓存态错误
            HapticUtils.refreshSettings(AppSettings.getInstance(context))
            var lastIdx = nearestStopIndex(displayValue.value, effectiveStops)
            snapshotFlow { nearestStopIndex(displayValue.value, effectiveStops) }.collect { idx ->
                if (idx != lastIdx) {
                    HapticUtils.stepTick(context)   // 内置 50ms 节流 + 震动开关判断
                    lastIdx = idx
                }
            }
        }
    }

    Slider(
        value = displayValue.value,
        onValueChange = { v ->
            dragging = true
            // snapTo 即时跟手; MutatorMutex 自动抢占进行中的归位动画
            scope.launch { displayValue.snapTo(v) }
            onValueChange(v)
        },
        // 先 dragging=false 再回调调用方 —— 调用方在同一回调里 snap 写 value, snapshot 同批提交
        onValueChangeFinished = {
            dragging = false
            onValueChangeFinished?.invoke()
        },
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = thumbColor,
            activeTrackColor = activeTrackColor,
            inactiveTrackColor = inactiveTrackColor,
        ),
        // SLIDER-01: track 收 SliderState, fraction 自算且下沉到 draw 阶段读 state.value
        // （归位动画期间逐帧重绘不重组, 绝不回退 positions.fraction）
        track = { state ->
            Box(
                modifier = Modifier.fillMaxWidth().height(trackHeight),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
                    val trackFraction = if (valueRange.endInclusive > valueRange.start) {
                        ((state.value - valueRange.start) /
                            (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                    } else 0f
                    // SLIDER-07: 轨道两端各外扩一个端帽半径(=trackHeight/2)，使端帽圆心与
                    // fraction=0/1 的 thumb 圆心重合 —— 圆钮嵌进端帽、与圆角视觉融合（对齐参考截图），
                    // 消除旧版"圆钮半悬在轨道端点之外"的脱离感。M3 布局把 thumb 中心放在
                    // 轨道槽位端点上(L829)，外扩量 = 端帽半径即恰好同心。DrawScope 无裁剪，越界绘制可见。
                    val ext = size.height / 2f                  // 外扩量 = 端帽半径
                    val left = -ext
                    val totalWidth = size.width + 2f * ext
                    val cap = size.height / 2f
                    // SLIDER-08: 已选段右边界 = 圆钮描边右缘（圆钮中心+半径）——已选色完整包裹
                    // 圆钮、未选段自圆钮右缘开始；已选段右端帽圆(圆心=圆钮圆心, r=轨道高/2)与
                    // 圆钮圆(knobDiameter 默认=轨道高)完全重合，无缝隙无灰角
                    val knobRadius = knobDiameter.toPx() / 2f
                    val activeWidth = (ext + trackFraction * size.width + knobRadius)
                        .coerceIn(0f, totalWidth)
                    drawRoundRect(  // 未选段（外扩后的整条胶囊）
                        color = inactiveTrackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(totalWidth, size.height),
                        cornerRadius = CornerRadius(cap),
                    )
                    drawRoundRect(  // 已选段：外扩左端 → 圆钮右缘（包裹圆钮）
                        color = activeTrackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(activeWidth, size.height),
                        cornerRadius = CornerRadius(cap),
                    )
                    val stroke = 2.dp.toPx()  // 描边
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(left + stroke / 2, stroke / 2),
                        size = Size(totalWidth - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(cap - stroke / 2),
                        style = Stroke(width = stroke),
                    )
                }
            }
        },
        // SLIDER-02: thumb 用 48dp Box 保证触控目标 (不依赖 minimumInteractiveComponentSize)
        // SLIDER-04: 圆钮三层结构 (halo / 实心 / 描边) + 居中图标
        thumb = { state ->
            Box(
                modifier = Modifier.size(48.dp),   // 48dp 触控目标
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(knobDiameter)) {
                    // ① 柔光环
                    drawCircle(
                        color = knobColor.copy(alpha = 0.18f),
                        radius = size.minDimension / 2f + 3.dp.toPx(),
                    )
                    // ② 实心圆
                    drawCircle(color = knobColor)
                    // ③ 描边
                    drawCircle(
                        color = knobBorderColor,
                        radius = size.minDimension / 2f - 1.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = thumbColor,
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            // f 在 draw 阶段由 state.value 自算 —— 与 M3 放置 thumb 的 fraction
                            // 严格同源, 归位动画零错位（graphicsLayer/draw lambda 内读 snapshot state
                            // 只触发重绘不触发重组）
                            val f = if (valueRange.endInclusive > valueRange.start) {
                                ((state.value - valueRange.start) /
                                    (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                            } else 0f
                            rotationZ = rotationDegrees * f
                        },
                )
            }
        },
    )
}

/**
 * SLIDER-05: 最近档位索引 —— minByOrNull 对非均匀档位（如 500/1000/2000/3000/5000）
 * 才是正确语义, tie-break 取第一个最小值, 与调用方 snapToOption.minByOrNull 口径一致。
 * 严禁引入 kotlin.math.roundToInt 顶层导入（项目历史编译坑）。
 */
private fun nearestStopIndex(value: Float, stops: List<Float>): Int =
    stops.indices.minByOrNull { abs(value - stops[it]) } ?: -1
