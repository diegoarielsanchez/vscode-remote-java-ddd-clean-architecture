package com.das.mobile.feature.hcp

import com.das.mobile.core.network.SafeApiCall
import com.das.mobile.feature.hcp.data.HcpApi
import com.das.mobile.feature.hcp.data.HcpRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class HcpRepositoryTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var repo: HcpRepository

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HcpApi::class.java)
        repo = HcpRepository(api, SafeApiCall(json))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `loads the directory once and serves names from cache`() = runTest {
        server.enqueue(MockResponse().setBody(
            """[{"id":"h1","name":"Ana","surname":"Gómez","email":"ana@x.com","active":true,"specialties":["Cardiology"]}]"""
        ))
        assertEquals("Ana Gómez", repo.all().getOrThrow().single().fullName)
        assertEquals(mapOf("h1" to "Ana Gómez"), repo.names())
        assertEquals("Ana Gómez", repo.get("h1").getOrThrow().fullName)
        assertEquals(1, server.requestCount)
        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/healthcareprof/list?firstName=&lastName="))
    }

    @Test
    fun `backend not-found on an empty directory yields an empty list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"statusCode":400,"message":"Health Care Professional not found."}"""
        ))
        assertEquals(0, repo.all().getOrThrow().size)
    }
}
