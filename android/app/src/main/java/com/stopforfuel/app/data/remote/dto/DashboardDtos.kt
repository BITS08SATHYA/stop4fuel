package com.stopforfuel.app.data.remote.dto

import java.math.BigDecimal

data class DashboardStatsDto(
    val todayRevenue: BigDecimal?,
    val todayFuelVolume: BigDecimal?,
    val todayInvoiceCount: Long?,
    val todayCashInvoices: Long?,
    val todayCreditInvoices: Long?,
    val activeShiftId: Long?,
    val activeShiftStartTime: String?,
    val shiftTotal: BigDecimal?,
    val shiftNet: BigDecimal?,
    val totalOutstanding: BigDecimal?,
    val totalCreditCustomers: Long?,
    val productSales: List<ProductSaleDto>?
)

data class ProductSaleDto(
    val productName: String?,
    val quantity: Double?,
    val amount: Double?
)

data class SystemHealthDto(
    val totalCustomers: Long?,
    val activeCustomers: Long?,
    val blockedCustomers: Long?,
    val inactiveCustomers: Long?,
    val totalVehicles: Long?,
    val totalEmployees: Long?,
    val activeShifts: Long?,
    val totalProducts: Long?
)

data class CashierDashboardDto(
    val hasActiveShift: Boolean?,
    val shiftId: Long?,
    val shiftStatus: String?,
    val startTime: String?,
    val attendantName: String?,
    val cashBillTotal: BigDecimal?,
    val creditBillTotal: BigDecimal?,
    val totalInvoiceCount: Int?,
    val cashInvoiceCount: Int?,
    val creditInvoiceCount: Int?,
    val eAdvanceTotals: Map<String, BigDecimal>?,
    val billPaymentTotal: BigDecimal?,
    val statementPaymentTotal: BigDecimal?,
    val expenseTotal: BigDecimal?,
    val operationalAdvanceTotal: BigDecimal?,
    val incentiveTotal: BigDecimal?,
    val cashInHand: BigDecimal?,
    val recentInvoices: List<CashierInvoiceItem>?
)

data class CashierInvoiceItem(
    val id: Long?,
    val billNo: String?,
    val billType: String?,
    val paymentMode: String?,
    val netAmount: BigDecimal?,
    val date: String?,
    val customerName: String?
)
