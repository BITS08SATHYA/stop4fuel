package com.stopforfuel.app.data.remote.dto

import java.math.BigDecimal

// --- Customer Management ---

data class CreditLimitUpdateRequest(
    val creditLimitAmount: BigDecimal?,
    val creditLimitLiters: BigDecimal?
)

data class CreditInfoResponse(
    val creditLimitAmount: BigDecimal?,
    val creditLimitLiters: BigDecimal?,
    val totalCredit: BigDecimal?,
    val consumedLiters: BigDecimal?,
    val totalPaid: BigDecimal?,
    val balance: BigDecimal?
)

data class BlockingGateDto(
    val key: String,
    val label: String,
    val state: String,                   // PASS | WARN | FAIL | SKIPPED
    val value: Any? = null,              // JSON primitive — could be String, Number, null
    val limit: Any? = null,
    val detail: String? = null,
    val progressPercent: Int? = null
)

data class BlockingStatusResponse(
    val customerId: Long,
    val customerName: String,
    val overall: String,                 // PASS | WARN | BLOCKED | OVERRIDE
    val forceUnblocked: Boolean,
    val primaryReason: String?,
    val suggestedAction: String?,
    val gates: List<BlockingGateDto>
)

// --- Vehicle Management ---

data class LiterLimitRequest(
    val maxLitersPerMonth: BigDecimal?
)

// --- Admin / Employee Management ---

data class AdminUserDto(
    val id: Long,
    val name: String?,
    val phone: String?,
    val email: String?,
    val role: String?,
    val designation: String?,
    val userType: String?,
    val status: String?,
    val joinDate: String?,
    val employeeCode: String?,
    val lastLoginAt: String?
)

data class PasscodeResetResponse(
    val passcode: String
)

data class PasscodeResetRequestDto(
    val id: Long,
    val userId: Long?,
    val userName: String?,
    val phone: String?,
    val status: String?,
    val requestedAt: String?,
    val processedAt: String?
)

data class PasscodeApproveResponse(
    val passcode: String?,
    val userName: String?,
    val phone: String?
)

data class VehicleTypeDto(
    val id: Long,
    val typeName: String?
)

// --- Product Management ---

data class PriceUpdateRequest(
    val price: BigDecimal
)

data class CreatePriceHistoryRequest(
    val product: ProductIdRef,
    val price: BigDecimal,
    val effectiveDate: String
)

data class ProductIdRef(val id: Long)

// --- Force Unblock ---

data class ForceUnblockRequest(
    val enabled: Boolean,
    val byUser: String = "Mobile App"
)

// --- Vehicle Creation ---

data class CreateVehicleRequest(
    val vehicleNumber: String,
    val customer: IdRef,
    val vehicleType: IdRef? = null,
    val preferredProduct: IdRef? = null,
    val maxCapacity: BigDecimal? = null,
    val maxLitersPerMonth: BigDecimal? = null
)
