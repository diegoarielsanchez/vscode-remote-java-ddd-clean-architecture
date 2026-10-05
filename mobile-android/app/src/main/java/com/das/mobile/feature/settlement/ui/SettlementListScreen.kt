package com.das.mobile.feature.settlement.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.das.mobile.core.data.ChangeNotifier
import com.das.mobile.core.data.DataChange
import com.das.mobile.core.ui.paging.PagedList
import com.das.mobile.core.ui.paging.PagedListViewModel
import com.das.mobile.core.util.UiFormats
import com.das.mobile.feature.msr.data.MsrRepository
import com.das.mobile.feature.settlement.data.Settlement
import com.das.mobile.feature.settlement.data.SettlementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

data class SettlementRow(val settlement: Settlement, val msrName: String?)

@HiltViewModel
class SettlementListViewModel @Inject constructor(
    private val settlements: SettlementRepository,
    private val msrs: MsrRepository,
    changes: ChangeNotifier,
) : PagedListViewModel<SettlementRow>() {

    init {
        refresh()
        viewModelScope.launch { changes.changes.filter { it == DataChange.SETTLEMENT }.collect { refresh() } }
    }

    override suspend fun fetch(page: Int, pageSize: Int): Result<List<SettlementRow>> =
        settlements.list(page, pageSize).map { list ->
            val names = msrs.names()
            list.map { SettlementRow(it, names[it.medicalSalesRepId]) }
        }
}

@Composable
fun SettlementListScreen(vm: SettlementListViewModel, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    PagedList(
        state = state,
        emptyText = "No settlements. Tap + to create one.",
        key = { it.settlement.id },
        onLoadMore = vm::loadMore,
        onRefresh = vm::refresh,
        onRetry = vm::retry,
        modifier = modifier,
    ) { row ->
        val s = row.settlement
        ListItem(
            overlineContent = { Text(listOfNotNull(UiFormats.date(s.date), s.status).joinToString(" · ")) },
            headlineContent = { Text(s.description) },
            supportingContent = {
                Text(listOfNotNull(row.msrName, "${s.invoices.size} invoice(s)").joinToString(" · "))
            },
            trailingContent = { Text(UiFormats.money(s.totalAmount), style = MaterialTheme.typography.titleMedium) },
            modifier = Modifier.clickable { onOpen(s.id) },
        )
    }
}
