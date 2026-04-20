package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LookupRepository @Inject constructor(
    private val api: ApiService
) {
    private var productsCache: List<ProductDto>? = null
    private var nozzlesCache: List<NozzleDto>? = null
    private var pumpsCache: List<PumpDto>? = null

    suspend fun getProducts(): List<ProductDto> {
        return productsCache ?: api.getActiveProducts().also { productsCache = it }
    }

    suspend fun getNozzles(): List<NozzleDto> {
        return nozzlesCache ?: api.getActiveNozzles().also { nozzlesCache = it }
    }

    suspend fun getPumps(): List<PumpDto> {
        return pumpsCache ?: api.getActivePumps().also { pumpsCache = it }
    }

    suspend fun searchCustomers(query: String): List<CustomerListDto> {
        return try {
            api.searchCustomers(query).content
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCustomerVehicles(customerId: Long): List<VehicleDto> {
        return api.getCustomerVehicles(customerId)
    }

    suspend fun getBlockingStatus(
        customerId: Long,
        vehicleId: Long? = null,
        invoiceAmount: java.math.BigDecimal? = null,
        invoiceLiters: java.math.BigDecimal? = null
    ): BlockingStatusResponse? = try {
        api.getBlockingStatus(customerId, vehicleId, invoiceAmount, invoiceLiters)
    } catch (e: Exception) {
        null
    }

    fun invalidateCache() {
        productsCache = null
        nozzlesCache = null
        pumpsCache = null
    }
}
