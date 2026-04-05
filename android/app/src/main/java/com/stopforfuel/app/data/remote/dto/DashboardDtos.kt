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
