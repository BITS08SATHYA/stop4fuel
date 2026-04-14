package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.CreateInvoiceRequest
import com.stopforfuel.app.data.remote.dto.InvoiceBillDto
import com.stopforfuel.app.data.remote.dto.PageResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvoiceRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun createInvoice(request: CreateInvoiceRequest): Result<InvoiceBillDto> {
        return try {
            Result.success(api.createInvoice(request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getInvoicesByShift(shiftId: Long): Result<List<InvoiceBillDto>> {
        return try {
            Result.success(api.getInvoicesByShift(shiftId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getInvoiceHistory(
        page: Int = 0,
        size: Int = 20,
        billType: String? = null,
        paymentStatus: String? = null,
        fromDate: String? = null,
        toDate: String? = null,
        search: String? = null,
        categoryType: String? = null
    ): Result<PageResponse<InvoiceBillDto>> {
        return try {
            Result.success(api.getInvoiceHistory(page, size, billType, paymentStatus, fromDate, toDate, search, categoryType))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateInvoice(id: Long, body: Map<String, Any?>): Result<InvoiceBillDto> {
        return try {
            Result.success(api.updateInvoice(id, body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadInvoiceFile(invoiceId: Long, type: String, file: File): Result<InvoiceBillDto> {
        return try {
            android.util.Log.d(
                "InvoiceUpload",
                "uploadInvoiceFile id=$invoiceId type=$type name=${file.name} exists=${file.exists()} size=${file.length()}"
            )
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestBody = file.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
            Result.success(api.uploadInvoiceFile(invoiceId, type, part))
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            val errorBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull()?.take(400)
            val serverMsg = errorBody?.let {
                runCatching { org.json.JSONObject(it).optString("message", "") }.getOrNull()?.ifBlank { null }
            } ?: errorBody
            android.util.Log.e("InvoiceUpload", "HTTP $code body=$errorBody", e)
            Result.failure(Exception("HTTP $code ${e.message().orEmpty()} ${serverMsg.orEmpty()}".trim()))
        } catch (e: Exception) {
            android.util.Log.e("InvoiceUpload", "upload exception", e)
            Result.failure(e)
        }
    }
}
