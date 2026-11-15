package com.will.lightreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.will.lightreader.ui.anim.rememberHapticTap
import com.will.lightreader.ui.theme.M3E

/**
 * tonal 分段控件：surfaceContainerHigh 轨道 + secondaryContainer 滑块。
 * 书架筛选 / 发现页签 / 阅读抽屉共用；滑块只做位置动画，文字色走淡变。
 */
@Composable
fun TonalSegmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    trackColor: Color = Color.Transparent,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    badge: (Int) -> String? = { null },
) {
    if (labels.isEmpty()) return
    val index = selectedIndex.coerceIn(0, labels.lastIndex)
    val haptic = rememberHapticTap()
    val lineHeight = when {
        textStyle.lineHeight.isSp -> textStyle.lineHeight
        textStyle.fontSize.isSp -> textStyle.fontSize * 1.4f
        else -> 20.sp
    }
    val rowHeight = maxOf(height, 48.dp, with(LocalDensity.current) { lineHeight.toDp() } + 16.dp)
    BoxWithConstraints(
        modifier
            .height(rowHeight)
            .selectableGroup().clip(RoundedCornerShape(18.dp)).background(trackColor.copy(alpha = trackColor.alpha * .35f)),
    ) {
        val segW = maxWidth / labels.size
        val washWidth = minOf(segW * .88f, 132.dp)
        val thumbX = animateDpAsState(segW * index, M3E.spatialSpec(), label = "seg-thumb")
        Box(
            Modifier.align(Alignment.CenterStart).offset { IntOffset((thumbX.value + (segW - washWidth) / 2).roundToPx(), 0) }
                .width(washWidth).height(rowHeight - 8.dp).watercolorWash(thumbColor),
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val active = i == index
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val focused by interaction.collectIsFocusedAsState()
                val pressure = animateFloatAsState(if (pressed || focused) .65f else 0f,
                    M3E.effectsSpec(), label = "tab-pressure")
                val fg by animateColorAsState(
                    if (active) activeContentColor else inactiveContentColor,
                    M3E.effectsSpec(), label = "seg-fg",
                )
                Row(
                    modifier = Modifier.weight(1f).fillMaxHeight().pressScale(interaction, .985f)
                        .watercolorWash(thumbColor) { pressure.value }
                        .selectable(
                            selected = active, role = Role.Tab,
                            interactionSource = interaction,
                            indication = null,
                        ) {
                            if (!active) {
                                haptic()
                                onSelect(i)
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        modifier = Modifier.weight(1f, fill = false),
                        style = textStyle,
                        color = fg,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    badge(i)?.let {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = fg,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
