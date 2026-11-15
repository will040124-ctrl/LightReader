@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.will.lightreader.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.will.lightreader.ui.components.WatercolorChoice
import com.will.lightreader.ui.components.watercolorWash

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentOptions(background: Color, ink: Color, onClose: () -> Unit,
    immersive: Boolean, hideStatusBar: Boolean,
    comicMode: String? = null, onComicMode: ((String) -> Unit)? = null,
    onFitPage: (() -> Unit)? = null, onFitWidth: (() -> Unit)? = null,
    downloadLabel: String? = null, downloadHint: String? = null, onDownload: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null) {
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = lerp(background, ink, .06f), contentColor = ink) {
        com.will.lightreader.ui.ReaderSystemBars(immersive, hideStatusBar)
        com.will.lightreader.ui.SystemBarsAppearance(background.luminance() < .45f)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (comicMode == null) "页面适配" else "漫画阅读", Modifier.watercolorWash(ink),
                style = MaterialTheme.typography.titleLarge)
            Text(if (comicMode == "scroll") "上下滑动阅读，点按呼出菜单，双击放大图片。"
                else "两侧点按或左右滑动翻页，中间点按呼出菜单，双击缩放。",
                style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = .75f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (comicMode != null) {
                    WatercolorChoice("翻页阅读", active = comicMode == "paged", paper = background, ink = ink, contentColor = ink) { onComicMode?.invoke("paged") }
                    WatercolorChoice("连续下滑", active = comicMode == "scroll", paper = background, ink = ink, contentColor = ink) { onComicMode?.invoke("scroll") }
                }
                if (onFitPage != null) WatercolorChoice("适合页面", paper = background, ink = ink, contentColor = ink) { onFitPage(); onClose() }
                if (onFitWidth != null) WatercolorChoice("适合宽度", paper = background, ink = ink, contentColor = ink) { onFitWidth(); onClose() }
            }
            if (downloadLabel != null) {
                FilledTonalButton(onClick = { onDownload?.invoke() }, enabled = onDownload != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = ink.copy(alpha = .12f), contentColor = ink)) { Text(downloadLabel) }
                if (downloadHint != null) Text(downloadHint, style = MaterialTheme.typography.bodySmall)
            }
            if (onOpenSettings != null) TextButton(onClick = onOpenSettings) { Text("更多偏好", color = ink) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
