package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.*
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerManageRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun searchCustomers(query: String): Result<List<CustomerListDto>> = runCatching {
        api.searchCustomers(query, 50).content
    }

    suspend fun getCustomer(id: Long): Result<CustomerListDto> = runCatching {
        api.getCustomer(id)
    }

    suspend fun getCreditInfo(id: Long): Result<CreditInfoResponse> = runCatching {
        api.getCreditInfo(id)
    }

    suspend fun updateCreditLimits(id: Long, amount: BigDecimal?, liters: BigDecimal?): Result<CustomerListDto> = runCatching {
        api.updateCreditLimits(id, CreditLimitUpdateRequest(amount, liters))
    }

    suspend fun toggleStatus(id: Long): Result<CustomerListDto> = runCatching {
        api.toggleCustomerStatus(id)
    }

    suspend fun block(id: Long, notes: String? = null): Result<CustomerListDto> = runCatching {
        val body = if (notes != null) mapOf("notes" to notes) else emptyMap()
        api.blockCustomer(id, body)
    }

    suspend fun unblock(id: Long, notes: String? = null): Result<CustomerListDto> = runCatching {
        val body = if (notes != null) mapOf("notes" to notes) else emptyMap()
        api.unblockCustomer(id, body)
    }

    suspend fun getVehicles(customerId: Long): Result<List<VehicleDto>> = runCatching {
        api.getCustomerVehicles(customerId)
    }

    suspend fun toggleVehicleStatus(id: Long): Result<VehicleDto> = runCatching {
        api.toggleVehicleStatus(id)
    }

    suspend fun blockVehicle(id: Long): Result<VehicleDto> = runCatching {
        api.blockVehicle(id)
    }

    suspend fun unblockVehicle(id: Long): Result<VehicleDto> = runCatching {
        api.unblockVehicle(id)
    }

    suspend fun updateVehicleLiterLimit(id: Long, limit: BigDecimal?): Result<VehicleDto> = runCatching {
        api.updateVehicleLiterLimit(id, LiterLimitRequest(limit))
    }

    suspend fun createVehicle(customerId: Long, vehicleNumber: String, vehicleTypeId: Long?, maxLitersPerMonth: BigDecimal?): Result<VehicleDto> = runCatching {
        api.createVehicle(CreateVehicleRequest(
            vehicleNumber = vehicleNumber,
            customer = IdRef(customerId),
            vehicleType = vehicleTypeId?.let { IdRef(it) },
            maxLitersPerMonth = maxLitersPerMonth
        ))
    }

    suspend fun toggleForceUnblock(id: Long, enabled: Boolean): Result<CustomerListDto> = runCatching {
        api.toggleForceUnblock(id, ForceUnblockRequest(enabled))
    }

    suspend fun getVehicleTypes(): Result<List<VehicleTypeDto>> = runCatching {
        api.getVehicleTypes()
    }
}
