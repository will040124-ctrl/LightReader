package com.will.lightreader.ui.reader

/** Line offsets refer to the original displayed paragraph, so reflow does not lose the anchor. */
data class FlowUnit(val source: Int, val height: Int, val from: Int = 0, val to: Int = 0,
    val text: Boolean = false, val top: Int = 0, val bottom: Int = 0)
data class FlowFragment(val source: Int, val height: Int, val from: Int, val to: Int,
    val text: Boolean, val top: Int, val bottom: Int)
data class ReadingFlow(val fragments: List<FlowFragment>, val pages: List<List<Int>>) {
    fun pageAt(block: Int, charOffset: Int = 0): Int {
        val exact = fragments.indexOfFirst { it.source == block && (!it.text || charOffset in it.from until it.to) }
        val index = if (exact >= 0) exact else fragments.indexOfFirst { it.source == block }
        return if (index < 0) 0 else pages.indexOfFirst { index in it }.coerceAtLeast(0)
    }
}

/** Pack measured lines, then join each page's adjacent lines back into a single Text. */
fun readingFlow(units: List<FlowUnit>, pageHeight: Int): ReadingFlow {
    val fragments = mutableListOf<FlowFragment>()
    val pages = paginate(units.map { it.height.coerceAtLeast(1) }, pageHeight.coerceAtLeast(1)).map { indices ->
        val page = mutableListOf<Int>()
        for (index in indices) {
            val unit = units[index]
            val previous = page.lastOrNull()?.let { fragments[it] }
            if (unit.text && previous?.text == true && previous.source == unit.source && previous.to == unit.from) {
                fragments[page.last()] = previous.copy(height = previous.height + unit.height,
                    to = unit.to, bottom = unit.bottom)
            } else {
                page += fragments.size
                fragments += FlowFragment(unit.source, unit.height, unit.from, unit.to, unit.text, unit.top, unit.bottom)
            }
        }
        page
    }
    return ReadingFlow(fragments, pages)
}

/** Reflow keeps the exact character; only a settled user page change selects a new page start. */
internal fun settledReaderAnchor(previousSpread: Int, spread: Int, live: Pair<Int, Int>,
    pageStart: Pair<Int, Int>): Pair<Int, Int> = if (previousSpread == spread) live else pageStart
