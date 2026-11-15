package com.will.lightreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.layout.ContentScale
import com.will.lightreader.R

/** Small printed seal; the character is decorative, never an unlabeled control. */
@Composable
fun BunkoSeal(modifier: Modifier = Modifier) {
    val edge = GenericShape { s, _ ->
        moveTo(s.width * .16f, s.height * .05f)
        cubicTo(s.width * .6f, -s.height * .04f, s.width, s.height * .1f, s.width * .96f, s.height * .5f)
        cubicTo(s.width, s.height * .9f, s.width * .65f, s.height, s.width * .3f, s.height * .94f)
        cubicTo(-s.width * .05f, s.height * .94f, 0f, s.height * .25f, s.width * .16f, s.height * .05f)
        close()
    }
    Box(modifier.size(40.dp, 44.dp).watercolorWash(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp, 36.dp).clip(edge)) {
            coil.compose.AsyncImage(model = R.drawable.ic_launcher_photo, contentDescription = "\u8f7b\u9605",
                modifier = Modifier.matchParentSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; alpha = .88f },
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.55f) }))
        }
    }
}

/** A cut-paper bookmark accent, shared by navigation and the book currently being read. */
@Composable
fun BookmarkNotch(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.size(8.dp, 13.dp)) {
        drawPath(Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f)
            lineTo(size.width, size.height); lineTo(size.width / 2, size.height * .72f)
            lineTo(0f, size.height); close()
        }, color)
    }
}
