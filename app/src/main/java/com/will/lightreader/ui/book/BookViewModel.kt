package com.will.lightreader.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.domain.ChapterRef
import com.will.lightreader.data.CachedBookDetail
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.SourceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BookState(
    val loading: Boolean = true,
    val detail: BookDetail? = null,
    val chapters: List<ChapterRef> = emptyList(),
    val downloadedKeys: Set<String> = emptySet(),   // 已离线的章节 key（=chapterUrl）
    val offline: Boolean = false,                   // 离线只读模式（本地目录降级）
    val error: SourceError? = null,
    val refreshMessage: String? = null,
    val refreshing: Boolean = false,   // 下拉刷新中（静默，不清已有内容）
    val hasCachedDetail: Boolean = false,
    val readPercent: Int = 0,          // 阅读进度百分比（章索引/总数）
    val readChapter: String = "",      // 上次读到的章节名
    val readChapterUrl: String = "",   // 上次读到的章节 URL（继续阅读入口）
)

class BookViewModel(
    private val registry: SourceRegistry,
    private val repo: com.will.lightreader.data.LibraryRepository,
    private val prefetchImage: suspend (String) -> Unit,
    private val appScope: kotlinx.coroutines.CoroutineScope,
    private val sourceId: String,
    private val bookUrl: String,
) : ViewModel() {
    internal val openingShelfCover = BookOpening.shelfCover(sourceId, bookUrl)
    private val _state = MutableStateFlow(BookState(detail = BookOpening.detail(sourceId, bookUrl)))
    private var loadJob: kotlinx.coroutines.Job? = null
    private var refreshJob: kotlinx.coroutines.Job? = null
    val state: StateFlow<BookState> = _state

    /** 离线下载进度（null = 未在下载）。 */
    val download: StateFlow<com.will.lightreader.data.BookDownloader.Progress?> by lazy { repo.downloadTasks.state(bookKey) }

    /** 书架键（阅读器路由用）。
     *  修复 P0：bookId 用各 source 的 bookIdOf 真实解析（与 saveBook 的 brief.bookId 一致），
     *  不再自造正则取「URL 最后一段数字」——否则 URL 数字 ≠ 真实 bookId 时 bookKey 错位，
     *  导致阅读进度/离线章节记录错乱。 */
    val bookKey: String = BookOpening.detail(sourceId, bookUrl)?.let {
        com.will.lightreader.data.LibraryRepository.bookKey(sourceId, it.bookId)
    } ?: if (sourceId == com.will.lightreader.source.local.LocalIds.SOURCE) {
        // 本地书：bookId 就是导入时的内容哈希，不做站点 URL 解析
        com.will.lightreader.data.LibraryRepository.bookKey(
            sourceId,
            com.will.lightreader.source.local.LocalIds.bookIdOf(bookUrl),
        )
    } else {
        com.will.lightreader.data.LibraryRepository.bookKey(
            sourceId,
            // 旧版书源的书（linovelib）解析不了 wenku8 URL，此时退回 URL 末段数字，
            // 与历史入库的 bookId 一致；绝不能在这里抛异常把详情页炸掉。
            runCatching { com.will.lightreader.source.wenku8.Wenku8Routes.bookIdOf(bookUrl) }
                .getOrElse { bookUrl.substringAfterLast('/').substringBefore('.').ifBlank { bookUrl } },
        )
    }

    /** 当前书源；旧版书源已停用，返回 null 时页面直接给降级提示。 */
    private val source = registry.find(sourceId)

    init {
        viewModelScope.launch { download.collect { if(it?.finished==true)refreshDownloaded() } }
        if (source == null) {
            viewModelScope.launch {
                val local = loadLocal(repo.cachedBookDetail(bookKey))
                val error = SourceError.Unsupported(bookUrl)
                _state.value = local?.copy(loading = false, error = if (local.chapters.isEmpty()) error else null)
                    ?: BookState(loading = false, error = error)
            }
        } else {
            reload()
        }
    }

    /** 刷新已下载标记（下载完成/删除后调用）。 */
    fun refreshDownloaded() {
        viewModelScope.launch {
            val keys = repo.downloadedChapters(bookKey).map { it.key }.toSet()
            _state.value = _state.value.copy(downloadedKeys = keys)
        }
    }

    /** 删除单章离线缓存。 */
    fun removeDownload(chapterUrl: String) {
        viewModelScope.launch {
            repo.removeChapterContent(chapterUrl)
            refreshDownloaded()
        }
    }

    fun reload() {
        doReload(retried = false)
    }

    /** 阅读页返回后刷新「读到」与百分比（VM 在 back stack 上不会自动更新）。 */
    fun refreshProgress() {
        viewModelScope.launch {
            val chapters = _state.value.chapters
            if (chapters.isEmpty()) return@launch
            val (pct, title, url) = readProgress(chapters)
            _state.value = _state.value.copy(readPercent = pct, readChapter = title, readChapterUrl = url)
        }
    }

    /** 算阅读进度：返回 (百分比, 上次读到章节名, 章节URL)。 */
    private suspend fun readProgress(chapters: List<com.will.lightreader.domain.ChapterRef>): Triple<Int, String, String> {
        val pos = runCatching { repo.position(bookKey) }.getOrNull() ?: return Triple(0, "", "")
        val ci = chapters.indexOfFirst { it.chapterUrl == pos.chapterUrl }
        val pct = if (ci >= 0 && chapters.isNotEmpty()) ((ci + 1) * 100 / chapters.size) else 0
        return Triple(pct, pos.chapterTitle ?: chapters.getOrNull(ci)?.title ?: "", pos.chapterUrl ?: "")
    }

    /** 下拉刷新：静默重拉详情+目录（不清已有内容），失败保留旧数据。 */
    fun consumeRefreshMessage() { _state.value = _state.value.copy(refreshMessage = null) }

    fun leavePage() { refreshJob?.cancel(); _state.value = _state.value.copy(refreshing = false) }

    fun refresh() {
        val src = source ?: return
        if (refreshJob?.isActive == true || loadJob?.isActive == true) return
        _state.value = _state.value.copy(refreshing = true, refreshMessage = null)
        refreshJob = viewModelScope.launch {
            try {
                val result = kotlinx.coroutines.withTimeout(35_000) {
                    src.bookDetail(bookUrl) to src.chapterList(bookUrl)
                }
                val (detail, chapters) = result
                val downloaded = repo.downloadedChapters(bookKey).map { it.key }.toSet()
                val (pct, title, url) = readProgress(chapters)
                repo.saveBook(com.will.lightreader.domain.BookBrief(sourceId, detail.bookId, detail.bookUrl,
                    detail.title, detail.author, detail.coverUrl))
                repo.saveChapters(bookKey, chapters)
                repo.cacheBookDetail(bookKey, detail, catalogueReady = true)
                _state.value = _state.value.copy(detail = detail, chapters = chapters, downloadedKeys = downloaded,
                    error = null, offline = false, hasCachedDetail = true, readPercent = pct, readChapter = title, readChapterUrl = url)
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value = _state.value.copy(refreshMessage = "刷新超时，已保留原内容")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _state.value = _state.value.copy(refreshMessage = "刷新失败，已保留原内容") }
            finally { _state.value = _state.value.copy(refreshing = false) }
        }
    }

    private fun doReload(retried: Boolean) {
        loadJob?.cancel()
        refreshJob?.cancel()
        loadJob = viewModelScope.launch {
            // 1) 本地快照秒开：有本地目录先显示（离线立即可用，不干等网络超时）
            val (cached, local) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val cached = repo.cachedBookDetail(bookKey)
                cached to loadLocal(cached)
            }
            _state.value = local ?: _state.value.copy(loading = true, refreshing = false, error = null)
            val hasDirectory = local?.chapters?.isNotEmpty() == true
            if (hasDirectory && cached?.detailIsFresh() == true && cached.catalogueIsFresh()) return@launch
            // 2) 网络刷新：成功覆盖；失败且有本地数据 = 静默保持离线只读
            try {
                val src = source ?: return@launch
                val freshDetail = cached?.takeIf { it.detailIsFresh() }?.detail
                val detail = freshDetail ?: src.bookDetail(bookUrl)
                _state.value = _state.value.copy(detail = detail, hasCachedDetail = true)
                if (freshDetail == null) {
                    try {
                        repo.saveBook(com.will.lightreader.domain.BookBrief(
                            sourceId, detail.bookId, detail.bookUrl, detail.title, detail.author, detail.coverUrl,
                        ))
                        repo.cacheBookDetail(bookKey, detail)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { /* Metadata persistence is best effort during a live open. */ }
                }
                val chapters = src.chapterList(bookUrl)
                val downloaded = runCatching { repo.downloadedChapters(bookKey).map { it.key }.toSet() }
                    .getOrDefault(emptySet())
                val (pct, chTitle, chUrl) = readProgress(chapters)
                _state.value = BookState(loading = false, detail = detail, chapters = chapters, hasCachedDetail = true,
                    downloadedKeys = downloaded, readPercent = pct, readChapter = chTitle, readChapterUrl = chUrl)
                // 落书架 + 目录（离线/记忆的地基）
                try {
                    repo.saveChapters(bookKey, chapters)
                    repo.cacheBookDetail(bookKey, detail, catalogueReady = true)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* Keep the catalogue available in memory. */ }
            } catch (e: SourceError) {
                // 限流自动重试一次（歇 2s 再来），不把「请求频繁」直接甩给用户
                if (e is SourceError.RateLimited && !retried && local == null) {
                    kotlinx.coroutines.delay(2_000)
                    doReload(retried = true)
                    return@launch
                }
                // 离线只读模式：网络不可用就用本地目录降级，不把下载入口锁死
                _state.value = bookLoadFailure(_state.value,hasDirectory,e)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value=bookLoadFailure(_state.value,hasDirectory,SourceError.Network(java.util.concurrent.TimeoutException("目录或源脚本执行超时，请重试")))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = bookLoadFailure(_state.value,hasDirectory,SourceError.Network(e))
            }
        }
    }

    /** 本地快照 → 离线只读状态（书 + 目录 + 下载标记）；无本地数据返回 null。 */
    private suspend fun loadLocal(cached: CachedBookDetail?): BookState? {
        val b = runCatching { repo.book(bookKey) }.getOrNull() ?: return null
        val chapters = runCatching {
            repo.chapters(bookKey).map { ChapterRef(it.key, it.title, it.volume) }
        }.getOrDefault(emptyList())
        if (chapters.isEmpty() && cached == null) return null
        val downloaded = runCatching { repo.downloadedChapters(bookKey).map { it.key }.toSet() }
            .getOrDefault(emptySet())
        val (pct, chapterTitle, chapterUrl) = readProgress(chapters)
        return BookState(
            loading = chapters.isEmpty(), offline = source == null, hasCachedDetail = cached != null,
            detail = cached?.detail ?: BookDetail(
                sourceId = b.sourceId, bookId = b.bookId, bookUrl = b.bookUrl,
                title = b.title, author = b.author, coverUrl = b.coverUrl,
            ),
            chapters = chapters, downloadedKeys = downloaded,
            readPercent = pct, readChapter = chapterTitle, readChapterUrl = chapterUrl,
        )
    }

    /** 离线下载全书。 */
    fun downloadAll() = downloadVolumes(null)

    /** 上次失败的章节（重试用尽仍失败的），点失败提示可一键补救。 */

    /** 重试上次下载失败的章节。 */
    fun retryFailed() {
        val targets = repo.downloadTasks.failed(bookKey)
        if (targets.isEmpty()) return
        startDownload(targets)
    }
    fun cancelDownload() = repo.downloadTasks.cancel(bookKey)

    /** 按册（分卷）下载；volumes = null 表示全书。 */
    fun downloadVolumes(volumes: Set<String>?) {
        if (source?.supportsBulkDownload != true) return
        if (download.value?.finished == false) return
        viewModelScope.launch {
            val chapters = repo.chapters(bookKey)
                .filter { volumes == null || (it.volume.ifBlank { "全书" }) in volumes }
                .map { com.will.lightreader.domain.ChapterRef(it.key, it.title, it.volume) }
            if (chapters.isEmpty()) return@launch
            startDownload(chapters)
        }
    }

    private fun startDownload(chapters: List<com.will.lightreader.domain.ChapterRef>) {
        // 应用级作用域：离开书页下载不断流，整册必须下完
        appScope.launch {
            val src = source ?: return@launch
            val downloaded=repo.downloadedChapters(bookKey).map { it.key }.toSet()
            // 已下载集合只查一次库；逐章读缓存文件会把下载主线拖慢
            val downloader = com.will.lightreader.data.BookDownloader(
                isDownloaded = { url -> if(src is com.will.lightreader.source.ComicSourceBridge)repo.comicChapterContent(url)!=null else url in downloaded },
                // 标记为后台请求：取页层让位给用户正在等的页面
                fetch = {
                    kotlinx.coroutines.withContext(
                        kotlinx.coroutines.Dispatchers.IO + com.will.lightreader.source.net.RequestPriority.Background,
                    ) { src.chapterContent(it) }
                },
                save = { url, c ->
                    if(c.comicPages.isNotEmpty()) {
                        val comic=(src as? com.will.lightreader.source.ComicSourceBridge)?.comic as? com.will.lightreader.source.script.ScriptComicSource
                            ?: error("本源暂不支持漫画离线下载")
                        repo.downloadComicChapter(url,c,comic) { done,total->repo.downloadTasks.page(bookKey,done,total) }
                    } else repo.saveChapterContent(url, c)
                },
                prefetchImage = prefetchImage,
                concurrency=if(src is com.will.lightreader.source.ComicSourceBridge)1 else 3,
            )
            repo.downloadTasks.start(bookKey,appScope,chapters,downloader)
        }
    }

    companion object {
        fun factory(
            registry: SourceRegistry,
            repo: com.will.lightreader.data.LibraryRepository,
            prefetchImage: suspend (String) -> Unit,
            appScope: kotlinx.coroutines.CoroutineScope,
            sourceId: String,
            bookUrl: String,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { BookViewModel(registry, repo, prefetchImage, appScope, sourceId, bookUrl) } }
    }
}
