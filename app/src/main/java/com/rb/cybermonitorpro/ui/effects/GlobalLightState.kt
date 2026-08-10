package com.rb.cybermonitorpro.ui.effects

import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Windows 10 Fluent Design 风格全局光照状态
 *
 * 核心概念 (基于 Microsoft RevealBrush + Fluent Design System):
 * - 全局光照点 (lightPosition): 鼠标悬停/触摸按下时在整个窗口中的坐标
 * - 光照状态 (mode): 空闲/悬停/触摸 三种模式驱动不同视觉行为
 * - 动画: 使用 Spring 物理弹簧动画实现平滑跟随,避免突兀跳变
 *
 * 工作流程:
 * 1. GlobalLightProvider 在根容器捕获全部 pointer 事件
 * 2. 更新 GlobalLightState 的 target position
 * 3. 弹簧动画平滑过渡到目标位置
 * 4. 各子元素通过 revealLight() Modifier 读取当前动画位置
 * 5. 在本地坐标空间绘制径向渐变光照
 */
@Stable
class GlobalLightState {

    /** 光照模式 */
    enum class Mode {
        /** 无交互 — 光照效果隐藏 */
        IDLE,
        /** 鼠标/触控笔悬停 — 光照跟随光标 */
        HOVER,
        /** 手指触摸 — 光照跟随触摸点 (更亮、更大) */
        TOUCH
    }

    // 目标光照位置 (CompositionLocal 透传, 用于动画计算起点)
    var targetPosition by mutableStateOf(Offset.Zero)
        private set

    // 当前模式
    var mode by mutableStateOf(Mode.IDLE)
        private set

    // 全局可见性 (覆盖层显示时主动隐藏, 避免在设置页/悬浮窗等页面误显)
    var visible by mutableStateOf(true)

    // 时间戳 — 用于判断新鲜度 (超过一定时间无事件则渐隐)
    var lastEventTime by mutableStateOf(0L)
        private set

    /**
     * 更新光照位置和模式
     * @param position 窗口坐标系下的指针位置
     * @param isTouch true=触摸事件, false=鼠标/笔悬停
     */
    fun update(position: Offset, isTouch: Boolean) {
        // 全局光照总开关关闭时静默丢弃事件 — 不写 State, 弹簧/绘制全链路零开销
        if (!GlobalLightSwitch.enabled) return
        targetPosition = position
        mode = if (isTouch) Mode.TOUCH else Mode.HOVER
        lastEventTime = System.currentTimeMillis()
    }

    /**
     * 切换到空闲模式 (鼠标离开窗口 / 手指抬起)
     */
    fun toIdle() {
        mode = Mode.IDLE
    }

    /**
     * 强制隐藏光照 (覆盖层出现时调用)
     */
    fun hide() {
        visible = false
        mode = Mode.IDLE
    }

    /**
     * 恢复光照
     */
    fun show() {
        visible = true
    }
}

/**
 * 动画后的当前位置 — 带 Spring 物理弹簧平滑跟随
 *
 * 在 GlobalLightProvider 中调用, 提供平滑的 offset 动画
 */
@Composable
fun rememberAnimatedLightPosition(state: GlobalLightState): Offset {
    // ★ 淡出时保持最后位置 (仅强度动画归零), 避免位置弹飞致淡出被截断; 仅覆盖层 hide() 才弹飞
    val target = if (state.visible)
        state.targetPosition
    else
        // 隐藏时弹飞出屏: 用 -100_000f 哨兵替代 NEGATIVE_INFINITY, 避免弹簧趋向无穷产生超大中间坐标 (code smell)
        Offset(-100_000f, -100_000f)

    val animatedPos by animateValueAsState(
        targetValue = target,
        typeConverter = TwoWayConverter(
            convertToVector = { AnimationVector2D(it.x, it.y) },
            convertFromVector = { Offset(it.v1, it.v2) }
        ),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,   // 原 MediumBouncy — 消除回弹，光效不拖尾
            stiffness = Spring.StiffnessMedium             // 原 StiffnessMediumLow — 提刚度，更快收敛
        ),
        label = "lightPosition"
    )
    return animatedPos
}

/**
 * 全局光照总开关 — 设置页可整体关闭以降低性能开销
 *
 * 初始值由 DeviceApplication.onCreate 从 AppSettings 读取注入;
 * 设置页开关切换时同步更新, 全 App 即时生效 (Compose State 驱动)。
 *
 * 关闭时:
 * - GlobalLightState.update() 直接丢弃指针事件, 不再触发弹簧动画
 * - revealLight() 的 isActive 恒为 false, 跳过全部径向渐变光栅绘制
 */
object GlobalLightSwitch {
    var enabled by mutableStateOf(true)
}

/**
 * CompositionLocal — 全局光照状态的传递通道
 *
 * 使用方式:
 * - GlobalLightProvider 提供 (向上游)
 * - revealLight() / acrylic() Modifier 消费 (向下游)
 * - 默认值: 空状态 (无光照效果)
 */
val LocalLightState = compositionLocalOf { GlobalLightState() }
