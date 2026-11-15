package com.will.lightreader.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.will.lightreader.ui.components.watercolorSurface
import com.will.lightreader.ui.components.watercolorWash
import com.will.lightreader.ui.components.watercolorPaper
import com.will.lightreader.ui.components.BookmarkNotch
import com.will.lightreader.ui.components.bookCoverTransition
import com.will.lightreader.source.local.LocalIds
import com.will.lightreader.LightReaderApp
import com.will.lightreader.data.shelfCoverRequest
import androidx.compose.material3.Icon
import com.will.lightreader.ui.theme.AppIcons
import com.will.lightreader.ui.theme.LocalAppDark

/** Real cover artwork stays intact. Missing covers use a quiet typographic book jacket. */
@Composable
fun Cover(url: String?, title: String, modifier: Modifier = Modifier.size(56.dp, 80.dp), radius: Dp = 7.dp, sharedKey: String? = null,
    jacketKey: String? = null, fallbackTitleAlpha: Float = 1f) {
    var failed by remember(url) { mutableStateOf(false) }
    val context = LocalContext.current
    // The jacket itself already moves in the shared layer; a second image fade causes a flash.
    val request = remember(context, url) { shelfCoverRequest(context, url) }
    val imageLoader = remember(context) {
        (context.applicationContext as? LightReaderApp)?.container?.shelfCache?.imageLoader
            ?: coil.Coil.imageLoader(context)
    }
    val dark = LocalAppDark.current
    val paper = jacketKey?.let { jacketColor(it, dark) } ?: MaterialTheme.colorScheme.secondaryContainer
    val ink = if (jacketKey == null) MaterialTheme.colorScheme.onSecondaryContainer
        else if (dark) Color(0xFFF5EFE2) else Color(0xFF343B42)
    val material = if (jacketKey != null) LocalBookMaterials.current else null
    val print = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif)
    Box(modifier.bookCoverTransition(sharedKey).clip(RoundedCornerShape(radius)).background(paper)
        .bookMaterial(material, BookMaterial.PAPER, .32f),
        contentAlignment = Alignment.Center) {
        if (jacketKey != null) {
            Box(Modifier.align(Alignment.BottomEnd).fillMaxWidth(.8f).fillMaxHeight(.36f)
                .watercolorWash(ink, .3f))
            // Typeset once on the physical jacket, then scale the same print at every cover size.
            val measurer = rememberTextMeasurer()
            val titleAlpha by rememberUpdatedState(fallbackTitleAlpha)
            Box(Modifier.matchParentSize().drawWithCache {
                val width = JacketWidth.dp.toPx()
                val height = JacketHeight.dp.toPx()
                val factor = size.width / width
                val layout = measurer.measure(AnnotatedString(title), print, overflow = TextOverflow.Ellipsis, maxLines = 4,
                    constraints = Constraints(maxWidth = (JacketWidth - 20f).dp.roundToPx()))
                val origin = Offset((width - layout.size.width) / 2, (height - layout.size.height) / 2)
                onDrawBehind {
                    if (titleAlpha > 0f) scale(factor, factor, pivot = Offset.Zero) {
                        drawText(layout, color = ink.copy(alpha = titleAlpha), topLeft = origin)
                    }
                }
            })
        } else {
            Text(title, Modifier.padding(10.dp), maxLines = 4, overflow = TextOverflow.Ellipsis, style = print, color = ink)
        }
        if (!url.isNullOrBlank() && !failed) {
            AsyncImage(model = request, contentDescription = null, imageLoader = imageLoader, modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop, onError = { failed = true })
        }
        // The slim spine is inside the jacket, independent of image content and screen theme.
        val binding = if (jacketKey == null) Modifier.width(3.dp) else Modifier.fillMaxWidth(3f / JacketWidth)
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().then(binding).background(Color.Black.copy(alpha = .08f)))
    }
}

@Composable
fun ProgressRing(percent: Int) {
    Text("${percent.coerceIn(0, 100)}%", color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall)
}

@Composable
fun BookRow(book: ShelfBook) {
    Row(Modifier.fillMaxWidth()
        .watercolorPaper(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.primary).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(book.coverUrl, book.title, Modifier.width(76.dp).aspectRatio(JacketAspectRatio),
            radius = 1.dp, sharedKey = "cover:${book.sourceId}:${book.bookUrl}", jacketKey = book.key)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (book.author.isNotBlank()) Text(book.author, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(7.dp))
            Text(when {
                book.sourceId == LocalIds.SOURCE -> "本地 · ${book.totalChapters} ${if (book.pageBased) "页" else "章"}"
                book.downloadedCount > 0 -> "已下载 ${book.downloadedCount}/${book.totalChapters} 章"
                book.totalChapters > 0 -> "共 ${book.totalChapters} 章"
                else -> "尚未开始"
            }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (book.lastReadAt > 0) {
                Spacer(Modifier.height(7.dp))
                Text("已读 ${book.progressPercent}%", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(AppIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp).rotate(-90f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The jacket provides the visual identity; metadata is typeset directly on the page. */
@Composable
fun BookTile(book: ShelfBook) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().floatingJacket(book.key)) {
            Cover(book.coverUrl, book.title, Modifier.fillMaxWidth().aspectRatio(JacketAspectRatio),
                radius = 1.dp, sharedKey = "cover:${book.sourceId}:${book.bookUrl}", jacketKey = book.key)
            if (book.lastReadAt > 0) BookmarkNotch(Modifier.align(Alignment.TopEnd).padding(end = 9.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(book.title, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(book.author.ifBlank { if (book.sourceId == LocalIds.SOURCE) "本地书籍" else "未知作者" },
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp))) {
            val progress = book.progressPercent.coerceIn(0, 100) / 100f
            if (progress > 0f) Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
        }
    }
}
