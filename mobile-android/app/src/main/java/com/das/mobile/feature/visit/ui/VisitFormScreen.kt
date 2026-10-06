package com.das.mobile.feature.visit.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.das.mobile.core.ui.picker.PickerItem
import com.das.mobile.feature.hcp.data.HcpRepository
import com.das.mobile.feature.hcp.data.toPickerItem
import com.das.mobile.feature.hcp.ui.HcpPickerField
import com.das.mobile.feature.msr.data.MsrRepository
import com.das.mobile.feature.msr.data.toPickerItem
import com.das.mobile.feature.msr.ui.MsrPickerField
import com.das.mobile.feature.visit.data.VisitDraft
import com.das.mobile.feature.visit.data.VisitRepository
import com.das.mobile.navigation.VisitFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Field error keys match the backend DTO property names, so server validation maps 1:1. */
data class VisitFormUiState(
    val id: String? = null,
    val loading: Boolean = false,
    val loadError: String? = null,
    val date: LocalDate = LocalDate.now(),
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
class VisitFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val visits: VisitRepository,
    private val hcps: HcpRepository,
    private val msrs: MsrRepository,
) : ViewModel() {

    private val visitId: String? = savedStateHandle.toRoute<VisitFormRoute>().id
    private val _state = MutableStateFlow(VisitFormUiState(id = visitId, loading = visitId != null))
    val state = _state.asStateFlow()

    init { if (visitId != null) load(visitId) }

    fun load(id: String = visitId!!) {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            visits.get(id)
                .onSuccess { v ->
                    _state.update {
                        it.copy(
                            loading = false,
                            date = v.date ?: LocalDate.now(),
                            hcp = v.healthCareProfId?.let { hid -> hcps.get(hid).getOrNull()?.toPickerItem() ?: PickerItem(hid, hid) },
                            msr = v.medicalSalesRepId?.let { mid -> msrs.get(mid).getOrNull()?.toPickerItem() ?: PickerItem(mid, mid) },
                            site = v.visitSiteId.orEmpty(),
                            comments = v.comments.orEmpty(),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e.message) } }
        }
    }

    fun onDate(v: LocalDate) = _state.update { it.copy(date = v, fieldErrors = it.fieldErrors - "visitDate") }
    fun onHcp(v: PickerItem) = _state.update { it.copy(hcp = v, fieldErrors = it.fieldErrors - "healthCareProfId") }
    fun onMsr(v: PickerItem) = _state.update { it.copy(msr = v, fieldErrors = it.fieldErrors - "medicalSalesRepId") }
    fun onSite(v: String) = _state.update { it.copy(site = v, fieldErrors = it.fieldErrors - "visitSiteId") }
    fun onComments(v: String) = _state.update { it.copy(comments = v) }

    fun save() {
        val s = _state.value
        val errors = buildMap {
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
            visits.save(VisitDraft(s.id, s.date, s.hcp!!.id, s.msr!!.id, s.site.trim(), s.comments))
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
fun VisitFormScreen(onClose: () -> Unit, vm: VisitFormViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    FormScaffold(
        title = if (s.id == null) "New visit" else "Edit visit",
        loading = s.loading,
        loadError = s.loadError,
        saving = s.saving,
        saved = s.saved,
        onSave = vm::save,
        onClose = onClose,
        onRetryLoad = { vm.load() },
    ) {
        DateField("Visit date", s.date, vm::onDate, Modifier.fillMaxWidth(), s.fieldErrors["visitDate"])
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
