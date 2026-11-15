package com.will.lightreader.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.will.lightreader.ui.components.watercolorSurface

/**
 * 表面抬升等级（UI 标准 01）：
 * [Card] 只靠一根发丝边 + 顶光立住；[Raised] 再加一层短投影，用于按钮/继续阅读/浮层。
 */
enum class SoftLevel { Card, Raised }

/**
 * 柔光纸感表面。
 *
 * 边角干净的关键：全程只有 **一层** 投影，且由 shape 轮廓直接生成；顶光不是描边，
 * 而是被 clip 裁进形状内部的顶部渐变。因此圆角处不会出现「阴影外沿 + 描边高光」的双线。
 */
@Composable
fun Modifier.softSurface(
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    level: SoftLevel = SoftLevel.Card,
): Modifier {
    // Flat printed stock: no sheen or floating shadow. Raised panels get a slightly stronger rule.
    return this.clip(shape)
        .watercolorSurface(color, MaterialTheme.colorScheme.primary, .055f)

}

/**
 * 柔光面板：替代裸露的 Surface 卡片。内容色跟随面板色自动推导。
 */
@Composable
fun SoftPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    level: SoftLevel = SoftLevel.Card,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides contentColorFor(color)
    ) {
        Box(
            modifier = modifier.softSurface(shape, color, level),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}
