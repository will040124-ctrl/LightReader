package com.will.lightreader.ui.reader

import com.will.lightreader.domain.ChapterContent

/** Full content equality avoids hash collisions; every geometry/typography input participates. */
data class ReadingLayoutKey(val content: ChapterContent, val width: Int, val height: Int,
    val header: Int, val fontSize: Int, val lineHeight: Float, val serif: Boolean,
    val paragraphPadding: Int, val density: Float, val fontScale: Float, val direction: String, val twoPage: Boolean)

class BoundedReadingLayouts(private val capacity: Int = 4, private val maxFragments: Int = 30_000) {
    private val entries = LinkedHashMap<ReadingLayoutKey, ReadingFlow>(4, .75f, true)
    @Synchronized fun get(key: ReadingLayoutKey): ReadingFlow? = entries[key]
    @Synchronized fun put(key: ReadingLayoutKey, flow: ReadingFlow) {
        if (flow.fragments.size > maxFragments) return
        entries[key] = flow
        while (entries.size > capacity || entries.values.sumOf { it.fragments.size } > maxFragments) {
            entries.remove(entries.keys.first())
        }
    }
    @Synchronized fun clear() { entries.clear() }
}

val readingLayouts = BoundedReadingLayouts()
