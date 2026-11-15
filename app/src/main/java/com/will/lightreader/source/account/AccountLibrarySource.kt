package com.will.lightreader.source.account

import com.will.lightreader.domain.BookBrief

data class AccountLibraryEntry(val book: BookBrief, val readChapterUrl: String? = null, val readChapterTitle: String = "")
data class AccountLibrarySnapshot(val sourceId: String, val entries: List<AccountLibraryEntry>, val pages: Int)

/** Account lists are read separately from public discovery. Never infer reading from latest updates. */
interface AccountLibrarySource {
    val sourceId: String
    val displayName: String
    val loginUrl: String
    suspend fun readLibrary(onProgress: (pages: Int, books: Int) -> Unit = { _, _ -> }): AccountLibrarySnapshot
}

/** Only fill missing positions, independently of timestamps the site does not provide. */
fun canImportAccountPosition(entry: AccountLibraryEntry, hasLocalPosition: Boolean, enabled: Boolean): Boolean =
    enabled && !hasLocalPosition && !entry.readChapterUrl.isNullOrBlank()
