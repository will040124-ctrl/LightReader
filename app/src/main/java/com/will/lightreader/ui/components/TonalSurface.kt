package com.will.lightreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxScope
import com.will.lightreader.ui.theme.M3E
import com.will.lightreader.ui.theme.SoftLevel
import com.will.lightreader.ui.theme.SoftPanel

/**
 * Tonal 面板：实色色阶分层，无一滴玻璃。
 * 光影走 UI 标准 01：默认 [SoftLevel.Card]（发丝边 + 内部顶光），
 * 需要浮起感（继续阅读 / 作者叠卡）时传 [SoftLevel.Raised]。
 */
@Composable
fun TonalPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    level: SoftLevel = SoftLevel.Card,
    content: @Composable BoxScope.() -> Unit,
) {
    SoftPanel(modifier = modifier, shape = shape, color = color, level = level, content = content)
}

/**
 * 按压反馈：scale .97 + expressive 弹簧（走硬件层）；减少动态效果时不动。
 * 用法：Modifier.pressScale(interaction).clickable(interaction, indication = null, ...)
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = com.will.lightreader.ui.theme.Motion.reducedMotion()
    val scale = animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = M3E.fastSpatialSpec(),
        label = "press-scale",
    )
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
