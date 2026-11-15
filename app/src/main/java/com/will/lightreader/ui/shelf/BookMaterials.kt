package com.will.lightreader.ui.shelf

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.will.lightreader.LightReaderApp
import kotlinx.coroutines.ExperimentalCoroutinesApi

internal val LocalBookMaterials = staticCompositionLocalOf<ImageBitmap?> { null }
private val DayJackets = listOf(Color(0xFF79849A), Color(0xFFBBC2AE), Color(0xFFC6AD99), Color(0xFFB8AFC0))
private val NightJackets = listOf(Color(0xFF445169), Color(0xFF546055), Color(0xFF65594F), Color(0xFF5B5368))

internal fun jacketColor(key: String, dark: Boolean): Color {
    val colors = if (dark) NightJackets else DayJackets
    return colors[key.hashCode().ushr(1) % colors.size]
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun rememberShelfTextures(): SolidShelfTextures? {
    val app = LocalContext.current.applicationContext as LightReaderApp
    val task = app.solidShelfTextures
    val initial = if (task.isCompleted && !task.isCancelled) runCatching { task.getCompleted() }.getOrNull() else null
    val result by produceState(initial, task) {
        try { value = task.await() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { value = null }
    }
    return result
}
internal enum class BookMaterial(val x: Int, val y: Int) {
    PAPER(0, 0), FORE_EDGE(1, 0), TIMBER(0, 1), CLOTH(1, 1)
}

/** Shared watercolor material atlas; decoded once and reused across book faces. */
internal fun Modifier.bookMaterial(atlas: ImageBitmap?, material: BookMaterial, alpha: Float = .3f): Modifier =
    if (atlas == null) this else drawWithCache {
        val width = atlas.width / 2
        val height = atlas.height / 2
        val offset = IntOffset(material.x * width + 2, material.y * height + 2)
        val source = IntSize(width - 4, height - 4)
        val target = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
        onDrawBehind {
            drawImage(atlas, srcOffset = offset, srcSize = source, dstSize = target,
                alpha = alpha, filterQuality = FilterQuality.Medium, blendMode = BlendMode.Multiply)
        }
    }
