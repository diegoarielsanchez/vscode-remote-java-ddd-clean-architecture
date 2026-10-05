package com.das.mobile.feature.auth.data

import com.das.mobile.core.network.SafeApiCall
import com.das.mobile.core.session.Session
import com.das.mobile.core.session.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val call: SafeApiCall,
    private val sessionStore: SessionStore,
) {
    suspend fun login(username: String, password: String): Result<Session> =
        call { api.login(LoginRequest(username.trim(), password)) }
            .map { Session(it.token, it.username, it.roles) }
            .onSuccess { sessionStore.save(it) }

    suspend fun logout() = sessionStore.clear()
}

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
}
