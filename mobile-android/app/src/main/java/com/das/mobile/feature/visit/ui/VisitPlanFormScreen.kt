package com.das.mobile.feature.visit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.das.mobile.core.network.ApiError
import com.das.mobile.core.ui.components.DateField
import com.das.mobile.core.ui.components.FormScaffold
import com.das.mobile.core.ui.components.InlineError
import com.das.mobile.core.ui.components.TimeField
import com.das.mobile.core.ui.picker.PickerItem
import com.das.mobile.feature.hcp.data.HcpRepository
import com.das.mobile.feature.hcp.data.toPickerItem
import com.das.mobile.feature.hcp.ui.HcpPickerField
import com.das.mobile.feature.msr.data.MsrRepository
import com.das.mobile.feature.msr.data.toPickerItem
import com.das.mobile.feature.msr.ui.MsrPickerField
import com.das.mobile.feature.visit.data.VisitPlanDraft
import com.das.mobile.feature.visit.data.VisitPlanRepository
import com.das.mobile.navigation.VisitPlanFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisitPlanFormUiState(
    val id: String? = null,
    val loading: Boolean = false,
    val loadError: String? = null,
    val date: LocalDate = LocalDate.now().plusDays(1),
    val time: LocalTime = LocalTime.of(9, 0),
    val hcp: PickerItem? = null,
    val msr: PickerItem? = null,
    val site: String = "",
    val comments: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class VisitPlanFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plans: VisitPlanRepository,
    private val hcps: HcpRepository,
    private val msrs: MsrRepository,
) : ViewModel() {

    private val planId: String? = savedStateHandle.toRoute<VisitPlanFormRoute>().id
    private val _state = MutableStateFlow(VisitPlanFormUiState(id = planId, loading = planId != null))
    val state = _state.asStateFlow()

    init { if (planId != null) load(planId) }

    fun load(id: String = planId!!) {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            plans.get(id)
                .onSuccess { p ->
                    _state.update {
                        it.copy(
                            loading = false,
                            date = p.dateTime?.toLocalDate() ?: it.date,
                            time = p.dateTime?.toLocalTime() ?: it.time,
                            hcp = p.healthCareProfId?.let { hid -> hcps.get(hid).getOrNull()?.toPickerItem() ?: PickerItem(hid, hid) },
                            msr = p.medicalSalesRepId?.let { mid -> msrs.get(mid).getOrNull()?.toPickerItem() ?: PickerItem(mid, mid) },
                            site = p.visitSiteId.orEmpty(),
                            comments = p.comments.orEmpty(),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e.message) } }
        }
    }

    fun onDate(v: LocalDate) = _state.update { it.copy(date = v, fieldErrors = it.fieldErrors - "visitDateTime") }
    fun onTime(v: LocalTime) = _state.update { it.copy(time = v, fieldErrors = it.fieldErrors - "visitDateTime") }
    fun onHcp(v: PickerItem) = _state.update { it.copy(hcp = v, fieldErrors = it.fieldErrors - "healthCareProfId") }
    fun onMsr(v: PickerItem) = _state.update { it.copy(msr = v, fieldErrors = it.fieldErrors - "medicalSalesRepId") }
    fun onSite(v: String) = _state.update { it.copy(site = v, fieldErrors = it.fieldErrors - "visitSiteId") }
    fun onComments(v: String) = _state.update { it.copy(comments = v) }

    fun save() {
        val s = _state.value
        val dateTime = LocalDateTime.of(s.date, s.time)
        val errors = buildMap {
            // Backend has @Future on visitDateTime (server clock, zone-less).
            if (!dateTime.isAfter(LocalDateTime.now())) put("visitDateTime", "Must be in the future")
            if (s.hcp == null) put("healthCareProfId", "Select a healthcare professional")
            if (s.msr == null) put("medicalSalesRepId", "Select a medical sales rep")
            if (s.site.isBlank()) put("visitSiteId", "Required")
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            plans.save(VisitPlanDraft(s.id, dateTime, s.hcp!!.id, s.msr!!.id, s.site.trim(), s.comments))
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e ->
                    _state.update {
                        if (e is ApiError.Validation) it.copy(saving = false, fieldErrors = e.fields)
                        else it.copy(saving = false, error = e.message)
                    }
                }
        }
    }
}

@Composable
fun VisitPlanFormScreen(onClose: () -> Unit, vm: VisitPlanFormViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    FormScaffold(
        title = if (s.id == null) "New visit plan" else "Edit visit plan",
        loading = s.loading,
        loadError = s.loadError,
        saving = s.saving,
        saved = s.saved,
        onSave = vm::save,
        onClose = onClose,
        onRetryLoad = { vm.load() },
    ) {
        val dtError = s.fieldErrors["visitDateTime"]
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Date", s.date, vm::onDate, Modifier.weight(1f), dtError)
            TimeField("Time", s.time, vm::onTime, Modifier.weight(1f), dtError?.let { " " })
        }
        HcpPickerField(s.hcp, vm::onHcp, Modifier.fillMaxWidth(), s.fieldErrors["healthCareProfId"])
        MsrPickerField(s.msr, vm::onMsr, Modifier.fillMaxWidth(), s.fieldErrors["medicalSalesRepId"])
        OutlinedTextField(
            value = s.site,
            onValueChange = vm::onSite,
            label = { Text("Visit site ID") },
            isError = s.fieldErrors["visitSiteId"] != null,
            supportingText = s.fieldErrors["visitSiteId"]?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.comments,
            onValueChange = vm::onComments,
            label = { Text("Comments") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        InlineError(s.error)
    }
}
