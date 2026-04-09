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
    val productSales: List<ProductSaleDto>?,
    val lastShiftProductSales: List<ProductSaleDto>?,
    val lastShiftId: Long?,
    val totalStatements: Long?,
    val paidStatements: Long?,
    val unpaidStatements: Long?,
    val mtdSales: List<ProductSaleDto>?,
    val mtdPurchases: List<ProductPurchaseDto>?,
    val mtdCreditCount: Long?,
    val mtdCreditAmount: BigDecimal?,
    val mtdPaymentCount: Long?,
    val mtdPaymentAmount: BigDecimal?,
    val tankStatuses: List<TankStatusDto>?
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
    val activeEmployees: Long?,
    val activeShifts: Long?,
    val totalProducts: Long?,
    val todayAttendanceCount: Long?
)

data class TankStatusDto(
    val tankId: Long?,
    val tankName: String?,
    val productName: String?,
    val capacity: Double?,
    val currentStock: Double?,
    val thresholdStock: Double?,
    val productPrice: Double?,
    val active: Boolean?,
    val lastReadingDate: String?
)

data class BackendHealthDto(
    val status: String?,
    val database: String?,
    val latencyMs: Long?,
    val timestamp: String?
)

data class AwsBillingDto(
    val available: Boolean?,
    val monthToDateCost: Double?,
    val currency: String?,
    val periodStart: String?,
    val periodEnd: String?
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

data class InvoiceAnalyticsDto(
    val totalRevenue: BigDecimal?,
    val totalInvoices: Long?,
    val creditCount: Long?,
    val creditAmount: BigDecimal?,
    val cashCount: Long?,
    val cashAmount: BigDecimal?,
    val productBreakdown: List<ProductBreakdownDto>?
)

data class PaymentAnalyticsDto(
    val totalCollected: BigDecimal?,
    val totalPayments: Long?
)

data class ProductPurchaseDto(
    val productName: String?,
    val quantity: Double?
)

data class ProductBreakdownDto(
    val productName: String?,
    val quantity: BigDecimal?,
    val amount: BigDecimal?
)
