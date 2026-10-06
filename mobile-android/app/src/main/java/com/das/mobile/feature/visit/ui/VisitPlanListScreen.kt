package com.das.mobile.feature.visit.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.AssistChip
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
import com.das.mobile.feature.visit.data.VisitPlan
import com.das.mobile.feature.visit.data.VisitPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

data class VisitPlanRow(val plan: VisitPlan, val hcpName: String?, val msrName: String?)

@HiltViewModel
class VisitPlanListViewModel @Inject constructor(
    private val plans: VisitPlanRepository,
    private val hcps: HcpRepository,
    private val msrs: MsrRepository,
    changes: ChangeNotifier,
) : PagedListViewModel<VisitPlanRow>() {

    init {
        refresh()
        viewModelScope.launch { changes.changes.filter { it == DataChange.VISIT_PLAN }.collect { refresh() } }
    }

    override suspend fun fetch(page: Int, pageSize: Int): Result<List<VisitPlanRow>> =
        plans.list(page, pageSize).map { list ->
            val hcpNames = hcps.names()
            val msrNames = msrs.names()
            list.map { VisitPlanRow(it, hcpNames[it.healthCareProfId], msrNames[it.medicalSalesRepId]) }
        }
}

@Composable
fun VisitPlanListScreen(vm: VisitPlanListViewModel, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    PagedList(
        state = state,
        emptyText = "No visit plans. Tap + to schedule one.",
        key = { it.plan.id },
        onLoadMore = vm::loadMore,
        onRefresh = vm::refresh,
        onRetry = vm::retry,
        modifier = modifier,
    ) { row ->
        ListItem(
            overlineContent = { Text(UiFormats.dateTime(row.plan.dateTime)) },
            headlineContent = { Text(row.hcpName ?: row.plan.healthCareProfId ?: "Unknown HCP") },
            supportingContent = {
                Text(listOfNotNull(row.msrName, row.plan.visitSiteId?.let { "Site $it" }).joinToString(" · "))
            },
            trailingContent = {
                AssistChip(onClick = {}, label = { Text(if (row.plan.active) "Active" else "Inactive") })
            },
            modifier = Modifier.clickable { onOpen(row.plan.id) },
        )
    }
}
