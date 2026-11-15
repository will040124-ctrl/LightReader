package com.will.lightreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.will.lightreader.data.BookmarkEntity
import com.will.lightreader.data.ExcerptEntity
import com.will.lightreader.data.LibraryRepository
import com.will.lightreader.domain.Block
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.ChapterRef
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderState(
    val comicMode:String="paged",
    val immersive:Boolean=true,
    val hideStatusBar:Boolean=false,
    val comicRtl:Boolean=false,
    val comicScrollFraction:Float=0f,
    val loading: Boolean = true,
    val repairing: Boolean = false,
    val repairNotice: String? = null,
    val error: SourceError? = null,
    val content: ChapterContent? = null,
    val chapterUrl: String = "",
    val chapterTitle: String = "",
    val bookKey: String = "",
    val sourceId: String = "",
    val atFirst: Boolean = false,
    val atLast: Boolean = false,
    val progressPercent: Int = 0,   // 全书进度百分比（章节索引/总数）
    val fontSize: Int = 18,
    val lineHeight: Float = 1.7f,
    val serif: Boolean = true,
    val paper: String = "follow",
    val toolbarVisible: Boolean = true,
    val downloadState: String? = null,   // 本章下载状态
    val downloadActive: Boolean = false,
    val offlineComicChapter: String? = null,
    val comicDownloadAttempt: String? = null,
    val startBlockIndex: Int = 0,        // 上次阅读位置（锚点块索引）
    val startCharOffset: Int = 0,
    val startAtEnd: Boolean = false,     // Backward page crossing lands after pagination, including a footer-only last page.
    val chapterDirection: Int = 0,      // -1 previous, +1 next, 0 first load or same-chapter refresh.
    val bgUri: String = "",              // 自定义背景图
    val twoPage: Boolean = false,        // 双页排版（左右两页并排）
    val turnStyle: String = "slide",     // 翻页动画：slide（平移）/ cover（覆盖）
    val savedTick: Int = 0,
    val chapters: List<ChapterRef> = emptyList(),
    val currentChapterUrl: String = "",   // 位置保存心跳（UI 可显示"已记住"）
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val excerpts: List<ExcerptEntity> = emptyList(),
    val jumpBlock: Int? = null,           // 同章跳转目标（书签/摘抄/搜索）
    val jumpTick: Int = 0,
)

/** 书内搜索命中：章节 + 段索引 + 上下文片段。 */
data class SearchHit(
    val chapterUrl: String,
    val chapterTitle: String,
    val blockIndex: Int,
    val snippet: String,
)

/** 书内搜索状态：只搜当前章与已缓存章节，结果实时给 UI。 */
data class BookSearch(
    val query: String = "",
    val running: Boolean = false,
    val scanned: Int = 0,
    val hits: List<SearchHit> = emptyList(),
    val done: Boolean = false,
)

/**
 * 阅读器状态机：离线优先加载（缓存 → 网络 → 回写缓存）、翻章、锚点式位置记忆。
 */
class ReaderViewModel(
    private val repo: LibraryRepository,
    registry: SourceRegistry,
    private val prefs: ReaderPrefs,
    private val prefetchImage: suspend (String) -> Unit,
    private val sourceId: String,
    private val bookKey: String,
    chapterUrl: String,
) : ViewModel() {
    private val source = registry.find(sourceId) ?: com.will.lightreader.source.UnavailableSource(sourceId)
    private val scriptComic = (source as? com.will.lightreader.source.ComicSourceBridge)?.comic as? com.will.lightreader.source.script.ScriptComicSource
    val supportsComicDownload: Boolean get() = scriptComic != null
    private val _state = MutableStateFlow(
        ReaderState(
            comicMode=prefs.comicMode(bookKey),
            immersive=prefs.immersive,
            hideStatusBar=prefs.hideStatusBar,
            comicRtl=prefs.comicRtl,
            chapterUrl = chapterUrl, bookKey = bookKey, sourceId = sourceId,
            fontSize = prefs.fontSize, lineHeight = prefs.lineHeight, paper = prefs.paper,
            serif = prefs.serif,
            bgUri = prefs.bgUri, twoPage = prefs.twoPage,
            turnStyle = prefs.turnStyle,
        )
    )
    val state: StateFlow<ReaderState> = _state

    private val _search = MutableStateFlow(BookSearch())
    val search: StateFlow<BookSearch> = _search
    private var searchJob: Job? = null

    init {
        viewModelScope.launch { repo.downloadTasks.state(bookKey).collect { progress ->
            if(progress!=null)_state.value=_state.value.copy(downloadActive = !progress.finished, downloadState=when {
                progress.cancelled->"下载已取消，可继续下载"
                !progress.finished->if(progress.pageTotal>0)"下载中 ${progress.pageDone}/${progress.pageTotal} 页" else "下载中 ${progress.done+progress.failed}/${progress.total}"
                progress.failed>0->"下载失败：${progress.lastError ?: "请重试"}"
                else->"已下载 ✓"
            })
            val s = _state.value
            val content = s.content
            if (progress?.finished == true && !s.loading && content != null && content.chapterUrl == s.chapterUrl && content.comicPages.isNotEmpty()) {
                val saved = withContext(Dispatchers.IO) { repo.hasOfflineComicChapter(s.chapterUrl) }
                if (_state.value.chapterUrl == s.chapterUrl) _state.value = _state.value.copy(offlineComicChapter = s.chapterUrl.takeIf { saved })
            }
        } }
        load(chapterUrl)
        viewModelScope.launch {
            repo.observeBookmarks(bookKey).collect { list ->
                _state.value = _state.value.copy(bookmarks = list)
            }
        }
        viewModelScope.launch {
            repo.observeExcerpts(bookKey).collect { list ->
                _state.value = _state.value.copy(excerpts = list)
            }
        }
    }

    /** [startAt] 非空时忽略记忆位置，直接落到指定段（书签/摘抄/搜索跳转）。 */
    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var prefetchJob: Job? = null

    fun load(chapterUrl: String, startAt: Int? = null, fromEnd: Boolean = false) {
        val generation = ++loadGeneration
        loadJob?.cancel()
        prefetchJob?.cancel()
        // 注意不清 content：翻章时保留旧章渲染，翻页动画才完整
        _state.value = _state.value.copy(
            loading = true, error = null, chapterUrl = chapterUrl,
            repairing = false, repairNotice = null,
            jumpBlock = null,
        )
        loadJob = viewModelScope.launch {
            try {
                // Imported account records can have a saved chapter before a local directory exists.
                // Resolve the real directory before reading so next/previous chapter keep working.
                withContext(Dispatchers.IO) {
                    if (sourceId != com.will.lightreader.source.local.LocalIds.SOURCE && repo.chapters(bookKey).isEmpty()) {
                        repo.book(bookKey)?.let { book -> repo.saveChapters(bookKey, source.chapterList(book.bookUrl)) }
                    }
                }
                val repair = withContext(Dispatchers.IO) { repo.chapterNeedsRepair(chapterUrl) }
                if (generation == loadGeneration) _state.value = _state.value.copy(repairing = repair)
                // A damaged cache remains on disk until a validated replacement is available.
                val offlineComic=if(sourceId.startsWith("comic-js-"))withContext(Dispatchers.IO) { repo.comicChapterContent(chapterUrl) } else null
                val content = offlineComic ?: if (source is com.will.lightreader.source.ComicSourceBridge)
                    withContext(Dispatchers.IO) { source.chapterContent(chapterUrl) }
                        .also { fresh -> withContext(Dispatchers.IO) { repo.saveChapterContent(chapterUrl, fresh) } }
                    else withContext(Dispatchers.IO) { repo.chapterContent(chapterUrl) }
                    ?: withContext(Dispatchers.IO) { source.chapterContent(chapterUrl) }
                        .also { fresh -> withContext(Dispatchers.IO) { runCatching { repo.saveChapterContent(chapterUrl, fresh) } } }
                if (content.comicPages.isNotEmpty()) com.will.lightreader.domain.validateComicPages(content.comicPages)
                if (com.will.lightreader.domain.TextIntegrity.damaged(content))
                    throw SourceError.ParseChanged(chapterUrl, "正文编码异常，请重试本章")
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (generation != loadGeneration) return@launch
                val chapters = withContext(Dispatchers.IO) { repo.chapters(bookKey) }
                val idx = chapters.indexOfFirst { it.key == chapterUrl }
                if (com.will.lightreader.domain.TextIntegrity.titleOnly(content, chapters.getOrNull(idx)?.title.orEmpty()) ||
                    com.will.lightreader.domain.TextIntegrity.titleOnly(content, content.title))
                    throw SourceError.ParseChanged(chapterUrl, "正文尚未完整载入，请重试本章")
                // 锚点记忆：同章恢复到上次的块位置
                val pos = withContext(Dispatchers.IO) { repo.position(bookKey) }
                val startBlock = startAt ?: if (fromEnd) {
                    if (content.comicPages.isNotEmpty()) content.comicPages.lastIndex else content.blocks.lastIndex.coerceAtLeast(0)
                } else if (content.comicPages.isNotEmpty()) {
                    com.will.lightreader.domain.restoreComicPage(content.comicPages, prefs.comicPageId(chapterUrl),
                        if (pos?.chapterUrl == chapterUrl) pos.blockIndex else 0)
                } else if (pos != null && pos.chapterUrl == chapterUrl) {
                    if (pos.blockIndex in 0..content.blocks.size) pos.blockIndex
                    else (pos.offsetRatio * content.blocks.lastIndex.coerceAtLeast(0)).toInt()
                } else 0
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (generation != loadGeneration) return@launch
                val previousIndex = chapters.indexOfFirst { it.key == _state.value.content?.chapterUrl }
                _state.value = _state.value.copy(
                    loading = false, content = content,
                    repairing = false, repairNotice = if (repair) "已重新获取正常正文" else null,
                    chapterTitle = chapters.getOrNull(idx)?.title?.takeIf { it.isNotBlank() } ?: content.title,
                    atFirst = idx <= 0,
                    atLast = idx < 0 || idx >= chapters.size - 1,
                    progressPercent = if (idx >= 0 && chapters.isNotEmpty()) ((idx + 1) * 100 / chapters.size) else 0,
                    startBlockIndex = startBlock,
                    startAtEnd = fromEnd && startAt == null,
                    chapterDirection = if (idx >= 0 && previousIndex >= 0) (idx - previousIndex).compareTo(0) else 0,
                    comicScrollFraction = if (startAt != null || fromEnd) 0f else if (content.fixedPage?.let { it.pdfIndex == null } == true &&
                        sourceId == com.will.lightreader.source.local.LocalIds.SOURCE && pos?.chapterUrl == chapterUrl)
                        pos.offsetRatio.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                    else content.comicPages.getOrNull(startBlock)?.let { prefs.comicScrollFraction(chapterUrl,it.id) } ?: 0f,
                    startCharOffset = if (startAt != null || fromEnd || pos?.chapterUrl != chapterUrl) 0 else prefs.inlineAnchor(chapterUrl, startBlock),
                )
                // 打开即记一笔（章首；后续滚动周期覆盖）。已有读档不动，避免抹掉字内锚点。
                // 提交后的记账失败不能拖垮已可读的正文。
                runCatching {
                    repo.touchRead(bookKey)
                    if (startBlock == 0 && pos?.chapterUrl != chapterUrl) savePosition(0, 0f)
                }
                if (content.comicPages.isEmpty()) prefetchNeighbors(generation)
            } catch (e: SourceError) {
                if (generation != loadGeneration) return@launch
                // 失败也保留旧章可读：初始打开无内容时才落到全屏错误面板，其余走顶部横幅
                _state.value = _state.value.copy(loading = false, error = e)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == loadGeneration) _state.value = _state.value.copy(loading = false,
                    error = SourceError.Network(e))
            } finally {
                if (generation == loadGeneration) _state.value = _state.value.copy(loading = false, repairing = false)
            }
        }
    }

    fun consumeRepairNotice() { _state.value = _state.value.copy(repairNotice = null) }

    /** 关掉加载失败横幅：旧章继续可读，不再提示。 */
    fun dismissError() {
        val state = _state.value
        val content = state.content
        val index = state.chapters.indexOfFirst { it.chapterUrl == content?.chapterUrl }
        _state.value = state.copy(error = null,
            chapterUrl = content?.chapterUrl ?: state.chapterUrl,
            chapterTitle = state.chapters.getOrNull(index)?.title ?: content?.title ?: state.chapterTitle,
            atFirst = if (content == null) state.atFirst else index <= 0,
            atLast = if (content == null) state.atLast else index < 0 || index >= state.chapters.lastIndex)
    }

    /** 性能：静默预取上/下章入缓存，翻页秒开。换章即取消，快速反向不留无用请求。 */
    private fun prefetchNeighbors(generation: Long) {
        val s = _state.value
        prefetchJob = viewModelScope.launch {
            val chapters = repo.chapters(s.bookKey)
            val idx = chapters.indexOfFirst { it.key == s.chapterUrl }
            val targets = listOfNotNull(chapters.getOrNull(idx - 1), chapters.getOrNull(idx + 1))
            for (t in targets) {
                if (generation != loadGeneration) return@launch
                if (withContext(Dispatchers.IO) { repo.chapterContent(t.key) } == null) {
                    runCatching {
                        val c = withContext(Dispatchers.IO) { source.chapterContent(t.key) }
                        if (generation != loadGeneration) return@launch
                        withContext(Dispatchers.IO) { repo.saveChapterContent(t.key, c) }
                    }
                }
            }
        }
    }

    fun nextChapter(fromBeginning:Boolean=true) {
        val s = _state.value
        if (s.loading) return
        val generation = loadGeneration
        viewModelScope.launch {
            val chapters = withContext(Dispatchers.IO) { repo.chapters(s.bookKey) }
            if (generation != loadGeneration || _state.value.chapterUrl != s.chapterUrl) return@launch
            val idx = chapters.indexOfFirst { it.key == s.chapterUrl }
            if (idx in 0 until chapters.size - 1) load(chapters[idx + 1].key,if(fromBeginning)0 else null)
        }
    }

    fun prevChapter(fromEnd: Boolean = false) {
        val s = _state.value
        if (s.loading) return
        val generation = loadGeneration
        viewModelScope.launch {
            val chapters = withContext(Dispatchers.IO) { repo.chapters(s.bookKey) }
            if (generation != loadGeneration || _state.value.chapterUrl != s.chapterUrl) return@launch
            val idx = chapters.indexOfFirst { it.key == s.chapterUrl }
            if (idx > 0) load(chapters[idx - 1].key, fromEnd = fromEnd)
        }
    }

    /** 锚点式位置记忆：块索引 + 偏移比例。只写存储、**不改 UI State**（每 1.5s 触发重组会卡滚动）。 */
    fun savePosition(blockIndex: Int, offsetRatio: Float, charOffset: Int = 0) {
        val s = _state.value
        prefs.saveInlineAnchor(s.chapterUrl, blockIndex, charOffset)
        viewModelScope.launch {
            repo.savePosition(s.bookKey, s.chapterUrl, s.chapterTitle, blockIndex, offsetRatio)
        }
    }

    fun comicImageHeaders(page: com.will.lightreader.domain.ComicPage): Map<String, String> =
        (source as? com.will.lightreader.source.ComicSourceBridge)?.comic?.imageHeaders(_state.value.chapterUrl, page).orEmpty()

    fun rememberComicPage(chapterUrl: String, index: Int) {
        val s = _state.value
        if (s.loading || s.chapterUrl != chapterUrl) return
        val pages = s.content?.comicPages.orEmpty()
        val page = pages.getOrNull(index) ?: return
        if(s.comicMode=="paged")liveComicAnchor=Triple(chapterUrl,index,prefs.comicScrollFraction(chapterUrl,page.id))
        prefs.saveComicPageId(chapterUrl, page.id)
        savePosition(index, if (pages.size <= 1) 1f else index.toFloat() / (pages.size - 1))
    }
    private var liveComicAnchor:Triple<String,Int,Float>?=null
    fun rememberComicScroll(chapterUrl:String,index:Int,fraction:Float,persist:Boolean) {
        val s=_state.value
        if(s.loading || s.chapterUrl!=chapterUrl)return
        val page=s.content?.comicPages?.getOrNull(index) ?: return
        liveComicAnchor=Triple(chapterUrl,index,fraction)
        if(persist) {
            prefs.saveComicScroll(chapterUrl,page.id,fraction)
            rememberComicPage(chapterUrl,index)
        }
    }
    /** 本地 CBZ 一章即一页：连续下滑按章读取图片路径，成功后缓存避免来回滚动重复读盘。 */
    private val importedPageRefs = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun importedComicPageRef(chapterUrl: String): String? {
        importedPageRefs[chapterUrl]?.let { return it }
        val loaded = withContext(Dispatchers.IO) { repo.chapterContent(chapterUrl) }
        val ref = loaded?.fixedPage?.ref
            ?: loaded?.blocks?.filterIsInstance<Block.Image>()?.firstOrNull()?.ref
        return ref?.also { importedPageRefs[chapterUrl] = it }
    }

    /** 连续下滑时把“当前页”同步给抽屉高亮、书签和阅读位置。 */
    fun showImportedComicPage(chapterUrl: String, title: String) {
        val s = _state.value
        if (s.loading) return
        if (s.chapterUrl != chapterUrl || s.chapterTitle != title)
            _state.value = s.copy(chapterUrl = chapterUrl, chapterTitle = title)
    }

    private var liveImportedAnchor: Triple<String, String, Float>? = null
    fun importedComicAnchor(): Triple<String, String, Float>? = liveImportedAnchor

    /** Track the strip without recomposing the whole reader for every pixel. */
    fun rememberImportedComicPosition(chapterUrl: String, title: String, fraction: Float) {
        val s = _state.value
        if (s.loading || s.comicMode != "scroll") return
        liveImportedAnchor = Triple(chapterUrl, title, fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
        showImportedComicPage(chapterUrl, title)
    }

    fun saveImportedComicPosition(chapterUrl: String, title: String, fraction: Float) {
        val s = _state.value
        if (s.loading || s.comicMode != "scroll") return
        rememberImportedComicPosition(chapterUrl, title, fraction)
        val saved = liveImportedAnchor ?: return
        viewModelScope.launch {
            repo.savePosition(s.bookKey, saved.first, saved.second, 0, saved.third)
        }
    }

    fun setComicImmersive(on:Boolean) {
        prefs.immersive = on
        _state.value = _state.value.copy(immersive = on)
    }

    fun setComicRtl(rtl:Boolean) {
        prefs.comicRtl = rtl
        _state.value = _state.value.copy(comicRtl = rtl)
    }

    fun setComicMode(mode:String,currentIndex:Int?=null) {
        val s=_state.value;val value=if(mode=="scroll")"scroll" else "paged"
        val anchor=liveComicAnchor?.takeIf { it.first==s.chapterUrl }
        val page=currentIndex ?: anchor?.second ?: s.startBlockIndex
        prefs.saveComicMode(bookKey,value)
        _state.value=s.copy(comicMode=value,startBlockIndex=page,
            comicScrollFraction=liveImportedAnchor?.takeIf { it.first==s.chapterUrl }?.third
                ?: anchor?.takeIf { it.second==page }?.third ?: s.content?.comicPages?.getOrNull(page)?.let { prefs.comicScrollFraction(s.chapterUrl,it.id) } ?: 0f)
    }

    // ---------- 排版 ----------
    fun refreshPreferences() {
        val current = _state.value
        val anchor = liveComicAnchor?.takeIf { it.first == current.chapterUrl }
        _state.value = current.copy(fontSize = prefs.fontSize, lineHeight = prefs.lineHeight,
            serif = prefs.serif, paper = prefs.paper, bgUri = prefs.bgUri,
            twoPage = prefs.twoPage, turnStyle = prefs.turnStyle,
            comicRtl = prefs.comicRtl, immersive = prefs.immersive, hideStatusBar = prefs.hideStatusBar,
            comicMode = prefs.comicMode(bookKey),
            startBlockIndex = anchor?.second ?: current.startBlockIndex,
            comicScrollFraction = liveImportedAnchor?.takeIf { it.first == current.chapterUrl }?.third
                ?: anchor?.third ?: current.comicScrollFraction)
    }
    fun setFontSize(delta: Int) {
        val v = (_state.value.fontSize + delta).coerceIn(13, 32)
        prefs.fontSize = v
        _state.value = _state.value.copy(fontSize = v)
    }

    fun setLineHeight(v: Float) {
        prefs.lineHeight = v
        _state.value = _state.value.copy(lineHeight = v)
    }

    fun toggleSerif() {
        val v = !_state.value.serif
        prefs.serif = v
        _state.value = _state.value.copy(serif = v)
    }

    /** 双页排版切换（平板大屏：左右两页并排）。 */
    fun toggleTwoPage() {
        val v = !_state.value.twoPage
        prefs.twoPage = v
        _state.value = _state.value.copy(twoPage = v)
    }

    /** 翻页动画：平移 / 覆盖。 */
    fun toggleTurnStyle() {
        val v = if (_state.value.turnStyle == "cover") "slide" else "cover"
        prefs.turnStyle = v
        _state.value = _state.value.copy(turnStyle = v)
    }

    fun cyclePaper() {
        val p = prefs.cyclePaper()
        _state.value = _state.value.copy(paper = p)
    }

    fun setPaper(value: String) {
        if (value !in setOf("follow", "light", "sepia", "dark")) return
        prefs.paper = value
        _state.value = _state.value.copy(paper = value)
    }

    fun toggleToolbar() {
        _state.value = _state.value.copy(toolbarVisible = !_state.value.toolbarVisible)
    }

    /** 下载本章离线（正文 + 插图预取）。 */
    fun downloadCurrent() {
        val s = _state.value
        val content = s.content ?: return
        if (s.loading || content.chapterUrl != s.chapterUrl) return
        if (content.comicPages.isNotEmpty()) {
            if(content.comicPages.all { it.localRef!=null }) { _state.value=s.copy(downloadState="已下载 ✓");return }
            val comic=scriptComic
            if(comic==null) { _state.value=s.copy(downloadState="请先启用本源再下载");return }
            val downloader=com.will.lightreader.data.BookDownloader(
                isDownloaded={repo.comicChapterContent(it)!=null},
                fetch={withContext(Dispatchers.IO) { source.chapterContent(it) }},
                save={url,fresh->withContext(Dispatchers.IO) { repo.downloadComicChapter(url,fresh,comic) { done,total->repo.downloadTasks.page(bookKey,done,total) } }},
                concurrency=1)
            if (repo.downloadTasks.start(bookKey,repo.downloadScope,listOf(ChapterRef(s.chapterUrl,s.chapterTitle)),downloader)) {
                _state.value = _state.value.copy(comicDownloadAttempt = s.chapterUrl)
            }
            return
        }
        if (!s.downloadState.isNullOrEmpty() && !s.downloadState!!.contains("已")) return
        _state.value = s.copy(downloadState = "下载中喵…")
        viewModelScope.launch {
            runCatching {
                repo.saveChapterContent(s.chapterUrl, content)
                for (b in content.blocks) {
                    if (b is com.will.lightreader.domain.Block.Image && b.ref.isNotBlank()) {
                        runCatching { prefetchImage(b.ref) }
                    }
                }
            }.onSuccess {
                _state.value = _state.value.copy(downloadState = "已下载 ✓喵")
            }.onFailure {
                _state.value = _state.value.copy(downloadState = "下载失败了喵…")
            }
        }
    }

    // ---------- 书签 / 摘抄 / 跳转 ----------

    /** 当前页书签：该位置已有则删除，没有则新增。[text] 为当前页首段摘要。 */
    fun toggleBookmark(blockIndex: Int, text: String) {
        val s = _state.value
        viewModelScope.launch {
            val existing = s.bookmarks.firstOrNull {
                it.chapterUrl == s.chapterUrl && it.blockIndex == blockIndex
            }
            if (existing != null) repo.removeBookmark(existing.id)
            else repo.addBookmark(s.bookKey, s.chapterUrl, blockIndex, text.take(120))
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch { repo.removeBookmark(id) }
    }

    fun addExcerpt(blockIndex: Int, text: String) {
        val s = _state.value
        viewModelScope.launch {
            val dup = s.excerpts.any { it.chapterUrl == s.chapterUrl && it.blockIndex == blockIndex }
            if (dup) return@launch
            repo.addExcerpt(s.bookKey, s.chapterUrl, s.chapterTitle, blockIndex, text)
        }
    }

    fun removeExcerpt(id: Long) {
        viewModelScope.launch { repo.removeExcerpt(id) }
    }

    /** 跳到指定章 / 段：同章只滚页，跨章走 load 并落在目标段。 */
    fun jumpTo(chapterUrl: String, blockIndex: Int) {
        val s = _state.value
        if (chapterUrl == s.chapterUrl && s.content != null) {
            _state.value = s.copy(jumpBlock = blockIndex, jumpTick = s.jumpTick + 1)
        } else {
            load(chapterUrl, startAt = blockIndex)
        }
    }

    /** 跳转已被正文消费：清掉目标，旋转/重组时不会重复跳回旧目标。 */
    fun consumeJump() {
        if (_state.value.jumpBlock != null) _state.value = _state.value.copy(jumpBlock = null)
    }

    // ---------- 书内搜索（当前章 + 已缓存章节） ----------

    fun searchBook(query: String) {
        val q = query.trim()
        searchJob?.cancel()
        if (q.isEmpty()) {
            _search.value = BookSearch()
            return
        }
        _search.value = BookSearch(query = q, running = true)
        searchJob = viewModelScope.launch {
            val s = _state.value
            val chapters = withContext(Dispatchers.IO) { repo.chapters(s.bookKey) }
            val cached = withContext(Dispatchers.IO) { repo.downloadedChapters(s.bookKey) }
                .map { it.key }.toHashSet()
            val hits = ArrayList<SearchHit>()
            var scanned = 0
            for (ch in chapters) {
                if (!isActive) break
                if (hits.size >= 80) break
                val content = when {
                    ch.key == s.chapterUrl -> s.content
                    ch.key in cached -> withContext(Dispatchers.IO) { repo.chapterContent(ch.key) }
                    else -> null
                } ?: continue
                scanned++
                content.blocks.forEachIndexed { i, b ->
                    if (b is Block.Paragraph) {
                        val at = b.text.indexOf(q, ignoreCase = true)
                        if (at >= 0) {
                            hits.add(SearchHit(ch.key, ch.title, i, snippet(b.text, at, q.length)))
                        }
                    }
                }
            }
            _search.value = BookSearch(
                query = q, running = false, scanned = scanned, hits = hits, done = true,
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = BookSearch()
    }

    private fun snippet(text: String, at: Int, len: Int): String {
        val start = (at - 30).coerceAtLeast(0)
        val end = (at + len + 60).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") + text.substring(start, end).replace('\n', ' ') +
            (if (end < text.length) "…" else "")
    }

    companion object {
        fun factory(
            repo: LibraryRepository, registry: SourceRegistry, prefs: ReaderPrefs,
            prefetchImage: suspend (String) -> Unit,
            sourceId: String, bookKey: String, chapterUrl: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReaderViewModel(repo, registry, prefs, prefetchImage, sourceId, bookKey, chapterUrl) }
        }
    }

    fun loadChapters(bookKey: String) {
        viewModelScope.launch {
            val chapters = withContext(Dispatchers.IO) {
                repo.chapters(bookKey).map { ChapterRef(it.key, it.title, it.volume) }
            }
            _state.value = _state.value.copy(chapters = chapters)
        }
    }

    fun navigateToChapter(chapterUrl: String) {
        if (chapterUrl == _state.value.content?.chapterUrl && !_state.value.loading) jumpTo(chapterUrl, 0)
        else load(chapterUrl, startAt = 0)
    }
}
