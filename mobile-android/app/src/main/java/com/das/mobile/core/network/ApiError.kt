package com.das.mobile.core.network

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

/**
 * Errors surfaced to ViewModels. The backend answers with three different error bodies:
 *  - `{statusCode, message}`     domain errors, 404, 429 (GlobalExceptionHandler)
 *  - `{"field": "message", ...}` bean-validation errors
 *  - `{error}`                   failed login (identity-service)
 */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized : ApiError("Your session has expired. Please sign in again.")
    class RateLimited : ApiError("Too many requests. Please wait a minute and try again.")
    class Unavailable : ApiError("The service is temporarily unavailable. Please try again later.")
    class Validation(val fields: Map<String, String>) :
        ApiError(fields.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    class Server(val code: Int, message: String) : ApiError(message)
    class Network(cause: Throwable) : ApiError("Cannot reach the server. Check your connection.", cause)
}

fun Throwable.toApiError(json: Json): ApiError = when (this) {
    is ApiError -> this
    is HttpException -> parseHttpError(code(), response()?.errorBody()?.string(), json)
    is IOException -> ApiError.Network(this)
    is SerializationException -> ApiError.Server(-1, "Unexpected response from the server.")
    else -> ApiError.Server(-1, message ?: "Unexpected error.")
}

internal fun parseHttpError(code: Int, body: String?, json: Json): ApiError {
    when (code) {
        401 -> return ApiError.Unauthorized()
        429 -> return ApiError.RateLimited()
        502, 503, 504 -> return ApiError.Unavailable() // 503 = gateway circuit-breaker fallback
    }
    val obj = body
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        ?: return ApiError.Server(code, "Request failed (HTTP $code).")

    obj.stringOrNull("message")?.let { return ApiError.Server(code, it) }
    obj.stringOrNull("error")?.let { return ApiError.Server(code, it) }
    if (obj.isNotEmpty() && obj.values.all { it is JsonPrimitive }) {
        return ApiError.Validation(obj.mapValues { (it.value as JsonPrimitive).content })
    }
    return ApiError.Server(code, "Request failed (HTTP $code).")
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
