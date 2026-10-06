package com.das.mobile.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `domain error body maps to Server with message`() {
        val e = parseHttpError(400, """{"statusCode":400,"message":"Medical sales rep not found or is not active."}""", json)
        assertTrue(e is ApiError.Server)
        assertEquals("Medical sales rep not found or is not active.", e.message)
    }

    @Test
    fun `validation body maps to field errors`() {
        val e = parseHttpError(400, """{"visitDateTime":"must be a future date","visitSiteId":"must not be null"}""", json)
        assertTrue(e is ApiError.Validation)
        assertEquals("must be a future date", (e as ApiError.Validation).fields["visitDateTime"])
    }

    @Test
    fun `login failure body maps to Server with error text`() {
        val e = parseHttpError(403, """{"error":"Invalid credentials"}""", json)
        assertEquals("Invalid credentials", e.message)
    }

    @Test
    fun `status codes map to specific errors`() {
        assertTrue(parseHttpError(401, null, json) is ApiError.Unauthorized)
        assertTrue(parseHttpError(429, "{}", json) is ApiError.RateLimited)
        assertTrue(parseHttpError(503, "{}", json) is ApiError.Unavailable)
    }

    @Test
    fun `not-found domain error on a list becomes an empty list`() {
        val r: Result<List<String>> = Result.failure(ApiError.Server(400, "Visit not found."))
        assertEquals(emptyList<String>(), r.recoverEmptyList().getOrThrow())
    }

    @Test
    fun `other errors on a list are kept`() {
        val r: Result<List<String>> = Result.failure(ApiError.Server(400, "Invalid page"))
        assertTrue(r.recoverEmptyList().isFailure)
    }
}
