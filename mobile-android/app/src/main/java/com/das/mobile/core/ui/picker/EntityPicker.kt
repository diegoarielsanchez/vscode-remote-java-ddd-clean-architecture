package com.das.mobile.core.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.das.mobile.core.ui.UiState
import com.das.mobile.core.ui.components.ClickableField
import com.das.mobile.core.ui.components.EmptyBox
import com.das.mobile.core.ui.components.ErrorBox
import com.das.mobile.core.ui.components.LoadingBox
import com.das.mobile.core.ui.toUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A selectable row in a picker: an HCP, an MSR, ... */
data class PickerItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val tags: List<String> = emptyList(),
)

data class PickerState(
    val query: String = "",
    val tags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val results: UiState<List<PickerItem>> = UiState.Loading,
)

/**
 * Loads the candidate list once and filters it on the device as the user types.
 * (The HCP/MSR name search endpoints only do exact matches, so server-side search is not
 * useful for type-ahead; see HcpRepository.)
 */
abstract class EntityPickerViewModel : ViewModel() {

    private val all = MutableStateFlow<UiState<List<PickerItem>>>(UiState.Loading)
    private val query = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)

    val state: StateFlow<PickerState> = combine(all, query, selectedTag) { all, q, tag ->
        PickerState(
            query = q,
            tags = (all as? UiState.Success)?.data?.flatMap { it.tags }?.distinct()?.sorted().orEmpty(),
            selectedTag = tag,
            results = when (all) {
                is UiState.Success -> UiState.Success(all.data.filter { it.matches(q, tag) })
                else -> all
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerState())

    protected abstract suspend fun loadItems(forceRefresh: Boolean): Result<List<PickerItem>>

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            all.value = UiState.Loading
            all.value = loadItems(forceRefresh).toUiState()
        }
    }

    fun onQueryChange(value: String) { query.value = value }

    fun onTagSelected(tag: String?) { selectedTag.value = if (selectedTag.value == tag) null else tag }

    private fun PickerItem.matches(q: String, tag: String?): Boolean {
        if (tag != null && tag !in tags) return false
        val needle = q.trim()
        if (needle.isEmpty()) return true
        return title.contains(needle, ignoreCase = true) ||
            subtitle.orEmpty().contains(needle, ignoreCase = true) ||
            tags.any { it.contains(needle, ignoreCase = true) }
    }
}

/** Field that shows the current selection and opens a searchable bottom sheet. */
@Composable
fun EntityPickerField(
    label: String,
    selected: PickerItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    ClickableField(
        value = selected?.title.orEmpty(),
        label = label,
        onClick = onClick,
        modifier = modifier,
        error = error,
        placeholder = "Tap to search",
        trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityPickerSheet(
    title: String,
    viewModel: EntityPickerViewModel,
    onPick: (PickerItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.9f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search by name, email…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.tags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tags) { tag ->
                        FilterChip(
                            selected = tag == state.selectedTag,
                            onClick = { viewModel.onTagSelected(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }
            when (val results = state.results) {
                UiState.Loading -> LoadingBox(Modifier.height(200.dp))
                is UiState.Error -> ErrorBox(results.message, onRetry = { viewModel.load(forceRefresh = true) })
                is UiState.Success ->
                    if (results.data.isEmpty()) {
                        EmptyBox("No matches.")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(results.data, key = { it.id }) { item ->
                                ListItem(
                                    headlineContent = { Text(item.title) },
                                    supportingContent = {
                                        val lines = listOfNotNull(item.subtitle, item.tags.takeIf { it.isNotEmpty() }?.joinToString())
                                        if (lines.isNotEmpty()) Text(lines.joinToString("\n"))
                                    },
                                    modifier = Modifier.clickable { onPick(item) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
            }
        }
    }
}
