package com.will.lightreader.ui.shelf

enum class ShelfArtwork(val label: String) {
    MASCOT("看板娘"), LAST_COVER("最近阅读封面"), HIDDEN("隐藏续读卡片");
    companion object {
        fun stored(value: String?): ShelfArtwork = entries.firstOrNull { it.name == value } ?: MASCOT
    }
}

/** Only books with a real saved position are candidates; adding a book is not reading it. */
fun latestReadingBook(books: List<ShelfBook>): ShelfBook? =
    books.filter { !it.continueUrl.isNullOrBlank() }.maxByOrNull { it.lastReadAt }

fun shelfArtworkModel(mode: ShelfArtwork, cover: String?): String? =
    cover?.takeIf { mode == ShelfArtwork.LAST_COVER && it.isNotBlank() }
