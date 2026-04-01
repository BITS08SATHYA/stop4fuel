package com.stopforfuel.app.data.remote.dto

import java.math.BigDecimal

data class ProductDto(
    val id: Long,
    val name: String?,
    val category: String?,
    val price: BigDecimal?,
    val unit: String?,
    val fuelFamily: String?,
    val gstRate: BigDecimal?,
    val discountRate: BigDecimal?,
    val active: Boolean?
)

data class NozzleDto(
    val id: Long,
    val nozzleName: String?,
    val nozzleNumber: String?,
    val tank: TankSummary?,
    val pump: PumpSummary?,
    val active: Boolean?
)

data class TankSummary(
    val id: Long?,
    val name: String?,
    val productId: Long?,
    val productName: String?
)

data class PumpSummary(
    val id: Long?,
    val name: String?
)

data class PumpDto(
    val id: Long,
    val name: String?,
    val active: Boolean?
)

data class CustomerListDto(
    val id: Long,
    val name: String?,
    val username: String?,
    val phoneNumbers: String?,
    val status: String?
)

data class VehicleDto(
    val id: Long,
    val vehicleNumber: String?,
    val vehicleType: String?,
    val maxCapacity: Double?,
    val maxLitersPerMonth: Double?,
    val consumedLiters: Double?,
    val preferredProduct: String?,
    val status: String?
)

data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long?,
    val totalPages: Int?,
    val number: Int?,
    val size: Int?
)
