package com.das.mobile.core.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.das.mobile.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class Session(val token: String, val username: String, val roles: List<String>)

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val session: Session) : SessionState
}

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * Holds the JWT issued by identity-service (POST /auth/login).
 *
 * The backend has no refresh endpoint, so when the gateway answers 401 the session is cleared
 * and the UI falls back to the login screen.
 *
 * TODO(prod): the token is stored in plain DataStore; encrypt it (e.g. Tink + Android Keystore).
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val keyToken = stringPreferencesKey("token")
    private val keyUsername = stringPreferencesKey("username")
    private val keyRoles = stringPreferencesKey("roles")

    val state: StateFlow<SessionState> = context.sessionDataStore.data
        .map { prefs ->
            val token = prefs[keyToken]
            if (token == null) {
                SessionState.LoggedOut
            } else {
                SessionState.LoggedIn(
                    Session(
                        token = token,
                        username = prefs[keyUsername].orEmpty(),
                        roles = prefs[keyRoles].orEmpty().split(',').filter { it.isNotBlank() },
                    )
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    /** Read synchronously by the OkHttp interceptor. */
    val token: String?
        get() = (state.value as? SessionState.LoggedIn)?.session?.token

    suspend fun save(session: Session) {
        context.sessionDataStore.edit { prefs ->
            prefs[keyToken] = session.token
            prefs[keyUsername] = session.username
            prefs[keyRoles] = session.roles.joinToString(",")
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
