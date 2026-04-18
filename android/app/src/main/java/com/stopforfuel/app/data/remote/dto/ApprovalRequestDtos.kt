package com.stopforfuel.app.data.remote.dto

data class ApprovalRequestDto(
    val id: Long,
    val requestType: String,      // ADD_VEHICLE | UNBLOCK_CUSTOMER | RAISE_CREDIT_LIMIT | RECORD_STATEMENT_PAYMENT | RECORD_INVOICE_PAYMENT
    val status: String,           // PENDING | APPROVED | REJECTED
    val customerId: Long?,
    val customerName: String?,
    val payload: Map<String, Any?>?,   // server-parsed
    val requestedBy: Long?,
    val requestNote: String?,
    val reviewedBy: Long?,
    val reviewNote: String?,
    val reviewedAt: String?,
    val createdAt: String?,
    val updatedAt: String?,
    // Type-specific hydrated fields (nullable; only the relevant ones populated)
    val billNo: String? = null,
    val statementNo: String? = null,
    val amount: Double? = null,
    val paymentMode: String? = null,
    val vehicleNumber: String? = null,
    val currentCreditLimitAmount: Double? = null,
    val requestedCreditLimitAmount: Double? = null,
    val currentCreditLimitLiters: Double? = null,
    val requestedCreditLimitLiters: Double? = null
)

data class SubmitApprovalRequestBody(
    val requestType: String,
    val customerId: Long?,
    val payload: Map<String, Any?>,
    val note: String?
)

data class ApprovalReviewBody(
    val note: String?
)

data class ApprovalPendingCountDto(
    val count: Long
)
