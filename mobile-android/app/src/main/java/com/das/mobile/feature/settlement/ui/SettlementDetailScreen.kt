package com.das.mobile.feature.settlement.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.das.mobile.core.data.ChangeNotifier
import com.das.mobile.core.data.DataChange
import com.das.mobile.core.files.DocumentReader
import com.das.mobile.core.files.UploadFile
import com.das.mobile.core.network.ApiError
import com.das.mobile.core.ui.UiState
import com.das.mobile.core.ui.components.DateField
import com.das.mobile.core.ui.components.ErrorBox
import com.das.mobile.core.ui.components.InlineError
import com.das.mobile.core.ui.components.LoadingBox
import com.das.mobile.core.util.UiFormats
import com.das.mobile.feature.msr.data.MsrRepository
import com.das.mobile.feature.settlement.data.Invoice
import com.das.mobile.feature.settlement.data.InvoiceDraft
import com.das.mobile.feature.settlement.data.SettlementRepository
import com.das.mobile.navigation.SettlementDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the "add invoice" sheet; null when the sheet is closed. */
data class InvoiceFormState(
    val number: String = "",
    val issueDate: LocalDate = LocalDate.now(),
    val dueDate: LocalDate? = null,
    val amount: String = "",
    val file: UploadFile? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val saving: Boolean = false,
)

data class SettlementDetailUiState(
    val content: UiState<SettlementRow> = UiState.Loading,
    val invoiceForm: InvoiceFormState? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettlementDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settlements: SettlementRepository,
    private val msrs: MsrRepository,
    private val documents: DocumentReader,
    changes: ChangeNotifier,
) : ViewModel() {

    val settlementId: String = savedStateHandle.toRoute<SettlementDetailRoute>().id

    private val _state = MutableStateFlow(SettlementDetailUiState())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { changes.changes.filter { it == DataChange.SETTLEMENT }.collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            val result = settlements.get(settlementId).map { s ->
                SettlementRow(s, msrs.get(s.medicalSalesRepId).getOrNull()?.fullName)
            }
            _state.update { st ->
                result.fold(
                    { st.copy(content = UiState.Success(it)) },
                    { e -> if (st.content is UiState.Success) st.copy(message = e.message) else st.copy(content = UiState.Error(e.message.orEmpty())) },
                )
            }
        }
    }

    // ── Add invoice ────────────────────────────────────────────────────────────
    fun openInvoiceForm() = _state.update { it.copy(invoiceForm = InvoiceFormState()) }
    fun closeInvoiceForm() = _state.update { it.copy(invoiceForm = null) }
    private fun form(block: (InvoiceFormState) -> InvoiceFormState) =
        _state.update { s -> s.copy(invoiceForm = s.invoiceForm?.let(block)) }

    fun onNumber(v: String) = form { it.copy(number = v, fieldErrors = it.fieldErrors - "invoiceNumber") }
    fun onIssueDate(v: LocalDate) = form { it.copy(issueDate = v) }
    fun onDueDate(v: LocalDate) = form { it.copy(dueDate = v) }
    fun onAmount(v: String) = form { it.copy(amount = v.replace(',', '.'), fieldErrors = it.fieldErrors - "amount") }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            documents.read(uri)
                .onSuccess { f -> form { it.copy(file = f, fieldErrors = it.fieldErrors - "file") } }
                .onFailure { e -> form { it.copy(fieldErrors = it.fieldErrors + ("file" to e.message.orEmpty())) } }
        }
    }

    fun submitInvoice() {
        val f = _state.value.invoiceForm ?: return
        val amount = f.amount.toBigDecimalOrNull()
        val errors = buildMap {
            if (f.number.isBlank()) put("invoiceNumber", "Required")
            if (amount == null || amount.signum() < 0) put("amount", "Enter a valid amount")
            if (f.file == null) put("file", "Attach the invoice file")
            if (f.dueDate != null && f.dueDate.isBefore(f.issueDate)) put("dueDate", "Must be on or after the issue date")
        }
        if (errors.isNotEmpty()) {
            form { it.copy(fieldErrors = errors) }
            return
        }
        form { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            settlements.addInvoice(settlementId, InvoiceDraft(f.number.trim(), f.issueDate, f.dueDate, amount!!, f.file!!))
                .onSuccess { _state.update { it.copy(invoiceForm = null, message = "Invoice added") } }
                .onFailure { e ->
                    form {
                        if (e is ApiError.Validation) it.copy(saving = false, fieldErrors = e.fields)
                        else it.copy(saving = false, error = e.message)
                    }
                }
        }
    }

    // ── Remove invoice ─────────────────────────────────────────────────────────
    fun removeInvoice(invoiceId: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            settlements.removeInvoice(settlementId, invoiceId)
                .onSuccess { _state.update { it.copy(busy = false, message = "Invoice removed") } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = e.message) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    vm: SettlementDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    val loaded = (state.content as? UiState.Success)?.data
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settlement") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (loaded?.settlement?.canEditHeader == true) {
                        IconButton(onClick = { onEdit(vm.settlementId) }) { Icon(Icons.Default.Edit, "Edit") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (loaded != null) {
                ExtendedFloatingActionButton(
                    onClick = vm::openInvoiceForm,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add invoice") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val content = state.content) {
            UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(content.message, vm::load, Modifier.padding(padding))
            is UiState.Success -> {
                val s = content.data.settlement
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        ListItem(
                            overlineContent = { Text(listOfNotNull(UiFormats.date(s.date), s.status).joinToString(" · ")) },
                            headlineContent = { Text(s.description, style = MaterialTheme.typography.titleLarge) },
                            supportingContent = { Text(content.data.msrName ?: s.medicalSalesRepId) },
                            trailingContent = { Text(UiFormats.money(s.totalAmount), style = MaterialTheme.typography.titleLarge) },
                        )
                        if (!s.canEditHeader) {
                            Text(
                                "Remove all invoices to edit the settlement details.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        Text("Invoices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                        if (s.invoices.isEmpty()) Text("No invoices yet.", modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    items(s.invoices, key = { it.id }) { inv ->
                        InvoiceRow(inv, enabled = !state.busy, onRemove = { confirmRemove = inv.id })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    confirmRemove?.let { invoiceId ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove invoice?") },
            text = { Text("The invoice and its uploaded file will be deleted.") },
            confirmButton = { TextButton(onClick = { vm.removeInvoice(invoiceId); confirmRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }

    state.invoiceForm?.let { form -> AddInvoiceSheet(form, vm) }
}

@Composable
private fun InvoiceRow(inv: Invoice, enabled: Boolean, onRemove: () -> Unit) {
    ListItem(
        overlineContent = {
            Text(listOfNotNull("Issued ${UiFormats.date(inv.issueDate)}", inv.dueDate?.let { "due ${UiFormats.date(it)}" }, inv.status).joinToString(" · "))
        },
        headlineContent = { Text("#${inv.number} — ${UiFormats.money(inv.amount)}") },
        supportingContent = inv.fileName?.let { name ->
            { Text(listOfNotNull(name, inv.sizeInBytes?.let { "${it / 1024} KB" }).joinToString(" · ")) }
        },
        trailingContent = {
            IconButton(onClick = onRemove, enabled = enabled) { Icon(Icons.Default.Delete, "Remove invoice") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInvoiceSheet(form: InvoiceFormState, vm: SettlementDetailViewModel) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.onFilePicked(it) }
    ModalBottomSheet(
        onDismissRequest = { if (!form.saving) vm.closeInvoiceForm() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add invoice", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = form.number,
                onValueChange = vm::onNumber,
                label = { Text("Invoice number") },
                isError = form.fieldErrors["invoiceNumber"] != null,
                supportingText = form.fieldErrors["invoiceNumber"]?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Issue date", form.issueDate, vm::onIssueDate, Modifier.weight(1f), form.fieldErrors["issueDate"])
                DateField("Due date (optional)", form.dueDate, vm::onDueDate, Modifier.weight(1f), form.fieldErrors["dueDate"])
            }
            OutlinedTextField(
                value = form.amount,
                onValueChange = vm::onAmount,
                label = { Text("Amount") },
                isError = form.fieldErrors["amount"] != null,
                supportingText = form.fieldErrors["amount"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { filePicker.launch(DocumentReader.PICKER_MIME_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                Text(form.file?.let { "${it.name} (${it.bytes.size / 1024} KB)" } ?: "Choose file (.pdf .xlsx .docx .txt, max 10 MB)")
            }
            InlineError(form.fieldErrors["file"])
            InlineError(form.error)
            Button(onClick = vm::submitInvoice, enabled = !form.saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (form.saving) "Uploading…" else "Add invoice")
            }
        }
    }
}
