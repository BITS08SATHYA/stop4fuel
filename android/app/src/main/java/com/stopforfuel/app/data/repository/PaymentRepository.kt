package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.PaymentDto
import com.stopforfuel.app.data.remote.dto.PaymentSummaryDto
import com.stopforfuel.app.data.remote.dto.RecordPaymentRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun recordBillPayment(invoiceBillId: Long, request: RecordPaymentRequest): Result<PaymentDto> {
        return try {
            Result.success(api.recordBillPayment(invoiceBillId, request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordStatementPayment(statementId: Long, request: RecordPaymentRequest): Result<PaymentDto> {
        return try {
            Result.success(api.recordStatementPayment(statementId, request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBillPaymentSummary(invoiceBillId: Long): Result<PaymentSummaryDto> {
        return try {
            Result.success(api.getBillPaymentSummary(invoiceBillId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStatementPaymentSummary(statementId: Long): Result<PaymentSummaryDto> {
        return try {
            Result.success(api.getStatementPaymentSummary(statementId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
