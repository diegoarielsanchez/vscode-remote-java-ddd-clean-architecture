package com.das.mobile.feature.settlement.data

import com.das.mobile.core.data.ChangeNotifier
import com.das.mobile.core.data.DataChange
import com.das.mobile.core.files.UploadFile
import com.das.mobile.core.files.toPart
import com.das.mobile.core.network.SafeApiCall
import com.das.mobile.core.util.ApiDates
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit

data class Invoice(
    val id: String,
    val number: String,
    val issueDate: LocalDate?,
    val dueDate: LocalDate?,
    val amount: BigDecimal,
    val status: String?,
    val fileName: String?,
    val sizeInBytes: Long?,
)

data class Settlement(
    val id: String,
    val description: String,
    val date: LocalDate?,
    val status: String?,
    val totalAmount: BigDecimal,
    val invoices: List<Invoice>,
    val medicalSalesRepId: String,
) {
    /** See [SettlementRepository.updateHeader]. */
    val canEditHeader: Boolean get() = invoices.isEmpty()
}

data class SettlementHeader(
    val id: String?,
    val description: String,
    val date: LocalDate,
    val medicalSalesRepId: String,
)

data class InvoiceDraft(
    val number: String,
    val issueDate: LocalDate,
    val dueDate: LocalDate?,
    val amount: BigDecimal,
    val file: UploadFile,
)

@Singleton
class SettlementRepository @Inject constructor(
    private val api: SettlementApi,
    private val call: SafeApiCall,
    private val changes: ChangeNotifier,
) {
    suspend fun list(page: Int, pageSize: Int): Result<List<Settlement>> =
        call { api.list(page, pageSize).map { it.toDomain() } }

    suspend fun get(id: String): Result<Settlement> = call { api.get(id).toDomain() }

    /** Creates an empty settlement; invoices (with their files) are added one by one afterwards. */
    suspend fun create(header: SettlementHeader): Result<Settlement> = call {
        api.create(
            SettlementRequest(
                description = header.description,
                settlementDate = ApiDates.format(header.date),
                invoices = emptyList(),
                medicalSalesRepId = header.medicalSalesRepId,
            )
        ).toDomain()
    }.onSuccess { changes.notify(DataChange.SETTLEMENT) }

    /**
     * Backend caveat: UpdateSettlementUseCase rebuilds the settlement with *only* the invoices in
     * the request (new ids, no file metadata). Sending none deletes existing invoices; resending
     * them loses their uploaded files. So the app only allows editing while there are no invoices.
     */
    suspend fun updateHeader(header: SettlementHeader): Result<Settlement> = call {
        val id = requireNotNull(header.id)
        val current = api.get(id)
        check(current.invoices.isEmpty()) { "A settlement with invoices can't be edited. Remove its invoices first." }
        api.update(
            SettlementRequest(
                id = id,
                description = header.description,
                settlementDate = ApiDates.format(header.date),
                invoices = emptyList(),
                medicalSalesRepId = header.medicalSalesRepId,
            )
        ).toDomain()
    }.onSuccess { changes.notify(DataChange.SETTLEMENT) }

    suspend fun addInvoice(settlementId: String, draft: InvoiceDraft): Result<Invoice> = call {
        api.addInvoice(
            settlementId = settlementId.asFormField(),
            invoiceNumber = draft.number.asFormField(),
            issueDate = ApiDates.format(draft.issueDate).asFormField(),
            dueDate = draft.dueDate?.let { ApiDates.format(it).asFormField() },
            amount = draft.amount.toPlainString().asFormField(),
            file = draft.file.toPart("file"),
        ).toDomain()
    }.onSuccess { changes.notify(DataChange.SETTLEMENT) }

    suspend fun removeInvoice(settlementId: String, invoiceId: String): Result<Settlement> =
        call { api.removeInvoice(RemoveInvoiceRequest(settlementId, invoiceId)).toDomain() }
            .onSuccess { changes.notify(DataChange.SETTLEMENT) }
}

private val textPlain = "text/plain".toMediaType()

/** Multipart form fields must be raw text; Retrofit's JSON converter would add quotes. */
private fun String.asFormField(): RequestBody = toRequestBody(textPlain)

private fun InvoiceDto.toDomain() = Invoice(
    id = id,
    number = invoiceNumber,
    issueDate = ApiDates.parseDate(issueDate),
    dueDate = ApiDates.parseDate(dueDate),
    amount = amount,
    status = status,
    fileName = fileName,
    sizeInBytes = sizeInBytes,
)

private fun SettlementDto.toDomain() = Settlement(
    id = id,
    description = description,
    date = ApiDates.parseDate(settlementDate),
    status = status,
    totalAmount = totalAmount,
    invoices = invoices.map { it.toDomain() },
    medicalSalesRepId = medicalSalesRepId,
)

@Module
@InstallIn(SingletonComponent::class)
object SettlementModule {
    @Provides
    @Singleton
    fun settlementApi(retrofit: Retrofit): SettlementApi = retrofit.create(SettlementApi::class.java)
}
