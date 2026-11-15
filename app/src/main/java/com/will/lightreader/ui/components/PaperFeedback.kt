package com.will.lightreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.will.lightreader.ui.theme.M3E

/** A finite wash responds to touch, keyboard focus and pointer hover. No idle animation. */
@Composable
fun Modifier.paperFeedback(interaction: MutableInteractionSource, pressedScale: Float = .99f,
    ink: Color = MaterialTheme.colorScheme.primary): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressure = animateFloatAsState(if (pressed) .8f else if (focused) .7f else if (hovered) .3f else 0f,
        M3E.effectsSpec(), label = "paper-pressure")
    return pressScale(interaction, pressedScale).watercolorWash(ink) { pressure.value }
}
