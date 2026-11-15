package com.will.lightreader.ui.shelf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import com.will.lightreader.ui.theme.Motion
import kotlin.math.hypot

@Stable
class ShelfTouch {
    var origin by mutableStateOf(Offset.Zero)
    var pointer by mutableStateOf<Offset?>(null)
    var fastScroll by mutableStateOf(false)
    val bounds = mutableStateMapOf<String, Rect>()

    fun influence(key: String, radius: Float): Float {
        val point = pointer ?: return 0f
        val rect = bounds[key] ?: return 0f
        val nearest = bounds.entries.sortedBy { (_, b) -> (b.center - point).getDistanceSquared() }.take(2)
        if (nearest.none { it.key == key }) return 0f
        val dx = (point.x - rect.center.x) / (rect.width / 2f + radius)
        val dy = (point.y - rect.center.y) / (rect.height / 2f + radius)
        return (1f - hypot(dx, dy)).coerceIn(0f, 1f) * if (fastScroll) .35f else 1f
    }
}

val LocalShelfTouch = staticCompositionLocalOf<ShelfTouch?> { null }

/** Observe initial-pass pointer positions; never consume native scroll, taps or long presses. */
fun Modifier.trackShelfTouch(state: ShelfTouch): Modifier = onGloballyPositioned { state.origin = it.positionInRoot() }
    .pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.pointer = state.origin + down.position
            var previous = down
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val elapsed = (change.uptimeMillis - previous.uptimeMillis).coerceAtLeast(1)
                    state.fastScroll = kotlin.math.abs(change.position.y - previous.position.y) / elapsed > 2f * density
                    state.pointer = if (change.pressed) state.origin + change.position else null
                    previous = change
                } while (event.changes.any { it.pressed })
            } finally { state.pointer = null; state.fastScroll = false }
        }
    }

@Composable
fun Modifier.floatingJacket(key: String): Modifier {
    val touch = LocalShelfTouch.current ?: return this
    val density = LocalDensity.current.density
    val reduced = Motion.reducedMotion()
    val target by remember(touch, key, reduced) { derivedStateOf { if (reduced) 0f else touch.influence(key, 64f * density) } }
    val amount by animateFloatAsState(target, spring(dampingRatio = .92f, stiffness = 650f), label = "jacket-touch")
    DisposableEffect(touch, key) { onDispose { touch.bounds.remove(key) } }
    return onGloballyPositioned { touch.bounds[key] = it.boundsInRoot() }.graphicsLayer {
        val rect = touch.bounds[key]
        val point = touch.pointer
        val tilt = if (point != null && rect != null) ((point.x - rect.center.x) / (rect.width / 2)).coerceIn(-1f, 1f) else 0f
        translationY = -5f * density * amount
        scaleX = 1f + .022f * amount; scaleY = scaleX
        rotationZ = tilt * 1.2f * amount
        shadowElevation = 6f * density * amount
    }
}
