package com.will.lightreader.ui.reader

import org.junit.Assert.*
import org.junit.Test

class ReadingFlowTest {
    @Test fun `long paragraph fills remaining space and continues on next page`() {
        val units = listOf(FlowUnit(-1, 30)) + (0..6).map { FlowUnit(0, 20, it * 10, (it+1)*10, true) }
        val flow = readingFlow(units, 100)
        assertEquals(listOf(2, 1), flow.pages.map { it.size })
        assertEquals(0, flow.pageAt(0, 20))
        assertEquals(1, flow.pageAt(0, 60))
        assertEquals(70, flow.fragments.last().to)
        assertEquals(30, flow.fragments[1].to)
    }
    @Test fun `layout changes preserve the character anchor within a paragraph`() {
        val units = (0..9).map { FlowUnit(5, 20, it*10, (it+1)*10, true) }
        assertEquals(1, readingFlow(units, 100).pageAt(5, 75))
        assertEquals(2, readingFlow(units, 60).pageAt(5, 75))
    }
    @Test fun `different source paragraphs do not merge`() {
        val flow = readingFlow(listOf(FlowUnit(0,20,0,10,true), FlowUnit(1,20,0,10,true)),100)
        assertEquals(2,flow.fragments.size)
        assertEquals(0,flow.pageAt(1))
    }
    @Test fun `empty flow and invalid dimensions remain safe`() {
        assertEquals(listOf(emptyList<Int>()), readingFlow(emptyList(),0).pages)
        assertEquals(0,readingFlow(emptyList(),100).pageAt(99))
    }

    @Test fun `repeated reflows retain the original character instead of drifting to earlier page starts`() {
        var live = 19 to 273
        for ((page, start) in listOf(18 to (19 to 200), 32 to (19 to 240), 27 to (19 to 150), 20 to (18 to 400))) {
            live = settledReaderAnchor(page, page, live, start)
        }
        assertEquals(19 to 273, live)
    }
    @Test fun `a settled page turn updates the anchor to the new page start`() {
        assertEquals(20 to 100, settledReaderAnchor(18, 19, 19 to 273, 20 to 100))
    }
    @Test fun `a cancelled turn retains the current character`() {
        assertEquals(19 to 273, settledReaderAnchor(18, 18, 19 to 273, 19 to 200))
    }
}
