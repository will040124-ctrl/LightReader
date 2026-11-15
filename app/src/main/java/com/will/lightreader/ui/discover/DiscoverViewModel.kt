package com.will.lightreader.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.BrowsePage
import com.will.lightreader.domain.BrowseSection
import com.will.lightreader.domain.TagGroup
import com.will.lightreader.source.NovelSource
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.SourceRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 发现页三个入口：榜单 / 标签云 / 完结全本。 */
enum class DiscoverTab(val label: String) {
    HOME("首页"),
    RANKING("榜单"),
    TAGS("标签"),
    COMPLETED("完结"),
}

/** 搜索范围：书名 / 作者。 */
enum class SearchScope(val label: String) {
    TITLE("书名"),
    AUTHOR("作者"),
}

/** 标签结果排序：按更新（站点默认）/ 按热门 / 只看完结。 */
enum class TagFilter(val label: String) {
    UPDATED("按更新"),
    POPULAR("按热门"),
    COMPLETED("只看完结"),
}

data class DiscoverState(
    val sourceId: String = "wenku8",
    val tab: DiscoverTab = DiscoverTab.RANKING,
    val kind: String = "dayvisit",
    val query: String = "",
    val scope: SearchScope = SearchScope.TITLE,
    val loading: Boolean = false,
    val refreshMessage: String? = null,
    val refreshing: Boolean = false,   // 下拉刷新中
    val loadingMore: Boolean = false,
    val items: List<BookBrief> = emptyList(),
    val sections: List<BrowseSection> = emptyList(),
    val error: SourceError? = null,
    // 标签云
    val tags: List<TagGroup> = emptyList(),
    val tagsLoading: Boolean = false,
    val tagsError: SourceError? = null,
    val selectedTag: String? = null,
    val tagRoute: String? = null,
    val tagReturnTab: DiscoverTab = DiscoverTab.TAGS,
    val tagFilter: TagFilter = TagFilter.UPDATED,
    // 分页
    val page: Int = 1,
    val hasMore: Boolean = false,
)

class DiscoverViewModel(private val registry: SourceRegistry) : ViewModel() {
    val sourceRevision = registry.revision
    private var seenSourceRevision = sourceRevision.value
    fun sourcesChanged() {
        val revision = sourceRevision.value
        if (revision == seenSourceRevision) return
        seenSourceRevision = revision
        if(registry.find(_state.value.sourceId)?.discovery==null) {
            registry.all().firstOrNull { it.discovery!=null }?.let { switchSource(it.id) }
        } else if(_state.value.sourceId.startsWith("comic-js-")) switchSource(_state.value.sourceId)
    }
    private val initialSource = registry.find("wenku8") ?: registry.all().firstOrNull { it.discovery != null }
    private val _state = MutableStateFlow(DiscoverState(sourceId=initialSource?.id ?: "wenku8",
        kind=initialSource?.discovery?.ranks?.firstOrNull()?.first ?: "dayvisit"))
    val state: StateFlow<DiscoverState> = _state

    private var requestGeneration = 0L
    private var refreshJob: Job? = null
    private var loadJob: Job? = null

    fun consumeRefreshMessage() { _state.value = _state.value.copy(refreshMessage = null) }

    fun leavePage() {
        if (refreshJob?.isActive == true || moreJob?.isActive == true) {
            requestGeneration++
            refreshJob?.cancel(); moreJob?.cancel()
        }
        tagsGeneration++; tagsJob?.cancel()
        _state.value = _state.value.copy(refreshing = false, loadingMore = false, tagsLoading = false)
    }

    private fun invalidateRequests() {
        requestGeneration++
        tagsGeneration++; tagsJob?.cancel()
        refreshJob?.cancel(); loadJob?.cancel(); moreJob?.cancel(); watchdogJob?.cancel()
        _state.value = _state.value.copy(refreshing = false, loadingMore = false, loading = false, tagsLoading = false)
    }
    private var watchdogJob: Job? = null
    private var tagsJob: Job? = null
    private var tagsGeneration = 0L
    private var moreJob: Job? = null

    init { enterSource() }

    /** 可用书源（id to 显示名）：UI 源切换栏据此渲染，单源时自动隐藏。 */
    fun availableSources(): List<Pair<String, String>> = registry.all().filter { it.discovery!=null }.map { it.id to it.displayName }
    fun rankingKinds(): List<Pair<String,String>> = registry.find(_state.value.sourceId)?.discovery?.ranks.orEmpty()
    fun availableTabs(): List<DiscoverTab> {
        val options=registry.find(_state.value.sourceId)?.discovery
        return listOfNotNull(DiscoverTab.HOME.takeIf { options?.supportsHome == true },
            DiscoverTab.RANKING.takeIf { options?.supportsHome != true || options.ranks.isNotEmpty() }, DiscoverTab.TAGS.takeIf { options?.supportsTags==true },
            DiscoverTab.COMPLETED.takeIf { options?.supportsCompleted==true })
    }
    fun tabLabel(tab:DiscoverTab):String = if(tab==DiscoverTab.RANKING)
        registry.find(_state.value.sourceId)?.discovery?.browseLabel ?: tab.label else tab.label

    fun switchSource(id: String) {
        val options=registry.find(id)?.discovery ?: return
        invalidateRequests(); tagsJob?.cancel()
        _state.value = DiscoverState(sourceId = id,kind=options.ranks.firstOrNull()?.first.orEmpty())
        enterSource()
    }

    private fun enterSource() = loadFirst { source ->
        val options = source.prepareDiscovery()
        val kind = options?.ranks?.firstOrNull()?.first.orEmpty()
        val tab = if (options?.supportsHome == true) DiscoverTab.HOME else DiscoverTab.RANKING
        _state.value = _state.value.copy(tab = tab, kind = kind)
        if (tab == DiscoverTab.HOME) source.discoveryHome() else source.popularPage(kind, 1)
    }

    fun selectTab(tab: DiscoverTab) {
        if(tab !in availableTabs())return
        if (_state.value.tab == tab) return
        invalidateRequests()
        _state.value = _state.value.copy(
            tab = tab, query = "", selectedTag = null, tagRoute = null, items = emptyList(), error = null,
            loading = false, loadingMore = false, hasMore = false, page = 1,
        )
        when (tab) {
            DiscoverTab.HOME -> showHome()
            DiscoverTab.RANKING -> if (_state.value.query.isBlank()) browse() else submitSearch(_state.value.query)
            DiscoverTab.TAGS -> if (_state.value.tags.isEmpty()) loadTags()
            DiscoverTab.COMPLETED -> loadCompleted()
        }
    }

    fun switchKind(kind: String) {
        _state.value = _state.value.copy(kind = kind, query = "", selectedTag = null)
        browse()
    }

    fun setScope(scope: SearchScope) {
        val query = _state.value.query
        _state.value = _state.value.copy(scope = scope)
        if (query.isNotBlank()) submitSearch(query)
    }

    fun search(query: String) = submitSearch(query)

    private fun submitSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) { clearSearch(); return }
        val scope = _state.value.scope
        _state.value = _state.value.copy(tab = DiscoverTab.RANKING, query = q, selectedTag = null)
        loadFirst {
            if (scope == SearchScope.AUTHOR) BrowsePage(it.searchByAuthor(q), false)
            else it.searchPage(q,1)
        }
    }

    /** 清除搜索，退出搜索态：立即取消请求 + 清骨架屏，回到榜单（重新拉取当前榜单）。 */
    fun clearSearch() {
        invalidateRequests()
        val tab = if (registry.find(_state.value.sourceId)?.discovery?.supportsHome == true) DiscoverTab.HOME else DiscoverTab.RANKING
        _state.value = _state.value.copy(tab = tab, selectedTag = null, tagRoute = null,
            query = "", loading = false, error = null, items = emptyList())
        if (tab == DiscoverTab.HOME) showHome() else browse()
    }

    private fun showHome() {
        if (_state.value.sections.isEmpty()) loadFirst { it.discoveryHome() }
    }

    fun browse() = loadFirst { it.popularPage(_state.value.kind, 1) }

    fun retry() {
        val s = _state.value
        when {
            s.tab == DiscoverTab.TAGS && s.selectedTag == null -> loadTags()
            s.tab == DiscoverTab.TAGS -> openTag(s.selectedTag!!, s.tagRoute, s.tagFilter, s.tagReturnTab)
            s.tab == DiscoverTab.HOME -> loadFirst { it.discoveryHome() }
            s.tab == DiscoverTab.COMPLETED -> loadCompleted()
            s.query.isNotBlank() -> submitSearch(s.query)
            s.kind.isBlank() -> enterSource()
            else -> browse()
        }
    }

    // ---------------- 标签 ----------------

    fun ensureLoaded() {
        val s = _state.value
        if (s.tab == DiscoverTab.TAGS && s.selectedTag == null && s.tags.isEmpty() && !s.tagsLoading && s.tagsError == null) loadTags()
    }

    fun loadTags(refresh: Boolean = false) {
        if (tagsJob?.isActive == true) return
        val generation = ++tagsGeneration
        val sourceId = _state.value.sourceId
        _state.value = _state.value.copy(tagsLoading = true, tagsError = null, refreshing = refresh, refreshMessage = null)
        tagsJob = viewModelScope.launch {
            try {
                val groups = withTimeoutOrNull(LOAD_TIMEOUT_MS) { registry.get(sourceId).tags() }
                    ?: throw java.util.concurrent.TimeoutException("标签加载超时")
                if (generation == tagsGeneration) _state.value = _state.value.copy(tags = groups, tagsError = null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == tagsGeneration) _state.value = if (_state.value.tags.isNotEmpty())
                    _state.value.copy(refreshMessage = "标签更新失败，已保留原标签")
                else _state.value.copy(tagsError = e as? SourceError ?: SourceError.Network(e))
            } finally {
                if (generation == tagsGeneration) _state.value = _state.value.copy(tagsLoading = false, refreshing = false)
            }
        }
    }

    fun openTag(tag: String, route: String? = null, filter: TagFilter = TagFilter.UPDATED, from: DiscoverTab = DiscoverTab.TAGS) {
        _state.value = _state.value.copy(
            tab = DiscoverTab.TAGS, selectedTag = tag, tagRoute = route, tagReturnTab = from, tagFilter = filter,
            items = emptyList(), page = 1, hasMore = false, error = null,
        )
        loadFirst {
            val options = it.prepareDiscovery()
            if (_state.value.kind.isBlank()) {
                _state.value = _state.value.copy(kind = options?.ranks?.firstOrNull()?.first.orEmpty())
            }
            it.tagBooks(route ?: tag, 1, filter == TagFilter.COMPLETED, filter == TagFilter.POPULAR)
        }
    }

    fun openBookTag(sourceId: String, tag: String) {
        val source = registry.find(sourceId)?.takeIf { it.discovery != null }
            ?: registry.find(_state.value.sourceId)
        if (source != null && source.id != _state.value.sourceId) {
            _state.value = DiscoverState(sourceId = source.id, kind = source.discovery?.ranks?.firstOrNull()?.first.orEmpty())
        }
        openTag(tag, source?.detailTagRoute(tag))
    }

    fun setTagFilter(filter: TagFilter) {
        val tag = _state.value.selectedTag ?: return
        if (filter == _state.value.tagFilter) return
        val s = _state.value
        openTag(tag, s.tagRoute, filter, s.tagReturnTab)
    }

    fun closeTag() {
        invalidateRequests()
        val destination = _state.value.tagReturnTab
        _state.value = _state.value.copy(
            tab = destination, selectedTag = null, tagRoute = null, items = emptyList(), page = 1, hasMore = false,
            error = null, loading = false, loadingMore = false,
        )
        if (destination == DiscoverTab.HOME) showHome()
        else if (_state.value.tags.isEmpty()) loadTags()
    }

    // ---------------- 完结 ----------------

    fun loadCompleted() {
        _state.value = _state.value.copy(tab = DiscoverTab.COMPLETED, selectedTag = null)
        loadFirst { it.completedBooks(1) }
    }

    // ---------------- 刷新 / 分页 ----------------

    /** 下拉刷新：静默重拉当前视图第一页（不清已有内容），完成后关 refreshing。 */
    fun refresh() {
        val s = _state.value
        if (refreshJob?.isActive == true || s.loading) return
        if (s.tab == DiscoverTab.TAGS && s.selectedTag == null) { loadTags(refresh = true); return }
        moreJob?.cancel()
        val generation = ++requestGeneration
        _state.value = s.copy(refreshing = true, loadingMore = false, refreshMessage = null)
        refreshJob = viewModelScope.launch {
            try {
                val page = withTimeoutOrNull(LOAD_TIMEOUT_MS) { fetchPage(1, s) }
                    ?: throw java.util.concurrent.TimeoutException("刷新超时")
                if (generation == requestGeneration) _state.value = _state.value.copy(items = page.items,
                    sections = if (s.tab == DiscoverTab.HOME) page.sections else s.sections,
                    page = 1, hasMore = page.hasMore, error = null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                if (generation == requestGeneration) _state.value = _state.value.copy(refreshMessage = "刷新失败，已保留原列表")
            } finally {
                if (generation == requestGeneration) _state.value = _state.value.copy(refreshing = false)
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.refreshing || s.loadingMore || !s.hasMore) return
        if (moreJob?.isActive == true) return
        val generation = requestGeneration
        moreJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            try {
                val next = _state.value.page + 1
                val page = withTimeoutOrNull(LOAD_TIMEOUT_MS) { fetchPage(next, s) }
                if (generation != requestGeneration) return@launch
                _state.value = if (page == null) {
                    _state.value.copy(loadingMore = false)
                } else {
                    _state.value.copy(
                        loadingMore = false,
                        items = (_state.value.items + page.items).distinctBy { it.sourceId to it.bookId },
                        page = next, hasMore = page.hasMore,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                if (generation == requestGeneration) _state.value = _state.value.copy(refreshMessage = "加载失败，请重试")
            } finally {
                if (generation == requestGeneration) _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }

    /** 按当前 tab / 搜索态取第 [page] 页。 */
    private suspend fun fetchPage(page: Int, s: DiscoverState): BrowsePage {
        val source: NovelSource = registry.get(s.sourceId)
        return when {
            s.tab == DiscoverTab.HOME -> source.discoveryHome()
            s.tab == DiscoverTab.TAGS && s.selectedTag != null ->
                source.tagBooks(
                    s.tagRoute ?: s.selectedTag, page,
                    completedOnly = s.tagFilter == TagFilter.COMPLETED,
                    popular = s.tagFilter == TagFilter.POPULAR,
                )
            s.tab == DiscoverTab.COMPLETED -> source.completedBooks(page)
            s.query.isNotBlank() -> if(s.scope == SearchScope.AUTHOR) BrowsePage(source.searchByAuthor(s.query),false)
                else source.searchPage(s.query,page)
            else -> source.popularPage(s.kind, page)
        }
    }

    private fun loadFirst(block: suspend (NovelSource) -> BrowsePage) {
        invalidateRequests()
        val generation = requestGeneration
        val sourceId = _state.value.sourceId
        _state.value = _state.value.copy(
            loading = true, error = null, loadingMore = false,
            items = emptyList(), page = 1, hasMore = false,
        )
        // 独立兜底：挂在 viewModelScope（而非 loadJob 子级），loadJob 被取消时它仍要清骨架屏。
        val watchdog = viewModelScope.launch {
            delay(LOAD_TIMEOUT_MS)
            if (generation == requestGeneration && _state.value.loading) {
                _state.value = _state.value.copy(loading = false,
                    error = SourceError.Network(java.util.concurrent.TimeoutException("加载超时了喵…")))
            }
        }
        watchdogJob = watchdog
        loadJob = viewModelScope.launch {
            try {
                // withTimeoutOrNull：超时返回 null。不能用 withTimeout——它的 CancellationException
                // 会被下面的「外部取消」分支重抛，导致 loading 永不清除（无限骨架屏）。
                val page = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    block(registry.get(sourceId))
                }
                if (generation != requestGeneration) return@launch
                if (page == null) {
                    _state.value = _state.value.copy(loading = false,
                        error = SourceError.Network(java.util.concurrent.TimeoutException("加载超时了喵…检查网络后再试")))
                } else {
                    _state.value = _state.value.copy(
                        loading = false, items = page.items,
                        sections = if (_state.value.tab == DiscoverTab.HOME) page.sections else _state.value.sections, page = 1,
                        hasMore = page.hasMore, error = null,
                    )
                }
            } catch (e: SourceError) {
                if (generation == requestGeneration) _state.value = _state.value.copy(loading = false, error = e)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e   // 外部取消（新请求接管）：不清状态
            } catch (e: Exception) {
                if (generation == requestGeneration) _state.value = _state.value.copy(loading = false,
                    error = SourceError.Network(e))
            } finally {
                watchdog.cancel()
            }
        }
    }

    companion object {
        /** 加载预算：需覆盖 OkHttp→WebView 降级链（WebView 自身最长 30s），故留 35s。 */
        private const val LOAD_TIMEOUT_MS = 35_000L

        fun defaultKind(sourceId: String) = "dayvisit"

        fun factory(registry: SourceRegistry): ViewModelProvider.Factory = viewModelFactory {
            initializer { DiscoverViewModel(registry) }
        }
    }
}
