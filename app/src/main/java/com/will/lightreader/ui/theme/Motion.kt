package com.will.lightreader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

internal val LocalReducedMotion = staticCompositionLocalOf { false }

/** One observer per theme, rather than a settings read in every book and button. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        val events = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) reduced = read()
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        lifecycle.addObserver(events)
        onDispose {
            resolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(events)
        }
    }
    return reduced
}

/** 动效系统：减速进入、加速退出、弹性回弹（与 dsh-roxy-motion 同语言）。 */
object Motion {
    val Decelerate = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Accelerate = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /**
     * 退出缓动：强 ease-out（起步快、尾部缓）。
     * 退出也要「起步快」，故用与 Decelerate 同族的强 ease-out 反向。
     */
    val ExitOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** 页面转场 fade-through：320ms */
    val PageEnter = tween<Float>(320, easing = Decelerate)
    val PageExit = tween<Float>(160, easing = ExitOut)

    /** 列表项进场：淡入 + 上移 8dp，300ms（配合 stagger 30ms） */
    val ItemEnter = tween<Float>(300, easing = Decelerate)

    /** 按钮按压回弹 */
    val PressSpring = spring<Float>(stiffness = Spring.StiffnessMedium, dampingRatio = 0.88f)

    /** 面板展开：柔和生长（低刚度 + 高阻尼 = 丝滑不弹硬） */
    val SoftSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f)

    /** Tab 指示器弹性跟随 */
    val TabSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.8f)

    /** 玻璃面板浮出：240ms */
    val GlassEnter = tween<Float>(240, easing = Decelerate)

    /** 翻章：280ms */
    val PageTurn = tween<Float>(280, easing = Decelerate)

    /** 进度等数值 */
    val Value = tween<Float>(400, easing = Decelerate)

    /** stagger 步长 */
    const val StaggerMs = 30

    /**
     * 降低动画：跟随系统「减少动态效果」设置。
     * 开启时动效降级为「只留 opacity/color、去掉位移与缩放」（保留可理解性，去掉运动）。
     * 各动效处读它决定是否保留 transform 位移/缩放。
     */
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    fun reducedMotion(): Boolean {
        return LocalReducedMotion.current
    }
}
