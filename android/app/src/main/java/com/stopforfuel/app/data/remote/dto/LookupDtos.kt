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
    val phoneNumbers: Set<String>?,
    val emails: Set<String>?,
    val address: String?,
    val personType: String?,
    val status: String?,
    val isActive: Boolean?,
    val creditLimitAmount: BigDecimal?,
    val creditLimitLiters: BigDecimal?,
    val consumedLiters: BigDecimal?,
    val group: GroupSummary?,
    val party: PartySummary?,
    val customerCategory: CategorySummary?
)

data class GroupSummary(
    val id: Long?,
    val groupName: String?
)

data class PartySummary(
    val id: Long?,
    val partyType: String?
)

data class CategorySummary(
    val id: Long?,
    val categoryName: String?,
    val categoryType: String?
)

data class VehicleDto(
    val id: Long,
    val vehicleNumber: String?,
    val status: String?,
    val isActive: Boolean?,
    val maxCapacity: BigDecimal?,
    val maxLitersPerMonth: BigDecimal?,
    val consumedLiters: BigDecimal?,
    val vehicleType: VehicleTypeSummary?,
    val preferredProduct: ProductSummaryRef?
)

data class VehicleTypeSummary(
    val id: Long?,
    val name: String?
)

data class ProductSummaryRef(
    val id: Long?,
    val name: String?,
    val fuelFamily: String?
)

data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long?,
    val totalPages: Int?,
    val number: Int?,
    val size: Int?
)
