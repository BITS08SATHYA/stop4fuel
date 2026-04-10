package com.stopforfuel.app.data.remote.dto

import java.math.BigDecimal

data class RecordPaymentRequest(
    val amount: BigDecimal,
    val paymentMode: String,
    val referenceNo: String? = null,
    val remarks: String? = null
)

data class PaymentDto(
    val id: Long,
    val amount: BigDecimal?,
    val paymentMode: String?,
    val paymentDate: String?,
    val referenceNo: String?,
    val remarks: String?,
    val customer: CustomerSummaryDto?,
    val receivedBy: UserSummaryDto?,
    val shiftId: Long?
)

data class PaymentSummaryDto(
    val totalAmount: BigDecimal?,
    val receivedAmount: BigDecimal?,
    val balanceAmount: BigDecimal?,
    val paymentCount: Int?,
    val payments: List<PaymentDto>?
)
