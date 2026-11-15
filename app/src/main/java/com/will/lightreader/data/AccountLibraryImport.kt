package com.will.lightreader.data

import android.content.Context
import androidx.room.withTransaction
import com.will.lightreader.source.account.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AccountImportPlan(val newBooks: List<AccountLibraryEntry>, val positions: List<AccountLibraryEntry>, val existing: Int)
data class AccountImportReport(val added: Int, val existing: Int, val positions: Int)

fun planAccountImport(snapshot: AccountLibrarySnapshot, selectedIds: Set<String>, existingBooks: Set<String>,
    existingPositions: Set<String>, importPositions: Boolean): AccountImportPlan {
    require(snapshot.sourceId == "wenku8" && snapshot.pages in 1..80) { "不支持的网站书架" }
    require(snapshot.entries.size <= 10_000 && snapshot.entries.all(Wenku8AccountParser::validEntry)) { "网站书架条目无效" }
    val entries = snapshot.entries.distinctBy { it.book.bookId }
    require(selectedIds.isNotEmpty() && selectedIds.all { id -> entries.any { it.book.bookId == id } }) { "请选择有效的书籍" }
    val selected = entries.filter { it.book.bookId in selectedIds }
    fun key(entry: AccountLibraryEntry) = "${entry.book.sourceId}:${entry.book.bookId}"
    val new = selected.filter { key(it) !in existingBooks }
    val positions = selected.filter { canImportAccountPosition(it, key(it) in existingPositions, importPositions) }
    return AccountImportPlan(new, positions, selected.size-new.size)
}

class AccountLibraryImporter(context: Context) {
    private val db = LightReaderDb.get(context.applicationContext)
    suspend fun merge(snapshot: AccountLibrarySnapshot, selected: Set<String>, importPositions: Boolean): AccountImportReport = withContext(Dispatchers.IO) {
        db.withTransaction {
            val plan = planAccountImport(snapshot, selected, db.bookDao().snapshot().map { it.key }.toSet(),
                db.positionDao().snapshot().map { it.bookKey }.toSet(), importPositions)
            val now = System.currentTimeMillis()
            for (entry in plan.newBooks) {
                val b = entry.book
                db.bookDao().upsert(BookEntity("${b.sourceId}:${b.bookId}",b.sourceId,b.bookId,b.bookUrl,b.title,b.author,b.coverUrl,addedAt=now))
            }
            for (entry in plan.positions) {
                val b = entry.book
                // The site does not provide a trustworthy read timestamp. Do not pretend this was read now.
                db.positionDao().upsert(PositionEntity("${b.sourceId}:${b.bookId}",requireNotNull(entry.readChapterUrl),entry.readChapterTitle,updatedAt=0))
            }
            AccountImportReport(plan.newBooks.size,plan.existing,plan.positions.size)
        }
    }
}
