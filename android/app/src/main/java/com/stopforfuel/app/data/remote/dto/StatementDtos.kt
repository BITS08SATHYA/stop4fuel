package com.stopforfuel.app.data.remote.dto

import java.math.BigDecimal

data class StatementDto(
    val id: Long,
    val statementNo: String?,
    val fromDate: String?,
    val toDate: String?,
    val statementDate: String?,
    val numberOfBills: Int?,
    val totalAmount: BigDecimal?,
    val roundingAmount: BigDecimal?,
    val netAmount: BigDecimal?,
    val totalQuantity: BigDecimal?,
    val receivedAmount: BigDecimal?,
    val balanceAmount: BigDecimal?,
    val status: String?,
    val customer: StatementCustomerSummary?
)

data class StatementCustomerSummary(
    val id: Long?,
    val name: String?,
    val username: String?,
    val categoryType: String?
)
