package com.will.lightreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.will.lightreader.ui.theme.M3E
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.will.lightreader.R

/** Preserve ears, hand and book, with room around the silhouette instead of a square crop. */
@Composable
fun WatercolorCompanion(waving: Boolean = false, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.matchParentSize().padding(top = 12.dp)
            .watercolorWash(MaterialTheme.colorScheme.primary, .28f))
        AsyncImage(model = if (waving) R.drawable.watercolor_wave else R.drawable.watercolor_read,
            contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize().padding(3.dp))
    }
}

/** Common paper-and-pigment treatment for settings, filters and reader controls. */
@Composable
fun WatercolorChoice(text: String, active: Boolean = false, modifier: Modifier = Modifier,
    paper: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    ink: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = com.will.lightreader.ui.anim.rememberHapticTap()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pigment = animateFloatAsState(if (!enabled) .04f else if (pressed || focused) .9f else if (active) .5f else .1f,
        M3E.effectsSpec(), label = "choice-pressure")
    val base = animateColorAsState(lerp(paper, ink, if (active) .12f else .025f),
        M3E.effectsSpec(), label = "pigment-choice")
    Box(modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).pressScale(interaction, .985f)
        .watercolorPaper({ base.value }, ink, if (active) .7f else .35f)
        .watercolorWash(ink) { pigment.value }
        .semantics {
            selected = active
            stateDescription = if (active) "已选择" else "未选择"
            role = Role.Button
        }
        .clickable(enabled = enabled, interactionSource = interaction, indication = null) { haptic(); onClick() }
        .padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = contentColor.copy(alpha = if (enabled) 1f else .38f), style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun WatercolorHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, Modifier.watercolorWash(MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif))
        Spacer(Modifier.height(10.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
