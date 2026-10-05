package com.das.mobile.feature.visit.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
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
import com.das.mobile.feature.hcp.data.HcpRepository
import com.das.mobile.feature.msr.data.MsrRepository
import com.das.mobile.feature.visit.data.Visit
import com.das.mobile.feature.visit.data.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** A visit plus the HCP / MSR display names resolved from their directories. */
data class VisitRow(val visit: Visit, val hcpName: String?, val msrName: String?)

@HiltViewModel
class VisitListViewModel @Inject constructor(
    private val visits: VisitRepository,
    private val hcps: HcpRepository,
    private val msrs: MsrRepository,
    changes: ChangeNotifier,
) : PagedListViewModel<VisitRow>() {

    init {
        refresh()
        viewModelScope.launch { changes.changes.filter { it == DataChange.VISIT }.collect { refresh() } }
    }

    override suspend fun fetch(page: Int, pageSize: Int): Result<List<VisitRow>> =
        visits.list(page, pageSize).map { list ->
            val hcpNames = hcps.names()
            val msrNames = msrs.names()
            list.map { VisitRow(it, hcpNames[it.healthCareProfId], msrNames[it.medicalSalesRepId]) }
        }
}

@Composable
fun VisitListScreen(vm: VisitListViewModel, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    PagedList(
        state = state,
        emptyText = "No visits yet. Tap + to record one.",
        key = { it.visit.id },
        onLoadMore = vm::loadMore,
        onRefresh = vm::refresh,
        onRetry = vm::retry,
        modifier = modifier,
    ) { row ->
        ListItem(
            overlineContent = { Text(UiFormats.date(row.visit.date)) },
            headlineContent = { Text(row.hcpName ?: row.visit.healthCareProfId ?: "Unknown HCP") },
            supportingContent = {
                Text(listOfNotNull(row.msrName, row.visit.visitSiteId?.let { "Site $it" }).joinToString(" · "))
            },
            trailingContent = {
                if (row.visit.attachments.isNotEmpty()) Text("${row.visit.attachments.size} files")
            },
            modifier = Modifier.clickable { onOpen(row.visit.id) },
        )
    }
}
