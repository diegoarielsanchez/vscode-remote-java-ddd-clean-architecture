package com.das.mobile.feature.visit.data

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// ── DTOs: mirror visit-domain usecases/dtos ────────────────────────────────────

@Serializable
data class AttachmentDto(val fileName: String, val storageReference: String? = null, val sha256Hash: String? = null)

/** VisitOutputDTO. `visitDate` is a LocalDateTime (no offset) in responses. */
@Serializable
data class VisitDto(
    val id: String,
    val visitDate: String,
    val healthCareProfId: String? = null,
    val visitComments: String? = null,
    val visitSiteId: String? = null,
    val medicalSalesRepId: String? = null,
    val productPromoAttachments: List<AttachmentDto> = emptyList(),
)

/** Create/UpdateVisitInputDTO. `visitDate` is "yyyy-MM-dd". `id` only on update. */
@Serializable
data class VisitRequest(
    val id: String? = null,
    val visitDate: String,
    val healthCareProfId: String,
    val visitComments: String?,
    val visitSiteId: String,
    val medicalSalesRepId: String,
)

@Serializable
data class UploadAttachmentsResponse(val visitId: String, val attachments: List<AttachmentDto> = emptyList())

@Serializable
data class VisitPlanDto(
    val id: String,
    val visitDateTime: String,
    val healthCareProfId: String? = null,
    val visitComments: String? = null,
    val visitSiteId: String? = null,
    val medicalSalesRepId: String? = null,
    val active: Boolean = true,
)

/** Create/UpdateVisitPlanInputDTO. `visitDateTime` is "yyyy-MM-dd'T'HH:mm:ss" and must be in the future. */
@Serializable
data class VisitPlanRequest(
    val id: String? = null,
    val visitDateTime: String,
    val healthCareProfId: String,
    val visitComments: String?,
    val visitSiteId: String,
    val medicalSalesRepId: String,
)

// ── Endpoints: gateway routes /api/v1/visit/** and /api/v1/visitplan/** ────────

interface VisitApi {
    @POST("api/v1/visit/create")
    suspend fun create(@Body body: VisitRequest): VisitDto

    @PUT("api/v1/visit/update")
    suspend fun update(@Body body: VisitRequest): VisitDto

    @GET("api/v1/visit/{id}")
    suspend fun get(@Path("id") id: String): VisitDto

    /** POST with query params and no body. */
    @POST("api/v1/visit/list")
    suspend fun list(@Query("page") page: Int, @Query("pageSize") pageSize: Int): List<VisitDto>

    /** Part name "files", repeatable. Allowed: .pdf .xlsx .docx .txt */
    @Multipart
    @POST("api/v1/visit/{visitId}/attachments")
    suspend fun uploadAttachments(
        @Path("visitId") visitId: String,
        @Part files: List<MultipartBody.Part>,
    ): UploadAttachmentsResponse
}

interface VisitPlanApi {
    @POST("api/v1/visitplan/create")
    suspend fun create(@Body body: VisitPlanRequest): VisitPlanDto

    @PUT("api/v1/visitplan/update")
    suspend fun update(@Body body: VisitPlanRequest): VisitPlanDto

    @GET("api/v1/visitplan/{id}")
    suspend fun get(@Path("id") id: String): VisitPlanDto

    @POST("api/v1/visitplan/list")
    suspend fun list(@Query("page") page: Int, @Query("pageSize") pageSize: Int): List<VisitPlanDto>
}
