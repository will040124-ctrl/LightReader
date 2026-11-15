package com.will.lightreader.ui.shelf

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.will.lightreader.ui.theme.Motion
import kotlin.math.abs

internal data class WheelPose(val scale: Float, val tilt: Float, val alpha: Float)

internal fun wheelPose(center: Float, start: Int, end: Int): WheelPose {
    if (end <= start || !center.isFinite()) return WheelPose(1f, 0f, 1f)
    val distance = ((center - (start.toFloat() + end) / 2f) / ((end.toFloat() - start) / 2f)).coerceIn(-1f, 1f)
    val edge = abs(distance)
    if (distance == 0f) return WheelPose(1f, 0f, 1f)
    return WheelPose(1f - .06f * edge * edge, -9f * distance, 1f - .14f * edge * edge)
}

/** Read scrolling in the layer phase, keeping native fling, hit testing and list measurement. */
@Composable
fun Modifier.shelfWheel(state: LazyListState, key: String, enabled: Boolean = true): Modifier {
    if (!enabled || Motion.reducedMotion()) return this
    return graphicsLayer {
        val layout = state.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.key == key }
        val pose = item?.let { wheelPose(it.offset + it.size / 2f, layout.viewportStartOffset, layout.viewportEndOffset) }
            ?: WheelPose(1f, 0f, 1f)
        scaleX = pose.scale; scaleY = pose.scale
        rotationX = pose.tilt; alpha = pose.alpha
        cameraDistance = 72f * density
    }
}
