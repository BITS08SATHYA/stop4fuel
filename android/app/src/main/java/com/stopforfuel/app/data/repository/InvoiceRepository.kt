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
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestBody = file.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
            Result.success(api.uploadInvoiceFile(invoiceId, type, part))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val message = if (e.code() == 400 && errorBody != null) {
                try {
                    org.json.JSONObject(errorBody).optString("message", e.message())
                } catch (_: Exception) { errorBody }
            } else {
                e.message()
            }
            Result.failure(Exception(message ?: "Upload failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
