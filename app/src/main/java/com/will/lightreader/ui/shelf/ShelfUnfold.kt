package com.will.lightreader.ui.shelf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.will.lightreader.ui.theme.Motion

@Composable
internal fun ShelfUnfold(expanded: Boolean, content: @Composable (Boolean) -> Unit) {
    val reduced = Motion.reducedMotion()
    AnimatedContent(targetState = expanded, modifier = Modifier.fillMaxSize(), transitionSpec = {
        // Replace the whole scene at the fade boundary so its books keep their own occlusion.
        val fadeOutMs = if (reduced) 0 else 125
        fadeIn(tween(if (reduced) 0 else 185, delayMillis = fadeOutMs)) togetherWith fadeOut(tween(fadeOutMs))
    }, label = "shelf-unfold", content = { open -> content(open) })
}
