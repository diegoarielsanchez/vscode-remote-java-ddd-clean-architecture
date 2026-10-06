package com.das.mobile.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.das.mobile.feature.settlement.ui.SettlementListScreen
import com.das.mobile.feature.settlement.ui.SettlementListViewModel
import com.das.mobile.feature.visit.ui.VisitListScreen
import com.das.mobile.feature.visit.ui.VisitListViewModel
import com.das.mobile.feature.visit.ui.VisitPlanListScreen
import com.das.mobile.feature.visit.ui.VisitPlanListViewModel

private enum class HomeTab(val label: String, val icon: ImageVector) {
    VISITS("Visits", Icons.AutoMirrored.Filled.List),
    PLANS("Plans", Icons.Default.DateRange),
    SETTLEMENTS("Settlements", Icons.Default.ShoppingCart),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    username: String,
    onLogout: () -> Unit,
    onOpenVisit: (String) -> Unit,
    onNewVisit: () -> Unit,
    onOpenVisitPlan: (String) -> Unit,
    onNewVisitPlan: () -> Unit,
    onOpenSettlement: (String) -> Unit,
    onNewSettlement: () -> Unit,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = HomeTab.entries[tabIndex]

    // Scoped to the Home back-stack entry, so each tab keeps its list across tab switches.
    val visitsVm: VisitListViewModel = hiltViewModel()
    val plansVm: VisitPlanListViewModel = hiltViewModel()
    val settlementsVm: SettlementListViewModel = hiltViewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.label) },
                actions = {
                    Text(username)
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when (tab) {
                    HomeTab.VISITS -> onNewVisit()
                    HomeTab.PLANS -> onNewVisitPlan()
                    HomeTab.SETTLEMENTS -> onNewSettlement()
                }
            }) { Icon(Icons.Default.Add, "New") }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            HomeTab.VISITS -> VisitListScreen(visitsVm, onOpenVisit, modifier)
            HomeTab.PLANS -> VisitPlanListScreen(plansVm, onOpenVisitPlan, modifier)
            HomeTab.SETTLEMENTS -> SettlementListScreen(settlementsVm, onOpenSettlement, modifier)
        }
    }
}
