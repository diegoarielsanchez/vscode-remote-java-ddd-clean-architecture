package com.das.mobile.core.ui.paging

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.das.mobile.core.ui.components.EmptyBox
import com.das.mobile.core.ui.components.ErrorBox
import com.das.mobile.core.ui.components.LoadingBox
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagedListState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

/**
 * Page-number pagination for the backend `POST .../list?page=N&pageSize=M` endpoints
 * (pages start at 1, pageSize is capped at 100 server side).
 * Subclasses call [refresh] from their own `init` block.
 */
abstract class PagedListViewModel<T>(private val pageSize: Int = 20) : ViewModel() {

    private val _state = MutableStateFlow(PagedListState<T>())
    val state: StateFlow<PagedListState<T>> = _state.asStateFlow()

    private var nextPage = 1
    private var job: Job? = null

    protected abstract suspend fun fetch(page: Int, pageSize: Int): Result<List<T>>

    fun refresh() {
        job?.cancel()
        nextPage = 1
        _state.update {
            it.copy(isLoading = it.items.isEmpty(), isRefreshing = it.items.isNotEmpty(), endReached = false, error = null)
        }
        job = load(reset = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isRefreshing || s.endReached || s.error != null) return
        _state.update { it.copy(isLoading = true) }
        job = load(reset = false)
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        if (_state.value.items.isEmpty()) refresh() else loadMore()
    }

    private fun load(reset: Boolean) = viewModelScope.launch {
        fetch(nextPage, pageSize)
            .onSuccess { batch ->
                nextPage++
                _state.update {
                    it.copy(
                        items = if (reset) batch else it.items + batch,
                        isLoading = false,
                        isRefreshing = false,
                        endReached = batch.size < pageSize,
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PagedList(
    state: PagedListState<T>,
    emptyText: String,
    key: (T) -> Any,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    when {
        state.items.isEmpty() && state.isLoading -> LoadingBox(modifier)
        state.items.isEmpty() && state.error != null -> ErrorBox(state.error, onRetry, modifier)
        else -> PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
            if (state.items.isEmpty()) {
                EmptyBox(emptyText)
            } else {
                val listState = rememberLazyListState()
                val nearEnd by remember {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= listState.layoutInfo.totalItemsCount - 4
                    }
                }
                LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) onLoadMore() }

                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = key) { item ->
                        itemContent(item)
                        HorizontalDivider()
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            when {
                                state.isLoading -> CircularProgressIndicator()
                                state.error != null -> TextButton(onClick = onRetry) { Text("${state.error} — tap to retry") }
                            }
                        }
                    }
                }
            }
        }
    }
}
