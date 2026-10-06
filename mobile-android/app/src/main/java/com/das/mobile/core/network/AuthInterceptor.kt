package com.das.mobile.core.network

import com.das.mobile.core.di.ApplicationScope
import com.das.mobile.core.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <jwt>` to every gateway call except /auth/…, and clears the
 * session when the gateway rejects the token (expired or invalid).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
    @ApplicationScope private val scope: CoroutineScope,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isAuthCall = request.url.encodedPath.startsWith("/auth/")
        val token = sessionStore.token

        val authorized = if (!isAuthCall && token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }

        val response = chain.proceed(authorized)
        if (response.code == 401 && !isAuthCall && token != null) {
            scope.launch { sessionStore.clear() }
        }
        return response
    }
}
