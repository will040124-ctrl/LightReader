package com.will.lightreader.ui.reader

/**
 * 分页纯逻辑：把块按高度顺序装入"页"（贪心装箱），返回每页的块下标。
 *
 * 规则：
 * - 顺序不变（阅读流连续：左页读完接右页）
 * - 一页装不下时开新页
 * - 单块比一页还高时独占一页（宁可裁切也不乱序）
 */
fun paginate(heights: List<Int>, pageHeight: Int): List<List<Int>> {
    if (heights.isEmpty()) return listOf(emptyList())
    val pages = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    var used = 0
    for (i in heights.indices) {
        val h = heights[i]
        if (current.isNotEmpty() && used + h > pageHeight) {
            pages.add(current)
            current = mutableListOf()
            used = 0
        }
        current.add(i)
        used += h
    }
    if (current.isNotEmpty()) pages.add(current)
    return pages
}

/**
 * 找到包含某块的页号（越界返回最近页）。
 *
 * 二分查找：pages 的块下标是递增有序的，用 first 二分定位 O(log n)，
 * 避免目录级长章节用 [List.indexOfFirst] 线性扫描（pageOf 被每次滚动/保存位置调用，
 * 块多时是 CPU 热点）。
 */
fun pageOf(pages: List<List<Int>>, blockIndex: Int): Int {
    if (pages.isEmpty()) return 0
    var lo = 0
    var hi = pages.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        if (pages[mid].first() <= blockIndex) lo = mid else hi = mid - 1
    }
    // lo 是最后一个 first() <= blockIndex 的页；确认 blockIndex 确实落在其中
    // 越界（不在任何页）回落到第 0 页，与原线性实现语义一致
    return if (pages[lo].contains(blockIndex)) lo else 0
}
