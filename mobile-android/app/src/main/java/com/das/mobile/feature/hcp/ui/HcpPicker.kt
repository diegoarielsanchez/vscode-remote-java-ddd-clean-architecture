package com.das.mobile.feature.hcp.ui

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
import com.das.mobile.feature.hcp.data.HcpRepository
import com.das.mobile.feature.hcp.data.toPickerItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Active HCPs only; specialties become filter chips. */
@HiltViewModel
class HcpPickerViewModel @Inject constructor(private val repo: HcpRepository) : EntityPickerViewModel() {
    init { load() }

    override suspend fun loadItems(forceRefresh: Boolean) =
        repo.all(forceRefresh).map { list ->
            list.filter { it.active }.sortedBy { it.fullName.lowercase() }.map { it.toPickerItem() }
        }
}

@Composable
fun HcpPickerField(
    selected: PickerItem?,
    onSelected: (PickerItem) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    EntityPickerField("Healthcare professional", selected, { open = true }, modifier, error)
    if (open) {
        val vm: HcpPickerViewModel = hiltViewModel()
        EntityPickerSheet(
            title = "Select a healthcare professional",
            viewModel = vm,
            onPick = { onSelected(it); open = false },
            onDismiss = { open = false },
        )
    }
}
