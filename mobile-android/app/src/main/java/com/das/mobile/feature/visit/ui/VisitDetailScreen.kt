package com.das.mobile.feature.visit.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.das.mobile.core.ui.UiState
import com.das.mobile.core.ui.components.ErrorBox
import com.das.mobile.core.ui.components.LoadingBox
import com.das.mobile.core.util.UiFormats
import com.das.mobile.feature.hcp.data.HcpRepository
import com.das.mobile.feature.msr.data.MsrRepository
import com.das.mobile.feature.visit.data.VisitRepository
import com.das.mobile.navigation.VisitDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisitDetailUiState(
    val content: UiState<VisitRow> = UiState.Loading,
    val uploading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class VisitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val visits: VisitRepository,
    private val hcps: HcpRepository,
    private val msrs: MsrRepository,
    private val documents: DocumentReader,
    changes: ChangeNotifier,
) : ViewModel() {

    val visitId: String = savedStateHandle.toRoute<VisitDetailRoute>().id

    private val _state = MutableStateFlow(VisitDetailUiState())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { changes.changes.filter { it == DataChange.VISIT }.collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            val result = visits.get(visitId).map { v ->
                VisitRow(
                    visit = v,
                    hcpName = v.healthCareProfId?.let { hcps.get(it).getOrNull()?.fullName },
                    msrName = v.medicalSalesRepId?.let { msrs.get(it).getOrNull()?.fullName },
                )
            }
            _state.update { s ->
                result.fold(
                    { s.copy(content = UiState.Success(it)) },
                    // keep showing stale content on a background refresh failure
                    { e -> if (s.content is UiState.Success) s.copy(message = e.message) else s.copy(content = UiState.Error(e.message.orEmpty())) },
                )
            }
        }
    }

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            val files = uris.map { documents.read(it) }
            val failed = files.firstOrNull { it.isFailure }?.exceptionOrNull()
            if (failed != null) {
                _state.update { it.copy(uploading = false, message = failed.message) }
                return@launch
            }
            visits.uploadAttachments(visitId, files.map { it.getOrThrow() })
                .onSuccess { list -> _state.update { it.copy(uploading = false, message = "Uploaded ${list.size} file(s)") } }
                .onFailure { e -> _state.update { it.copy(uploading = false, message = e.message) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    vm: VisitDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.upload(it) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visit") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { onEdit(vm.visitId) }) { Icon(Icons.Default.Edit, "Edit") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!state.uploading) picker.launch(DocumentReader.PICKER_MIME_TYPES) },
                icon = {
                    if (state.uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Add, null)
                },
                text = { Text("Attach promo files") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val content = state.content) {
            UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(content.message, vm::load, Modifier.padding(padding))
            is UiState.Success -> {
                val row = content.data
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    ListItem(overlineContent = { Text("Date") }, headlineContent = { Text(UiFormats.date(row.visit.date)) })
                    ListItem(
                        overlineContent = { Text("Healthcare professional") },
                        headlineContent = { Text(row.hcpName ?: row.visit.healthCareProfId.orEmpty()) },
                    )
                    ListItem(
                        overlineContent = { Text("Medical sales rep") },
                        headlineContent = { Text(row.msrName ?: row.visit.medicalSalesRepId.orEmpty()) },
                    )
                    ListItem(overlineContent = { Text("Site") }, headlineContent = { Text(row.visit.visitSiteId ?: "—") })
                    ListItem(overlineContent = { Text("Comments") }, headlineContent = { Text(row.visit.comments ?: "—") })
                    HorizontalDivider()
                    Text(
                        "Product promo attachments",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                    if (row.visit.attachments.isEmpty()) {
                        Text("None yet.", modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    row.visit.attachments.forEach { a ->
                        ListItem(
                            headlineContent = { Text(a.fileName) },
                            supportingContent = a.sha256Hash?.let { { Text("SHA-256 ${it.take(16)}…") } },
                        )
                    }
                }
            }
        }
    }
}
