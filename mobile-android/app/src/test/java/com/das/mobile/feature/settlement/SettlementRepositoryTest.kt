package com.das.mobile.feature.settlement

import com.das.mobile.core.data.ChangeNotifier
import com.das.mobile.core.files.UploadFile
import com.das.mobile.core.network.SafeApiCall
import com.das.mobile.feature.settlement.data.InvoiceDraft
import com.das.mobile.feature.settlement.data.SettlementApi
import com.das.mobile.feature.settlement.data.SettlementHeader
import com.das.mobile.feature.settlement.data.SettlementRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SettlementRepositoryTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var repo: SettlementRepository

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SettlementApi::class.java)
        repo = SettlementRepository(api, SafeApiCall(json), ChangeNotifier())
    }

    @After
    fun tearDown() = server.shutdown()

    private val settlementJson = """
        {"id":"s1","description":"Q3","settlementDate":"2026-09-30","status":"DRAFT","totalAmount":1234.56,
         "invoices":[{"id":"i1","invoiceNumber":"A-1","issueDate":"2026-09-01","dueDate":null,"amount":1234.56,
                      "status":"PENDING","fileName":"a.pdf","contentType":"application/pdf","sizeInBytes":2048,"sha256Hash":"x"}],
         "medicalSalesRepId":"m1"}
    """.trimIndent()

    @Test
    fun `parses settlement with exact BigDecimal amounts`() = runTest {
        server.enqueue(MockResponse().setBody(settlementJson))
        val s = repo.get("s1").getOrThrow()
        assertEquals(BigDecimal("1234.56"), s.totalAmount)
        assertEquals(LocalDate.of(2026, 9, 30), s.date)
        assertFalse(s.canEditHeader)
        assertEquals("/api/v1/settlement/s1", server.takeRequest().path)
    }

    @Test
    fun `addInvoice sends raw multipart form fields and a file part`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"id":"i9","invoiceNumber":"B-7","issueDate":"2026-10-01","amount":99.90}"""
        ))
        repo.addInvoice(
            "s1",
            InvoiceDraft("B-7", LocalDate.of(2026, 10, 1), null, BigDecimal("99.90"),
                UploadFile("inv.pdf", "application/pdf", byteArrayOf(1, 2, 3))),
        ).getOrThrow()

        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/settlement/invoice/add", req.path)
        assertTrue(req.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(body.contains("name=\"invoiceNumber\""))
        assertTrue("field must be raw text", body.contains("\r\n\r\nB-7\r\n"))
        assertFalse("field must not be JSON-quoted", body.contains("\"B-7\""))
        assertTrue(body.contains("name=\"amount\""))
        assertTrue(body.contains("99.90"))
        assertTrue(body.contains("name=\"issueDate\""))
        assertTrue(body.contains("2026-10-01"))
        assertFalse("optional dueDate omitted", body.contains("name=\"dueDate\""))
        assertTrue(body.contains("name=\"file\"; filename=\"inv.pdf\""))
    }

    @Test
    fun `removeInvoice is a DELETE with a JSON body`() = runTest {
        server.enqueue(MockResponse().setBody(settlementJson))
        repo.removeInvoice("s1", "i1").getOrThrow()
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("""{"settlementId":"s1","invoiceId":"i1"}""", req.body.readUtf8())
    }

    @Test
    fun `updateHeader refuses when the settlement has invoices`() = runTest {
        server.enqueue(MockResponse().setBody(settlementJson))
        val r = repo.updateHeader(SettlementHeader("s1", "Q3 fixed", LocalDate.of(2026, 9, 30), "m1"))
        assertTrue(r.isFailure)
        assertEquals(1, server.requestCount) // only the GET, no PUT
    }

    @Test
    fun `create sends zone-less date and empty invoices`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"id":"s2","description":"New","settlementDate":"2026-10-02","totalAmount":0,"invoices":[],"medicalSalesRepId":"m1"}"""
        ))
        repo.create(SettlementHeader(null, "New", LocalDate.of(2026, 10, 2), "m1")).getOrThrow()
        assertEquals(
            """{"description":"New","settlementDate":"2026-10-02","invoices":[],"medicalSalesRepId":"m1"}""",
            server.takeRequest().body.readUtf8(),
        )
    }
}
