package com.will.lightreader.ui.author

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.source.NovelSource
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.SourceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthorState(
    val authorName: String = "",
    val loading: Boolean = false,
    val items: List<BookBrief> = emptyList(),
    val error: SourceError? = null,
    val refreshing: Boolean = false,   // 下拉刷新中
)

class AuthorViewModel(
    private val registry: SourceRegistry,
    private val sourceId: String,
    private val authorName: String,
    private val authorUrl: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthorState(authorName = authorName))
    val state: StateFlow<AuthorState> = _state

    private var loadJob: kotlinx.coroutines.Job? = null
    private var refreshJob: kotlinx.coroutines.Job? = null
    private var generation = 0L

    init { load() }

    fun load() = doLoad(retried = false)
    fun ensureLoaded() {
        val s = _state.value
        if (!s.loading && s.items.isEmpty() && s.error == null) load()
    }
    fun leavePage() {
        generation++
        loadJob?.cancel(); refreshJob?.cancel()
        _state.value = _state.value.copy(loading = false, refreshing = false)
    }

    fun refresh() {
        if (_state.value.loading || _state.value.refreshing) return
        val token = generation
        _state.value = _state.value.copy(refreshing = true, error = null)
        refreshJob = viewModelScope.launch {
            try {
                val items = kotlinx.coroutines.withTimeout(8_000) {
                    registry.get(sourceId).authorWorks(authorName, authorUrl)
                }
                if (token == generation) _state.value = _state.value.copy(items = unique(items), error = null)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (token == generation) _state.value = _state.value.copy(error = SourceError.Network(e))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                if (token == generation) _state.value = _state.value.copy(error = e as? SourceError ?: SourceError.Network(e))
            } finally {
                if (token == generation) _state.value = _state.value.copy(refreshing = false)
            }
        }
    }

    private fun unique(items: List<BookBrief>) = items.distinctBy { it.sourceId to it.bookId }

    private fun doLoad(retried: Boolean) {
        val token = ++generation
        loadJob?.cancel(); refreshJob?.cancel()
        _state.value = _state.value.copy(loading = true, refreshing = false, error = null)
        loadJob = viewModelScope.launch {
            try {
                val items = kotlinx.coroutines.withTimeout(8_000) {
                    registry.get(sourceId).authorWorks(authorName, authorUrl)
                }
                if (token == generation) _state.value = _state.value.copy(loading = false, items = unique(items), error = null)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (token == generation) _state.value = _state.value.copy(loading = false, error = SourceError.Network(e))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: SourceError) {
                if (e is SourceError.RateLimited && !retried) {
                    kotlinx.coroutines.delay(2_000)
                    if (token == generation) doLoad(retried = true)
                } else if (token == generation) _state.value = _state.value.copy(loading = false, error = e)
            } catch (e: Exception) {
                if (token == generation) _state.value = _state.value.copy(loading = false, error = SourceError.Network(e))
            }
        }
    }

    companion object {
        fun factory(
            registry: SourceRegistry,
            sourceId: String,
            authorName: String,
            authorUrl: String?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthorViewModel(registry, sourceId, authorName, authorUrl) }
        }
    }
}
