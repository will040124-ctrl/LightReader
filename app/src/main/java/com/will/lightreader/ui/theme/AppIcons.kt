package com.will.lightreader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 自绘图标集（UI 标准 01）：24dp 网格 / 1.75 描边 / 圆头圆角 / 内层 tonal 填充。
 * 统一用黑色绘制，交给 Icon(tint=…) 上色——填充层用 alpha 区分，所以单色 tint 也能出双层效果。
 */
object AppIcons {
    // A quiet cat-ear signature, used only as a small library/reading companion accent.
    val Cat: ImageVector = icon(
        "cat",
        stroke = "M4 10 3 3l6 3a13 13 0 0 1 6 0l6-3-1 7c3 8-2 11-8 11S1 18 4 10Z" +
            " M6 13l3 1 M18 13l-3 1 M10.5 17l1.5 1 1.5-1",
    )
    val Sources: ImageVector = icon("sources", stroke = "M8 4h11v14H8Z M8 7H4v14h11v-3 M11 8h5 M11 12h5")
    val Grid: ImageVector = icon("grid", stroke = "M4 4h6v6H4Z M14 4h6v6h-6Z M4 14h6v6H4Z M14 14h6v6h-6Z")
    val List: ImageVector = icon("list", stroke = "M9 6h11 M9 12h11 M9 18h11 M4 6h.1 M4 12h.1 M4 18h.1")
    val Book: ImageVector = icon(
        "book",
        fill = "M5.4 5.9A2.4 2.4 0 0 1 7.8 3.5h9.6a1.4 1.4 0 0 1 1.4 1.4v14.2a1.4 1.4 0 0 1-1.4 1.4H7.8a2.4 2.4 0 0 1-2.4-2.4Z",
        stroke = "M5.4 5.9A2.4 2.4 0 0 1 7.8 3.5h9.6a1.4 1.4 0 0 1 1.4 1.4v14.2a1.4 1.4 0 0 1-1.4 1.4H7.8a2.4 2.4 0 0 1-2.4-2.4Z" +
            " M5.4 17.3A2.4 2.4 0 0 1 7.8 15h11" +
            " M13.6 3.5v6l2.1-1.5 2.1 1.5v-6",
    )

    // An arriving page and a bound book: import is an action, not an unlabeled plus.
    val ImportBook: ImageVector = icon("import-book",
        fill = "M4 8h7v12H6a2 2 0 0 1-2-2Z",
        stroke = "M8 5H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7" +
            " M4 16.5h11 M14 2.5v9 M10.5 8l3.5 3.5L17.5 8")

    val Compass: ImageVector = icon(
        "compass",
        fill = circle(12f, 12f, 8.6f),
        solid = "m15.4 8.6-2 4.8-4.8 2 2-4.8z",
        solidAlpha = 1f,
        stroke = circle(12f, 12f, 8.6f),
    )

    val Tune: ImageVector = icon(
        "tune",
        stroke = "M3.8 6.2h16.4 M3.8 12h16.4 M3.8 17.8h16.4",
        solid = circle(8.55f, 6.1f, 1.7f) + circle(15.35f, 11.9f, 1.7f) + circle(10.95f, 17.7f, 1.7f),
        solidAlpha = 1f,
    )

    val Chevron: ImageVector = icon("chevron", stroke = "m6.4 9.4 5.6 5.6 5.6-5.6")

    val Back: ImageVector = icon("back", stroke = "M20 12H5.4 M11 5.5 4.5 12l6.5 6.5")

    val ChevronsLeft: ImageVector = icon(
        "chevrons-left",
        stroke = "M11.5 6 6 12l5.5 6 M18.5 6 13 12l5.5 6",
    )

    val ChevronsRight: ImageVector = icon(
        "chevrons-right",
        stroke = "M12.5 6 18 12l-5.5 6 M5.5 6 11 12l-5.5 6",
    )

    val Close: ImageVector = icon("close", stroke = "M6.2 6.2l11.6 11.6 M17.8 6.2 6.2 17.8")

    val Download: ImageVector = icon(
        "download",
        stroke = "M12 3.6v10 M8.2 9.8l3.8 3.8 3.8-3.8" +
            " M4.8 15.2v3.2a1.6 1.6 0 0 0 1.6 1.6h11.2a1.6 1.6 0 0 0 1.6-1.6v-3.2",
    )

    val Search: ImageVector = icon(
        "search",
        fill = circle(10.8f, 10.8f, 6f),
        stroke = circle(10.8f, 10.8f, 6.2f) + " M15.4 15.4l4.4 4.4",
    )

    val History: ImageVector = icon(
        "history",
        stroke = circle(12f, 12f, 8.6f) + " M12 7v5.2l3.4 2",
    )

    val Person: ImageVector = icon(
        "person",
        fill = circle(12f, 8.6f, 3.4f),
        stroke = circle(12f, 8.6f, 3.4f) + " M5 20.2c.6-3.7 3.5-5.9 7-5.9s6.4 2.2 7 5.9",
    )

    val Plus: ImageVector = icon("plus", stroke = "M12 5.2v13.6 M5.2 12h13.6")

    val More: ImageVector = icon(
        "more",
        solid = circle(6f, 12f, 1.5f) + circle(12f, 12f, 1.5f) + circle(18f, 12f, 1.5f),
        solidAlpha = 1f,
    )

    val Check: ImageVector = icon("check", stroke = "m5.4 12.6 4.4 4.4 8.8-9.2")

    val Bookmark: ImageVector = icon(
        "bookmark",
        fill = "M6.4 5A1.8 1.8 0 0 1 8.2 3.2h7.6A1.8 1.8 0 0 1 17.6 5v15.6l-5.6-4-5.6 4z",
        stroke = "M6.4 5A1.8 1.8 0 0 1 8.2 3.2h7.6A1.8 1.8 0 0 1 17.6 5v15.6l-5.6-4-5.6 4z",
    )

    val Sparkle: ImageVector = icon(
        "sparkle",
        stroke = "M12 3l2.2 6.8L21 12l-6.8 2.2L12 21l-2.2-6.8L3 12l6.8-2.2z",
    )
}

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${r * 2} 0a$r $r 0 1 0 ${-r * 2} 0Z"

private fun icon(
    name: String,
    stroke: String? = null,
    fill: String? = null,
    solid: String? = null,
    solidAlpha: Float = 0.22f,
): ImageVector {
    val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    fill?.let {
        b.addPath(
            PathParser().parsePathString(it).toNodes(),
            fill = SolidColor(Color.Black),
            fillAlpha = 0.20f,
        )
    }
    solid?.let {
        b.addPath(
            PathParser().parsePathString(it).toNodes(),
            fill = SolidColor(Color.Black),
            fillAlpha = solidAlpha,
        )
    }
    stroke?.let {
        b.addPath(
            PathParser().parsePathString(it).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}
