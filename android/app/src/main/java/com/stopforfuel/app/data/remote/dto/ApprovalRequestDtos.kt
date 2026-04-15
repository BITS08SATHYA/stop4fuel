package com.stopforfuel.app.data.remote.dto

data class ApprovalRequestDto(
    val id: Long,
    val requestType: String,      // ADD_VEHICLE | UNBLOCK_CUSTOMER | RAISE_CREDIT_LIMIT | RECORD_STATEMENT_PAYMENT | RECORD_INVOICE_PAYMENT
    val status: String,           // PENDING | APPROVED | REJECTED
    val customerId: Long?,
    val payload: String?,         // raw JSON string
    val requestedBy: Long?,
    val requestNote: String?,
    val reviewedBy: Long?,
    val reviewNote: String?,
    val reviewedAt: String?,
    val createdAt: String?,
    val updatedAt: String?
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
