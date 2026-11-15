package com.will.lightreader.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Soft, gently asymmetric paper corners shared by cards, inputs and sheets.
 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(topStart = 16.dp, topEnd = 12.dp, bottomEnd = 16.dp, bottomStart = 12.dp),
    medium = RoundedCornerShape(topStart = 20.dp, topEnd = 16.dp, bottomEnd = 20.dp, bottomStart = 16.dp),
    large = RoundedCornerShape(topStart = 28.dp, topEnd = 22.dp, bottomEnd = 28.dp, bottomStart = 22.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Expressive 动效入口：全部走 MaterialTheme.motionScheme（expressive 弹簧），
 *  并统一尊重系统「减少动态效果」——开启时降级为 snap（只留状态变化，去掉运动）。 */
object M3E {
    val ShapePill = RoundedCornerShape(999.dp)

    /** 默认空间动效：形状/边界变化（面板展开、指示器跟随）。 */
    @Composable
    fun <T> spatialSpec(): FiniteAnimationSpec<T> =
        if (Motion.reducedMotion()) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

    /** 快速空间动效：按压回弹等高频小交互。 */
    @Composable
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        if (Motion.reducedMotion()) snap() else MaterialTheme.motionScheme.fastSpatialSpec()

    /** 非空间动效：颜色/透明度（选中态变色等）。 */
    @Composable
    fun <T> effectsSpec(): FiniteAnimationSpec<T> =
        if (Motion.reducedMotion()) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()
}
