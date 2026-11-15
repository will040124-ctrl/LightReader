package com.will.lightreader.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.lightreader.R
import com.will.lightreader.ui.components.pressScale
import com.will.lightreader.ui.components.watercolorSurface
import com.will.lightreader.ui.theme.LocalAppDark

/** A legible jacket and reading position, with watercolor kept behind the quiet edge. */
@Composable
fun ReadingRoomCard(book: ShelfBook, wide: Boolean, modifier: Modifier = Modifier, compact: Boolean = false,
    artwork: ShelfArtwork = ShelfArtwork.MASCOT, dense: Boolean = false, onContinue: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val paper = MaterialTheme.colorScheme.surfaceContainerLow
    val dark = LocalAppDark.current
    Box(modifier.fillMaxWidth().heightIn(min = if (dense) 104.dp else if (compact) 148.dp else if (wide) 220.dp else 188.dp)
        .pressScale(interaction, .993f)
        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 42.dp, bottomEnd = 32.dp, bottomStart = 24.dp))
        .watercolorSurface(paper, MaterialTheme.colorScheme.primary, .06f)
        .clickable(interactionSource = interaction, indication = null, onClickLabel = "继续阅读${book.title}", onClick = onContinue)) {
        if (artwork == ShelfArtwork.MASCOT) {
            Box(Modifier.matchParentSize()) {
                coil.compose.AsyncImage(model = R.drawable.watercolor_reading_room, contentDescription = null,
                    contentScale = ContentScale.Fit, alignment = Alignment.CenterEnd,
                    alpha = if (dark) .25f else .38f,
                    modifier = Modifier.height(if (wide) 220.dp else 146.dp).width(if (wide) 330.dp else 220.dp).align(Alignment.CenterEnd)
                        .drawWithCache {
                            val fade = Brush.horizontalGradient(listOf(paper, paper.copy(alpha = 0f)), endX = size.width * .65f)
                            val edges = Brush.verticalGradient(0f to paper, .18f to paper.copy(alpha = 0f),
                                .82f to paper.copy(alpha = 0f), 1f to paper)
                            onDrawWithContent { drawContent(); drawRect(fade); drawRect(edges) }
                        })
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                    0f to paper, .33f to paper, .78f to paper.copy(alpha = .38f), 1f to paper.copy(alpha = .05f))))
            }
        }
        Row(Modifier.fillMaxWidth().padding(if (dense) 12.dp else if (compact) 16.dp else if (wide) 24.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(book.coverUrl, book.title, Modifier.width(if (dense) 54.dp else if (compact) 76.dp else if (wide) 116.dp else 88.dp)
                .aspectRatio(JacketAspectRatio), radius = 1.dp, jacketKey = book.key)
            Spacer(Modifier.width(if (wide) 26.dp else 18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 9.dp)) {
                Text("把故事接着读下去", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                Text(book.title, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = if (wide && !compact) 28.sp else 22.sp,
                        lineHeight = if (wide && !compact) 36.sp else 29.sp, fontFamily = FontFamily.Serif))
                if (!compact) Text(book.continueTitle.ifBlank { book.progressText }, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("继续阅读  ↗", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${book.progressPercent}%", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { book.progressPercent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.width(80.dp).height(3.dp),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                    drawStopIndicator = {})
            }
        }
    }
}
