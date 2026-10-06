package com.das.mobile.feature.settlement.data

import com.das.mobile.core.network.BigDecimalSerializer
import java.math.BigDecimal
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// ── DTOs: mirror settlement-domain usecases/dtos ───────────────────────────────

@Serializable
data class InvoiceDto(
    val id: String,
    val invoiceNumber: String,
    val issueDate: String,
    val dueDate: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val status: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val sizeInBytes: Long? = null,
    val sha256Hash: String? = null,
)

@Serializable
data class SettlementDto(
    val id: String,
    val description: String,
    val settlementDate: String,
    val status: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val totalAmount: BigDecimal = BigDecimal.ZERO,
    val invoices: List<InvoiceDto> = emptyList(),
    val medicalSalesRepId: String,
)

/** CreateInvoiceInputDTO (invoice without file, embedded in create/update). */
@Serializable
data class InvoiceRequest(
    val invoiceNumber: String,
    val issueDate: String,
    val dueDate: String?,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
)

/** Create/UpdateSettlementInputDTO. `id` only on update. */
@Serializable
data class SettlementRequest(
    val id: String? = null,
    val description: String,
    val settlementDate: String,
    val invoices: List<InvoiceRequest>, // no default: kotlinx omits default-valued fields
    val medicalSalesRepId: String,
)

@Serializable
data class RemoveInvoiceRequest(val settlementId: String, val invoiceId: String)

// ── Endpoints: gateway route /api/v1/settlement/** ─────────────────────────────

interface SettlementApi {
    @POST("api/v1/settlement/create")
    suspend fun create(@Body body: SettlementRequest): SettlementDto

    /** WARNING: rebuilds the invoice list from `body.invoices` (see SettlementRepository.updateHeader). */
    @PUT("api/v1/settlement/update")
    suspend fun update(@Body body: SettlementRequest): SettlementDto

    @GET("api/v1/settlement/{id}")
    suspend fun get(@Path("id") id: String): SettlementDto

    @POST("api/v1/settlement/list")
    suspend fun list(@Query("page") page: Int, @Query("pageSize") pageSize: Int): List<SettlementDto>

    /** Plain form fields + a "file" part; max 10 MB; .pdf .xlsx .docx .txt */
    @Multipart
    @POST("api/v1/settlement/invoice/add")
    suspend fun addInvoice(
        @Part("settlementId") settlementId: RequestBody,
        @Part("invoiceNumber") invoiceNumber: RequestBody,
        @Part("issueDate") issueDate: RequestBody,
        @Part("dueDate") dueDate: RequestBody?,
        @Part("amount") amount: RequestBody,
        @Part file: MultipartBody.Part,
    ): InvoiceDto

    /** DELETE with a JSON body. */
    @HTTP(method = "DELETE", path = "api/v1/settlement/invoice/remove", hasBody = true)
    suspend fun removeInvoice(@Body body: RemoveInvoiceRequest): SettlementDto
}
