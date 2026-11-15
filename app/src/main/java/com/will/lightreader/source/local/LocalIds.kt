package com.will.lightreader.source.local

/**
 * 本地导入书籍的标识约定。
 * - sourceId = "local"
 * - bookUrl = "local://<bookId>"，bookId 为源文件内容哈希（同一本书重复导入落在同一 key 上）
 * - chapterUrl = "local://<bookId>/<index>"，索引即阅读顺序
 */
object LocalIds {
    const val SOURCE = "local"

    /** filesDir 下每本书一个目录：正文图片 / 封面 / meta.json。 */
    const val IMPORT_DIR = "imports"

    fun bookUrl(bookId: String): String = "local://$bookId"

    fun bookIdOf(bookUrl: String): String =
        bookUrl.removePrefix("local://").substringBefore('/').substringBefore('#')

    fun chapterUrl(bookId: String, index: Int): String = "local://$bookId/$index"

    fun isLocal(bookKey: String): Boolean = bookKey.startsWith("$SOURCE:")
}
