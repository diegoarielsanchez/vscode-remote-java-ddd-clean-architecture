package com.das.mobile.feature.msr.data

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
data class MsrDto(
    val id: String,
    val name: String,
    val surname: String,
    val email: String? = null,
    val active: Boolean? = null,
)

/** medical-sales-rep-service via gateway route `/api/v1/medicalsalesrep/…` (read-only use here). */
interface MsrApi {
    @GET("api/v1/medicalsalesrep/{id}")
    suspend fun get(@Path("id") id: String): MsrDto

    /** Exact (not partial) match on firstName/lastName; both empty returns everyone, unpaginated. */
    @POST("api/v1/medicalsalesrep/list")
    suspend fun list(
        @Query("firstName") firstName: String = "",
        @Query("lastName") lastName: String = "",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100,
    ): List<MsrDto>
}

data class MedicalSalesRep(
    val id: String,
    val name: String,
    val surname: String,
    val email: String,
    val active: Boolean,
) {
    val fullName: String get() = "$name $surname"
}

fun MedicalSalesRep.toPickerItem() = PickerItem(id, fullName, email.ifBlank { null })

/**
 * Directory of medical sales reps used by visit, visit-plan and settlement forms (picker) and
 * lists (id -> name). Cached in memory for the session and filtered on the device.
 */
@Singleton
class MsrRepository @Inject constructor(
    private val api: MsrApi,
    private val call: SafeApiCall,
) {
    private val mutex = Mutex()
    private var cache: List<MedicalSalesRep>? = null
    private val byId = ConcurrentHashMap<String, MedicalSalesRep>()

    suspend fun all(forceRefresh: Boolean = false): Result<List<MedicalSalesRep>> = mutex.withLock {
        val cached = cache
        if (cached != null && !forceRefresh) return@withLock Result.success(cached)
        call { api.list().map { it.toDomain() } }
            .recoverEmptyList()
            .onSuccess { list ->
                cache = list
                list.forEach { byId[it.id] = it }
            }
    }

    suspend fun get(id: String): Result<MedicalSalesRep> =
        byId[id]?.let { Result.success(it) }
            ?: call { api.get(id).toDomain() }.onSuccess { byId[id] = it }

    suspend fun names(): Map<String, String> =
        all().getOrNull().orEmpty().associate { it.id to it.fullName }
}

private fun MsrDto.toDomain() = MedicalSalesRep(id, name, surname, email.orEmpty(), active ?: true)

@Module
@InstallIn(SingletonComponent::class)
object MsrModule {
    @Provides
    @Singleton
    fun msrApi(retrofit: Retrofit): MsrApi = retrofit.create(MsrApi::class.java)
}
