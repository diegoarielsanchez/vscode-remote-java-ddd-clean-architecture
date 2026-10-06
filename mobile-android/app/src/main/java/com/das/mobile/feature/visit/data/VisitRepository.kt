package com.das.mobile.feature.visit.data

import com.das.mobile.core.data.ChangeNotifier
import com.das.mobile.core.data.DataChange
import com.das.mobile.core.files.UploadFile
import com.das.mobile.core.files.toPart
import com.das.mobile.core.network.SafeApiCall
import com.das.mobile.core.network.recoverEmptyList
import com.das.mobile.core.util.ApiDates
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit

data class Attachment(val fileName: String, val storageReference: String?, val sha256Hash: String?)

data class Visit(
    val id: String,
    val date: LocalDate?,
    val healthCareProfId: String?,
    val medicalSalesRepId: String?,
    val visitSiteId: String?,
    val comments: String?,
    val attachments: List<Attachment>,
)

data class VisitDraft(
    val id: String?,
    val date: LocalDate,
    val healthCareProfId: String,
    val medicalSalesRepId: String,
    val visitSiteId: String,
    val comments: String?,
)

data class VisitPlan(
    val id: String,
    val dateTime: LocalDateTime?,
    val healthCareProfId: String?,
    val medicalSalesRepId: String?,
    val visitSiteId: String?,
    val comments: String?,
    val active: Boolean,
)

data class VisitPlanDraft(
    val id: String?,
    val dateTime: LocalDateTime,
    val healthCareProfId: String,
    val medicalSalesRepId: String,
    val visitSiteId: String,
    val comments: String?,
)

@Singleton
class VisitRepository @Inject constructor(
    private val api: VisitApi,
    private val call: SafeApiCall,
    private val changes: ChangeNotifier,
) {
    suspend fun list(page: Int, pageSize: Int): Result<List<Visit>> =
        call { api.list(page, pageSize).map { it.toDomain() } }.recoverEmptyList()

    suspend fun get(id: String): Result<Visit> = call { api.get(id).toDomain() }

    suspend fun save(draft: VisitDraft): Result<Visit> = call {
        val body = VisitRequest(
            id = draft.id,
            visitDate = ApiDates.format(draft.date),
            healthCareProfId = draft.healthCareProfId,
            visitComments = draft.comments?.ifBlank { null },
            visitSiteId = draft.visitSiteId,
            medicalSalesRepId = draft.medicalSalesRepId,
        )
        (if (draft.id == null) api.create(body) else api.update(body)).toDomain()
    }.onSuccess { changes.notify(DataChange.VISIT) }

    suspend fun uploadAttachments(visitId: String, files: List<UploadFile>): Result<List<Attachment>> =
        call { api.uploadAttachments(visitId, files.map { it.toPart("files") }).attachments.map { it.toDomain() } }
            .onSuccess { changes.notify(DataChange.VISIT) }
}

@Singleton
class VisitPlanRepository @Inject constructor(
    private val api: VisitPlanApi,
    private val call: SafeApiCall,
    private val changes: ChangeNotifier,
) {
    suspend fun list(page: Int, pageSize: Int): Result<List<VisitPlan>> =
        call { api.list(page, pageSize).map { it.toDomain() } }.recoverEmptyList()

    suspend fun get(id: String): Result<VisitPlan> = call { api.get(id).toDomain() }

    suspend fun save(draft: VisitPlanDraft): Result<VisitPlan> = call {
        val body = VisitPlanRequest(
            id = draft.id,
            visitDateTime = ApiDates.format(draft.dateTime),
            healthCareProfId = draft.healthCareProfId,
            visitComments = draft.comments?.ifBlank { null },
            visitSiteId = draft.visitSiteId,
            medicalSalesRepId = draft.medicalSalesRepId,
        )
        (if (draft.id == null) api.create(body) else api.update(body)).toDomain()
    }.onSuccess { changes.notify(DataChange.VISIT_PLAN) }
}

private fun AttachmentDto.toDomain() = Attachment(fileName, storageReference, sha256Hash)

private fun VisitDto.toDomain() = Visit(
    id = id,
    date = ApiDates.parseDate(visitDate),
    healthCareProfId = healthCareProfId,
    medicalSalesRepId = medicalSalesRepId,
    visitSiteId = visitSiteId,
    comments = visitComments,
    attachments = productPromoAttachments.map { it.toDomain() },
)

private fun VisitPlanDto.toDomain() = VisitPlan(
    id = id,
    dateTime = ApiDates.parseDateTime(visitDateTime),
    healthCareProfId = healthCareProfId,
    medicalSalesRepId = medicalSalesRepId,
    visitSiteId = visitSiteId,
    comments = visitComments,
    active = active,
)

@Module
@InstallIn(SingletonComponent::class)
object VisitModule {
    @Provides
    @Singleton
    fun visitApi(retrofit: Retrofit): VisitApi = retrofit.create(VisitApi::class.java)

    @Provides
    @Singleton
    fun visitPlanApi(retrofit: Retrofit): VisitPlanApi = retrofit.create(VisitPlanApi::class.java)
}
