package com.will.lightreader.source

data class SourceDiscovery(val ranks: List<Pair<String,String>> = WENKU8_RANKS,
    val supportsTags: Boolean = true, val supportsCompleted: Boolean = true, val browseLabel: String = "榜单",
    val supportsHome: Boolean = false)

val WENKU8_RANKS = listOf("dayvisit" to "今日热榜", "monthvisit" to "本月热点", "allvisit" to "热门",
    "goodnum" to "最受关注", "postdate" to "新书", "sugoi:2026" to "这本轻小说真厉害")
