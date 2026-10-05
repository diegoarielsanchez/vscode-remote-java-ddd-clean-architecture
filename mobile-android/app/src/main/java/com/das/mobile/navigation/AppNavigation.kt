package com.das.mobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.das.mobile.core.session.SessionState
import com.das.mobile.core.session.SessionStore
import com.das.mobile.core.ui.components.LoadingBox
import com.das.mobile.feature.auth.data.AuthRepository
import com.das.mobile.feature.auth.ui.LoginScreen
import com.das.mobile.feature.settlement.ui.SettlementDetailScreen
import com.das.mobile.feature.settlement.ui.SettlementFormScreen
import com.das.mobile.feature.visit.ui.VisitDetailScreen
import com.das.mobile.feature.visit.ui.VisitFormScreen
import com.das.mobile.feature.visit.ui.VisitPlanFormScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    sessionStore: SessionStore,
    private val auth: AuthRepository,
) : ViewModel() {
    val session = sessionStore.state
    fun logout() { viewModelScope.launch { auth.logout() } }
}

/**
 * Session-driven root: logged out -> login; logged in -> main graph.
 * A 401 from the gateway clears the session (AuthInterceptor), which lands here on the login screen.
 */
@Composable
fun AppRoot(vm: RootViewModel = hiltViewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    when (val s = session) {
        SessionState.Loading -> LoadingBox()
        SessionState.LoggedOut -> LoginScreen()
        is SessionState.LoggedIn -> MainNavGraph(username = s.session.username, onLogout = vm::logout)
    }
}

@Composable
private fun MainNavGraph(username: String, onLogout: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                username = username,
                onLogout = onLogout,
                onOpenVisit = { nav.navigate(VisitDetailRoute(it)) },
                onNewVisit = { nav.navigate(VisitFormRoute()) },
                onOpenVisitPlan = { nav.navigate(VisitPlanFormRoute(it)) },
                onNewVisitPlan = { nav.navigate(VisitPlanFormRoute()) },
                onOpenSettlement = { nav.navigate(SettlementDetailRoute(it)) },
                onNewSettlement = { nav.navigate(SettlementFormRoute()) },
            )
        }
        composable<VisitDetailRoute> {
            VisitDetailScreen(onBack = nav::popBackStack, onEdit = { nav.navigate(VisitFormRoute(it)) })
        }
        composable<VisitFormRoute> {
            VisitFormScreen(onClose = nav::popBackStack)
        }
        composable<VisitPlanFormRoute> {
            VisitPlanFormScreen(onClose = nav::popBackStack)
        }
        composable<SettlementDetailRoute> {
            SettlementDetailScreen(onBack = nav::popBackStack, onEdit = { nav.navigate(SettlementFormRoute(it)) })
        }
        composable<SettlementFormRoute> {
            SettlementFormScreen(
                onClose = nav::popBackStack,
                // replace the "new settlement" form with the created settlement's detail
                onCreated = { id ->
                    nav.navigate(SettlementDetailRoute(id)) {
                        popUpTo<SettlementFormRoute> { inclusive = true }
                    }
                },
            )
        }
    }
}
