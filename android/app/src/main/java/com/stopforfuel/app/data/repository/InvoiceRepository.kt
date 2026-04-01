package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.CreateInvoiceRequest
import com.stopforfuel.app.data.remote.dto.InvoiceBillDto
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
}
