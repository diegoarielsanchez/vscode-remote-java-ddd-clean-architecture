package com.das.mobile.feature.hcp.data

import com.das.mobile.core.network.SafeApiCall
import com.das.mobile.core.network.recoverEmptyList
import com.das.mobile.core.ui.picker.PickerItem
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class HcpDto(
    val id: String,
    val name: String,
    val surname: String,
    val email: String? = null,
    val active: Boolean? = null,
    val specialties: List<String> = emptyList(), // specialty display names
)

@Serializable
data class SpecialtyDto(val code: String, val name: String)

/** healthcare-prof-service via gateway route `/api/v1/healthcareprof/…` (read-only use here). */
interface HcpApi {
    @GET("api/v1/healthcareprof/{id}")
    suspend fun get(@Path("id") id: String): HcpDto

    /** Exact (not partial) match on firstName/lastName; both empty returns everyone, unpaginated. */
    @POST("api/v1/healthcareprof/list")
    suspend fun list(
        @Query("firstName") firstName: String = "",
        @Query("lastName") lastName: String = "",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100,
    ): List<HcpDto>

    @GET("api/v1/healthcareprof/specialties")
    suspend fun specialties(): List<SpecialtyDto>
}

data class HealthCareProf(
    val id: String,
    val name: String,
    val surname: String,
    val email: String,
    val active: Boolean,
    val specialties: List<String>,
) {
    val fullName: String get() = "$name $surname"
}

fun HealthCareProf.toPickerItem() = PickerItem(id, fullName, email.ifBlank { null }, specialties)

/**
 * Directory of healthcare professionals used by visit / visit-plan forms (picker) and lists
 * (id -> name). The list is cached in memory for the session and filtered on the device.
 */
@Singleton
class HcpRepository @Inject constructor(
    private val api: HcpApi,
    private val call: SafeApiCall,
) {
    private val mutex = Mutex()
    private var cache: List<HealthCareProf>? = null
    private val byId = ConcurrentHashMap<String, HealthCareProf>()

    suspend fun all(forceRefresh: Boolean = false): Result<List<HealthCareProf>> = mutex.withLock {
        val cached = cache
        if (cached != null && !forceRefresh) return@withLock Result.success(cached)
        call { api.list().map { it.toDomain() } }
            .recoverEmptyList()
            .onSuccess { list ->
                cache = list
                list.forEach { byId[it.id] = it }
            }
    }

    suspend fun get(id: String): Result<HealthCareProf> =
        byId[id]?.let { Result.success(it) }
            ?: call { api.get(id).toDomain() }.onSuccess { byId[id] = it }

    /** id -> full name, from the cached directory; ids it can't resolve are simply absent. */
    suspend fun names(): Map<String, String> =
        all().getOrNull().orEmpty().associate { it.id to it.fullName }
}

private fun HcpDto.toDomain() = HealthCareProf(id, name, surname, email.orEmpty(), active ?: true, specialties)

@Module
@InstallIn(SingletonComponent::class)
object HcpModule {
    @Provides
    @Singleton
    fun hcpApi(retrofit: Retrofit): HcpApi = retrofit.create(HcpApi::class.java)
}
