package com.das.mobile.feature.settlement.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.das.mobile.feature.msr.data.MsrRepository
import com.das.mobile.feature.msr.data.toPickerItem
import com.das.mobile.feature.msr.ui.MsrPickerField
import com.das.mobile.feature.settlement.data.SettlementHeader
import com.das.mobile.feature.settlement.data.SettlementRepository
import com.das.mobile.navigation.SettlementFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettlementFormUiState(
    val id: String? = null,
    val loading: Boolean = false,
    val loadError: String? = null,
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val msr: PickerItem? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val saving: Boolean = false,
    /** Id of the saved settlement; the screen navigates to its detail to add invoices. */
    val savedId: String? = null,
)

@HiltViewModel
class SettlementFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settlements: SettlementRepository,
    private val msrs: MsrRepository,
) : ViewModel() {

    private val settlementId: String? = savedStateHandle.toRoute<SettlementFormRoute>().id
    private val _state = MutableStateFlow(SettlementFormUiState(id = settlementId, loading = settlementId != null))
    val state = _state.asStateFlow()

    init { if (settlementId != null) load(settlementId) }

    fun load(id: String = settlementId!!) {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            settlements.get(id)
                .onSuccess { s ->
                    if (!s.canEditHeader) {
                        _state.update { it.copy(loading = false, loadError = "Remove all invoices before editing this settlement.") }
                        return@onSuccess
                    }
                    val msr = msrs.get(s.medicalSalesRepId).getOrNull()?.toPickerItem()
                        ?: PickerItem(s.medicalSalesRepId, s.medicalSalesRepId)
                    _state.update {
                        it.copy(loading = false, description = s.description, date = s.date ?: it.date, msr = msr)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e.message) } }
        }
    }

    fun onDescription(v: String) = _state.update { it.copy(description = v, fieldErrors = it.fieldErrors - "description") }
    fun onDate(v: LocalDate) = _state.update { it.copy(date = v, fieldErrors = it.fieldErrors - "settlementDate") }
    fun onMsr(v: PickerItem) = _state.update { it.copy(msr = v, fieldErrors = it.fieldErrors - "medicalSalesRepId") }

    fun save() {
        val s = _state.value
        val errors = buildMap {
            if (s.description.isBlank()) put("description", "Required")
            if (s.msr == null) put("medicalSalesRepId", "Select a medical sales rep")
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val header = SettlementHeader(s.id, s.description.trim(), s.date, s.msr!!.id)
            val result = if (s.id == null) settlements.create(header) else settlements.updateHeader(header)
            result
                .onSuccess { saved -> _state.update { it.copy(saving = false, savedId = saved.id) } }
                .onFailure { e ->
                    _state.update {
                        if (e is ApiError.Validation) it.copy(saving = false, fieldErrors = e.fields)
                        else it.copy(saving = false, error = e.message)
                    }
                }
        }
    }
}

/**
 * Creates or edits the settlement header. Invoices are added from the detail screen because each
 * one needs its file uploaded through POST /settlement/invoice/add.
 */
@Composable
fun SettlementFormScreen(
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
    vm: SettlementFormViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val isNew = s.id == null
    FormScaffold(
        title = if (isNew) "New settlement" else "Edit settlement",
        loading = s.loading,
        loadError = s.loadError,
        saving = s.saving,
        saved = s.savedId != null && !isNew,
        onSave = vm::save,
        onClose = onClose,
        onRetryLoad = { vm.load() },
    ) {
        LaunchedEffect(s.savedId) { if (isNew) s.savedId?.let(onCreated) }

        OutlinedTextField(
            value = s.description,
            onValueChange = vm::onDescription,
            label = { Text("Description") },
            isError = s.fieldErrors["description"] != null,
            supportingText = s.fieldErrors["description"]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        DateField("Settlement date", s.date, vm::onDate, Modifier.fillMaxWidth(), s.fieldErrors["settlementDate"])
        MsrPickerField(s.msr, vm::onMsr, Modifier.fillMaxWidth(), s.fieldErrors["medicalSalesRepId"])
        if (isNew) {
            Text(
                "After saving you'll be taken to the settlement to add invoices with their files.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        InlineError(s.error)
    }
}
