package com.will.lightreader.ui.discover

private val carouselHint=Regex("""\s*[→›»·|｜]?\s*(?:右滑|左滑|滑动)(?:看|查看)?更多\s*[→›»·|｜]?\s*$""")
/** Source headings occasionally include a duplicated carousel instruction. Keep the real title. */
internal fun comicSectionHeading(raw:String):String = raw.replace(carouselHint,"").trim().ifBlank { "为你推荐" }
