package com.will.lightreader.ui.reader

import android.content.Context

/** 阅读偏好（字号/行距/纸色），SharedPreferences 持久化。 */

/**
 * 书架布局模式
 */
enum class ShelfLayoutMode {
    AUTO,  // 文库封面网格，列数随屏幕宽度变化
    LIST,  // 列表模式
    GRID;  // 网格模式

    fun usesGrid(widthDp: Int): Boolean = this == GRID || (this == AUTO && widthDp >= 600)
}
class ReaderPrefs(context: Context) {
    private val sp = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    var shelfArtwork: com.will.lightreader.ui.shelf.ShelfArtwork
        get() = com.will.lightreader.ui.shelf.ShelfArtwork.stored(sp.getString("shelf_artwork", null))
        set(value) { sp.edit().putString("shelf_artwork", value.name).apply() }
    var shelfWheelEnabled: Boolean
        get() = sp.getBoolean("shelf_wheel", true)
        set(value) { sp.edit().putBoolean("shelf_wheel", value).apply() }
    var shelfStartsClosed: Boolean
        get() = sp.getBoolean("shelf_starts_closed", true)
        set(value) { sp.edit().putBoolean("shelf_starts_closed", value).apply() }
    var widgetShowDetails: Boolean
        get() = sp.getBoolean("widget_details", true)
        set(value) { sp.edit().putBoolean("widget_details", value).apply() }
    fun comicPageId(chapter: String): String? = sp.getString("comic-page:$chapter", null)
    fun saveComicPageId(chapter: String, id: String) { sp.edit().putString("comic-page:$chapter", id).apply() }
    var defaultComicMode: String
        get() = if (sp.getString("comic_mode_default", "paged") == "scroll") "scroll" else "paged"
        set(value) { sp.edit().putString("comic_mode_default", if (value == "scroll") "scroll" else "paged").apply() }
    fun comicMode(book:String):String=if(sp.getString("comic-mode:$book",defaultComicMode)=="scroll")"scroll" else "paged"
    fun saveComicMode(book:String,mode:String) {
        val value=if(mode=="scroll")"scroll" else "paged"
        // Keep an explicit per-book choice and let new books inherit the last used mode.
        sp.edit().putString("comic-mode:$book",value).putString("comic_mode_default",value).apply()
    }
    fun comicScrollFraction(chapter:String,pageId:String):Float =
        if(sp.getString("comic-scroll-page:$chapter",null)==pageId)sp.getFloat("comic-scroll-fraction:$chapter",0f).takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: 0f else 0f
    fun saveComicScroll(chapter:String,pageId:String,fraction:Float) {
        sp.edit().putString("comic-scroll-page:$chapter",pageId).putFloat("comic-scroll-fraction:$chapter",if(fraction.isFinite())fraction.coerceIn(0f,1f) else 0f).apply()
    }

    var comicRtl: Boolean
        get() = sp.getBoolean("comic_rtl", false)
        set(value) { sp.edit().putBoolean("comic_rtl", value).apply() }

    /** The original storage key preserves existing immersive preferences across all formats. */
    var immersive: Boolean
        get() = sp.getBoolean("comic_immersive", true)
        set(value) { sp.edit().putBoolean("comic_immersive", value).apply() }

    var hideStatusBar: Boolean
        get() = sp.getBoolean("reader_hide_status_bar", false)
        set(value) { sp.edit().putBoolean("reader_hide_status_bar", value).apply() }

    /** Supplement existing DB paragraph anchors without changing database schema or old positions. */
    fun saveInlineAnchor(chapter: String, block: Int, character: Int) {
        sp.edit().putString("inline:$chapter", "$block:${character.coerceAtLeast(0)}").apply()
    }
    fun inlineAnchor(chapter: String, block: Int): Int {
        val saved = sp.getString("inline:$chapter", null)?.split(':') ?: return 0
        return if (saved.getOrNull(0)?.toIntOrNull() == block) saved.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0 else 0
    }

    init {
        // 排版标准 02：默认行距 1.8 → 1.7。只迁移从未调过行距的旧值。
        if (!sp.getBoolean("lineHeight_v2", false)) {
            if (sp.contains("lineHeight") && kotlin.math.abs(sp.getFloat("lineHeight", 1.7f) - 1.8f) < 0.001f) {
                sp.edit().putFloat("lineHeight", 1.7f).apply()
            }
            sp.edit().putBoolean("lineHeight_v2", true).apply()
        }
    }

    var fontSize: Int
        get() = sp.getInt("fontSize", 18)
        set(v) = sp.edit().putInt("fontSize", v.coerceIn(13, 32)).apply()

    var lineHeight: Float
        get() = sp.getFloat("lineHeight", 1.7f)
        set(v) = sp.edit().putFloat("lineHeight", v.coerceIn(1.4f, 2.4f)).apply()

    /** 正文字体：宋体（默认）/ 黑体。 */
    var serif: Boolean
        get() = sp.getBoolean("serif", true)
        set(v) = sp.edit().putBoolean("serif", v).apply()

    /** follow | light | sepia | dark */
    var paper: String
        get() = sp.getString("paper", "follow") ?: "follow"
        set(v) = sp.edit().putString("paper", v).apply()

    /** 自定义背景图（content:// URI）；空 = 用纸色。 */
    var bgUri: String
        get() = sp.getString("bgUri", "") ?: ""
        set(v) = sp.edit().putString("bgUri", v).apply()

    /** 双页排版（平板大屏：左右两页并排）。 */
    var twoPage: Boolean
        get() = sp.getBoolean("twoPage", false)
        set(v) = sp.edit().putBoolean("twoPage", v).apply()

    /** 翻页动画：slide（平移）/ cover（覆盖）。 */
    var turnStyle: String
        get() = if (sp.getString("turnStyle", "slide") == "cover") "cover" else "slide"
        set(v) = sp.edit().putString("turnStyle", v).apply()

    /** 小球停靠位置（屏幕比例 0..1；-1 = 未停靠过，用默认右下角）。 */
    var ballX: Float
        get() = sp.getFloat("ballX", -1f)
        set(v) = sp.edit().putFloat("ballX", v).apply()

    var ballY: Float
        get() = sp.getFloat("ballY", -1f)
        set(v) = sp.edit().putFloat("ballY", v).apply()

    fun cyclePaper(): String {
        val order = listOf("follow", "light", "sepia", "dark")
        val next = order[(order.indexOf(paper).coerceAtLeast(0) + 1) % order.size]
        paper = next
        return next
    }

    /**
     * 书架布局模式
     */
    var shelfLayoutMode: ShelfLayoutMode
        get() = sp.getString("shelf_layout_mode", ShelfLayoutMode.AUTO.name)
            ?.let { stored -> ShelfLayoutMode.entries.firstOrNull { it.name == stored } }
            ?: ShelfLayoutMode.AUTO
        set(value) = sp.edit().putString("shelf_layout_mode", value.name).apply()
}
