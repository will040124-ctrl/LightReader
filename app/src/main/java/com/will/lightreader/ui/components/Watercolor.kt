package com.will.lightreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/** Quiet irregular paper wash. No opaque rectangular fill or animated noise. */
fun Modifier.watercolorPaper(paper: Color, ink: Color, strength: Float = .55f): Modifier =
    watercolorPaper({ paper }, ink, strength)

/** Animated pigment reads its color during drawing; paper geometry stays cached. */
fun Modifier.watercolorPaper(paper: () -> Color, ink: Color, strength: Float = .55f): Modifier = drawWithCache {
    val w = size.width; val h = size.height
    val edge = minOf(w*.035f,h*.08f,10.dp.toPx())
    val body = Path().apply {
        moveTo(edge,h*.08f)
        cubicTo(w*.16f,-edge*.15f,w*.34f,edge*.8f,w*.52f,edge*.35f)
        cubicTo(w*.72f,-edge*.1f,w*.94f,edge*.2f,w-edge,h*.06f)
        cubicTo(w+edge*.1f,h*.3f,w-edge*.7f,h*.7f,w-edge*.5f,h-edge)
        cubicTo(w*.78f,h+edge*.1f,w*.61f,h-edge*.9f,w*.44f,h-edge*.25f)
        cubicTo(w*.23f,h+edge*.2f,w*.06f,h-edge*.3f,edge*.5f,h-edge)
        cubicTo(-edge*.1f,h*.72f,edge*.8f,h*.31f,edge,h*.08f);close()
    }
    var lastPaper: Color? = null
    var base: Brush? = null
    val bloom = Brush.radialGradient(listOf(ink.copy(alpha=.028f*strength),Color.Transparent),
        center=Offset(w*.23f,h*.18f),radius=maxOf(w*.7f,h*.8f,1f))
    onDrawBehind {
        val color = paper()
        if (color != lastPaper) {
            lastPaper = color
            base = Brush.horizontalGradient(listOf(color.copy(alpha=0f),color.copy(alpha=strength*.8f),
                color.copy(alpha=strength),color.copy(alpha=strength*.7f),color.copy(alpha=0f)))
        }
        drawPath(body,checkNotNull(base));drawPath(body,bloom)
    }
}

@Composable
fun WatercolorPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.watercolorPaper(MaterialTheme.colorScheme.surfaceContainerLow,MaterialTheme.colorScheme.primary), content=content)
}

/** Stationary pigment at the edge of a paper surface; cached, with no animation loop. */
fun Modifier.watercolorSurface(paper: Color, ink: Color, strength: Float = .055f): Modifier = drawWithCache {
    val edge = minOf(size.height, 72.dp.toPx())
    val wash = Path().apply {
        moveTo(0f, 0f); lineTo(size.width, 0f)
        cubicTo(size.width * .94f, edge * .11f, size.width * .81f, edge * .25f, size.width * .60f, edge * .13f)
        cubicTo(size.width * .32f, edge * .03f, size.width * .17f, edge * .25f, 0f, edge * .16f)
        close()
    }
    val pigment = Brush.horizontalGradient(listOf(ink.copy(alpha = strength * .20f),
        ink.copy(alpha = strength), ink.copy(alpha = strength * .35f)))
    onDrawBehind { drawRect(paper); drawPath(wash, pigment) }
}

/** Fixed pigment shapes are cached at layout size; no per-frame noise or animated texture. */
fun Modifier.watercolorWash(ink: Color, strength: Float = 1f): Modifier = watercolorWash(ink) { strength }

/** Pressure changes pigment opacity at draw time without rebuilding paths or remeasuring text. */
fun Modifier.watercolorWash(ink: Color, strength: () -> Float): Modifier = drawWithCache {
    val w = size.width; val h = size.height
    val stroke = Path().apply {
        moveTo(w * .03f, h * .34f)
        cubicTo(w * .16f, h * .05f, w * .35f, h * .27f, w * .55f, h * .15f)
        cubicTo(w * .72f, h * .04f, w * .86f, h * .21f, w * .97f, h * .25f)
        lineTo(w * .94f, h * .72f)
        cubicTo(w * .76f, h * .98f, w * .63f, h * .72f, w * .45f, h * .86f)
        cubicTo(w * .27f, h * .98f, w * .13f, h * .72f, w * .02f, h * .79f)
        close()
    }
    val pigment = Brush.horizontalGradient(listOf(ink.copy(alpha = .24f),
        ink.copy(alpha = .76f), Color(0xFFBE968A).copy(alpha = .40f)))
    onDrawBehind {
        val opacity = strength().coerceIn(0f, 4f)
        drawPath(stroke, pigment, alpha = opacity / 4f)
        drawOval(ink.copy(alpha = .035f * opacity), Offset(w * .12f, h * .35f), Size(w * .72f, h * .42f))
    }
}

@Composable
fun WatercolorRule(modifier: Modifier = Modifier, ink: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.fillMaxWidth().height(5.dp)) {
        drawPath(Path().apply {
            moveTo(0f, size.height * .45f)
            quadraticTo(size.width * .3f, 0f, size.width * .55f, size.height * .4f)
            quadraticTo(size.width * .8f, size.height, size.width, size.height * .4f)
            lineTo(size.width, size.height * .62f)
            quadraticTo(size.width * .6f, size.height * .9f, 0f, size.height * .7f)
            close()
        }, Brush.horizontalGradient(listOf(ink.copy(alpha = 0f), ink.copy(alpha = .22f), ink.copy(alpha = 0f))))
    }
}
