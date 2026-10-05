package com.das.mobile.core.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class UploadFile(val name: String, val mimeType: String, val bytes: ByteArray)

fun UploadFile.toPart(partName: String): MultipartBody.Part =
    MultipartBody.Part.createFormData(partName, name, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))

/**
 * Reads a document picked with the Storage Access Framework and applies the same rules as the
 * backend controllers (allowed extensions, 10 MB invoice limit) before anything is uploaded.
 */
@Singleton
class DocumentReader @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun read(uri: Uri): Result<UploadFile> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var name = "document"
            var size: Long? = null
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
            val ext = name.substringAfterLast('.', "").lowercase()
            require(ext in ALLOWED_EXTENSIONS) {
                "Unsupported file type '.$ext'. Allowed: .pdf, .xlsx, .docx, .txt"
            }
            require((size ?: 0L) <= MAX_BYTES) { "$name exceeds the maximum size of 10 MB." }

            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot open $name")
            require(bytes.size <= MAX_BYTES) { "$name exceeds the maximum size of 10 MB." }

            UploadFile(name, resolver.getType(uri) ?: MIME_BY_EXTENSION.getValue(ext), bytes)
        }
    }

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
        val ALLOWED_EXTENSIONS = setOf("pdf", "xlsx", "docx", "txt")
        private val MIME_BY_EXTENSION = mapOf(
            "pdf" to "application/pdf",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt" to "text/plain",
        )

        /** MIME filter for OpenDocument / OpenMultipleDocuments. */
        val PICKER_MIME_TYPES: Array<String> = MIME_BY_EXTENSION.values.toTypedArray()
    }
}
