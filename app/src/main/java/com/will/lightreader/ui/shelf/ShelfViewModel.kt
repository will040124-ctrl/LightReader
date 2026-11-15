package com.will.lightreader.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.will.lightreader.data.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 书架分类维度。 */
enum class ShelfCategory { TIME, AUTHOR, DOWNLOADED }

data class ShelfBook(
    val key: String,
    val sourceId: String,
    val bookUrl: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val lastReadAt: Long,
    val progressPercent: Int,      // 0-100
    val progressText: String,      // "已读 12%" / "未开始"
    val downloadedText: String,    // "已下载 30/68" / ""
    val downloadedCount: Int = 0,  // 已离线章数（书架筛"已下载"用）
    val totalChapters: Int = 0,    // 全书章数（卡片展示"共 N 章"）
    val continueUrl: String?,      // 上次读到的章节（null = 未读过）
    val continueTitle: String,
    val pageBased: Boolean = false,
)

/** 按作者分组：一个作者的多本书叠成一摞。 */
data class AuthorGroup(
    val author: String,
    val books: List<ShelfBook>,
) {
    val count: Int get() = books.size
    /** 组内最近阅读的一本（折叠态当封面）。 */
    val representative: ShelfBook get() = books.maxByOrNull { it.lastReadAt } ?: books.first()
    val latestReadAt: Long get() = books.maxOf { it.lastReadAt }
}

/** 把书按作者归组，组按最近阅读时间倒序；无作者的合并为「未知作者」。 */
fun groupByAuthor(books: List<ShelfBook>): List<AuthorGroup> =
    books.groupBy { it.author.ifBlank { "未知作者" } }
        .map { (author, list) -> AuthorGroup(author, list.sortedByDescending { it.lastReadAt }) }
        .sortedByDescending { it.latestReadAt }

/** 书架：收藏 + 进度 + 下载状态 + 继续阅读。 */
class ShelfViewModel(
    private val repo: LibraryRepository,
    private val importer: com.will.lightreader.source.local.LocalImporter,
) : ViewModel() {
    private val _books = MutableStateFlow<List<ShelfBook>>(emptyList())
    val books: StateFlow<List<ShelfBook>> = _books

    /** 本地导入进度（null = 空闲）。 */
    data class ImportState(
        val running: Boolean = false,
        val index: Int = 0,
        val total: Int = 0,
        val step: String = "",
    )

    private val _import = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _import

    /** 导入结果提示（Toast 用，消费后清空）。 */
    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage

    fun consumeImportMessage() {
        _importMessage.value = null
    }

    fun importBooks(uris: List<android.net.Uri>) {
        if (uris.isEmpty() || _import.value.running) return
        viewModelScope.launch {
            _import.value = ImportState(running = true, total = uris.size, step = "准备导入")
            val imported = mutableListOf<String>()
            var duplicates = 0
            val failures = mutableListOf<String>()
            uris.forEachIndexed { i, uri ->
                _import.value = ImportState(true, i, uris.size, "读取第 ${i + 1}/${uris.size} 个文件")
                val result = runCatching {
                    importer.import(uri) { step ->
                        _import.value = ImportState(true, i, uris.size, step)
                    }
                }
                result.fold(
                    onSuccess = { r ->
                        if (r.duplicate) duplicates++ else imported += "《${r.title}》"
                    },
                    onFailure = { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        failures += e.message ?: "无法解析这个文件"
                    },
                )
            }
            _import.value = ImportState()
            _importMessage.value = importSummary(imported, duplicates, failures)
            refresh()
        }
    }

    /** 单本给细节（章数 / 失败原因），多本给一句总账，避免 Toast 被截断。 */
    private fun importSummary(imported: List<String>, duplicates: Int, failures: List<String>): String {
        val total = imported.size + duplicates + failures.size
        if (total == 1) {
            return when {
                imported.isNotEmpty() -> "已导入 ${imported.single()}"
                duplicates > 0 -> "这本书已经在书架里了"
                else -> "导入失败：${failures.single()}"
            }
        }
        return buildString {
            append("已导入 ${imported.size} 本")
            if (duplicates > 0) append(" · ${duplicates} 本已在书架")
            if (failures.isNotEmpty()) append(" · ${failures.size} 个失败")
        }
    }

    /** 分类维度：按时间 / 按作者 / 已下载 */
    private val _category = MutableStateFlow(ShelfCategory.TIME)
    val category: StateFlow<ShelfCategory> = _category

    fun setCategory(c: ShelfCategory) {
        _category.value = c
    }

    /** 展开中的作者分组（按作者视图：多本书的作者默认叠起，加入此集合表示已展开）。 */
    private val _expandedAuthors = MutableStateFlow<Set<String>>(emptySet())
    val expandedAuthors: StateFlow<Set<String>> = _expandedAuthors

    fun toggleAuthor(author: String) {
        _expandedAuthors.value = _expandedAuthors.value.let {
            if (author in it) it - author else it + author
        }
    }

    /** 下拉刷新中 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var refreshJob: Job? = null
    private var snapshotRevision = 0L
    private var hasResumed = false
    private val _initialShelfLoaded = MutableStateFlow(false)
    val initialShelfLoaded: StateFlow<Boolean> = _initialShelfLoaded

    fun leavePage() { _refreshing.value = false }

    fun onResume() {
        // The Room observer already loads the initial snapshot.
        if (hasResumed) refresh()
        hasResumed = true
    }

    init {
        viewModelScope.launch {
            repo.observeBooks().collectLatest { list ->
                val revision = ++snapshotRevision
                try {
                    val mapped = mapBooks(list)
                    if (revision == snapshotRevision) {
                        _books.value = mapped
                        _initialShelfLoaded.value = true
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    _initialShelfLoaded.value = true
                    _importMessage.value = "同步失败，已保留当前书架"
                }
            }
        }
    }

    /** Lifecycle sync is silent. Only a user pull owns the refresh indicator. */
    fun refresh(userInitiated: Boolean = false) {
        if (userInitiated) _refreshing.value = true
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val revision = ++snapshotRevision
            try {
                val mapped = mapBooks(repo.observeBooks().first())
                if (revision == snapshotRevision) {
                    _books.value = mapped
                    _initialShelfLoaded.value = true
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _importMessage.value = "刷新失败，已保留当前书架" }
            finally { _refreshing.value = false }
        }
    }

    /** 左滑删除：彻底移除这本书（含进度/章节/下载缓存）。 */
    fun removeBook(key: String) {
        viewModelScope.launch {
            repo.removeBook(key)
            // 立即从本地列表移除，不等 Flow 慢推（顺滑收起）
            _books.value = _books.value.filter { it.key != key }
        }
    }

    /** Removing online caches must keep the shelf entry, chapters and reading position. */
    fun clearDownloads(key: String) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.clearBookDownloads(key) }
            refresh()
        }
    }

    private suspend fun mapBooks(list: List<com.will.lightreader.data.BookEntity>): List<ShelfBook> {
        // 批量快照：一次取全部章节/位置/下载数，避免 N+1 查询（书多时刷新快几十倍）
        val keys = list.map { it.key }
        val snap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repo.shelfSnapshot(keys)
        }
        return list.map { b ->
            val chapters = snap.chaptersByBook[b.key].orEmpty()
            val pos = snap.positionByBook[b.key]
            val downloaded = snap.downloadedByBook[b.key] ?: 0
            val ci = if (pos != null) chapters.indexOfFirst { it.key == pos.chapterUrl } else -1
            val percent = if (ci >= 0 && chapters.isNotEmpty()) ((ci + 1) * 100 / chapters.size) else 0
            ShelfBook(
                key = b.key, sourceId = b.sourceId, bookUrl = b.bookUrl,
                title = b.title, author = b.author, coverUrl = b.coverUrl,
                lastReadAt = maxOf(b.lastReadAt, pos?.updatedAt ?: 0L),
                progressPercent = percent,
                progressText = if (ci >= 0) "已读 $percent%" else "未开始",
                downloadedText = if (downloaded > 0) "已下载 $downloaded/${chapters.size}" else "",
                downloadedCount = downloaded,
                totalChapters = chapters.size,
                continueUrl = pos?.chapterUrl,
                continueTitle = pos?.chapterTitle ?: "",
                pageBased = b.sourceId == com.will.lightreader.source.local.LocalIds.SOURCE &&
                    chapters.firstOrNull()?.title?.matches(Regex("第 \\d+ 页")) == true,
            )
        }
    }

    companion object {
        fun factory(
            repo: LibraryRepository,
            importer: com.will.lightreader.source.local.LocalImporter,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ShelfViewModel(repo, importer) }
        }
    }
}
