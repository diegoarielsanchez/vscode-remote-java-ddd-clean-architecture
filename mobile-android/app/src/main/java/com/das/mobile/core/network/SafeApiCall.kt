package com.das.mobile.core.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

/** Runs a Retrofit call and maps any failure to [ApiError]. */
@Singleton
class SafeApiCall @Inject constructor(private val json: Json) {
    suspend operator fun <T> invoke(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e.toApiError(json))
        }
}

/**
 * The visit, visit-plan, HCP and MSR list use cases throw a DomainException
 * ("Visit not found.", "No visit plans found.", ...) instead of returning `[]`,
 * including when paging past the last page. Treat that as an empty result.
 */
fun <T> Result<List<T>>.recoverEmptyList(): Result<List<T>> = recoverCatching { e ->
    if (e is ApiError.Server && e.code == 400 && e.message.orEmpty().contains("found", ignoreCase = true)) {
        emptyList()
    } else {
        throw e
    }
}
