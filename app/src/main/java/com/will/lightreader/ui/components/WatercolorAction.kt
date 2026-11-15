package com.will.lightreader.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.will.lightreader.ui.theme.Motion

/** Named paper actions share icon, pigment, progress and touch language across the app. */
@Composable
fun WatercolorAction(label: String, icon: ImageVector, modifier: Modifier = Modifier,
    enabled: Boolean = true, busy: Boolean = false, progress: Float? = null,
    emphasis: Boolean = false, description: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = com.will.lightreader.ui.anim.rememberHapticTap()
    val paper = if (emphasis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (emphasis) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    val tint = ink.copy(alpha = if (enabled || busy) 1f else .38f)
    val reduced = Motion.reducedMotion()
    Row(modifier.heightIn(min = 52.dp).paperFeedback(interaction, .985f, ink)
        .watercolorPaper(paper, ink, if (emphasis) .92f else .7f)
        .semantics {
            role = Role.Button
            if (description != null) contentDescription = description
            if (busy) stateDescription = "处理中"
        }
        .clickable(enabled = enabled, interactionSource = interaction, indication = null) { haptic(); onClick() }
        .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = tint, strokeWidth = 1.5.dp)
        else Crossfade(icon, animationSpec = tween(if (reduced) 0 else 100), label = "paper-action-icon") {
            Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium,
                color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp), color = ink,
                trackColor = ink.copy(alpha = .12f), drawStopIndicator = {})
        }
    }
}
