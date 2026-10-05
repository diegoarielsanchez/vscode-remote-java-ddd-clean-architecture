package com.das.mobile.feature.msr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.das.mobile.core.ui.picker.EntityPickerField
import com.das.mobile.core.ui.picker.EntityPickerSheet
import com.das.mobile.core.ui.picker.EntityPickerViewModel
import com.das.mobile.core.ui.picker.PickerItem
import com.das.mobile.feature.msr.data.MsrRepository
import com.das.mobile.feature.msr.data.toPickerItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Active reps only (visit-service and settlement-service reject inactive ones). */
@HiltViewModel
class MsrPickerViewModel @Inject constructor(private val repo: MsrRepository) : EntityPickerViewModel() {
    init { load() }

    override suspend fun loadItems(forceRefresh: Boolean) =
        repo.all(forceRefresh).map { list ->
            list.filter { it.active }.sortedBy { it.fullName.lowercase() }.map { it.toPickerItem() }
        }
}

@Composable
fun MsrPickerField(
    selected: PickerItem?,
    onSelected: (PickerItem) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    EntityPickerField("Medical sales rep", selected, { open = true }, modifier, error)
    if (open) {
        val vm: MsrPickerViewModel = hiltViewModel()
        EntityPickerSheet(
            title = "Select a medical sales rep",
            viewModel = vm,
            onPick = { onSelected(it); open = false },
            onDismiss = { open = false },
        )
    }
}
