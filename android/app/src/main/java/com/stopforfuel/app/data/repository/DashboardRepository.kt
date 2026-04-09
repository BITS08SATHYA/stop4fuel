package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getStats(): Result<DashboardStatsDto> = runCatching {
        api.getDashboardStats()
    }

    suspend fun getSystemHealth(): Result<SystemHealthDto> = runCatching {
        api.getSystemHealth()
    }

    suspend fun getProducts(): Result<List<ProductDto>> = runCatching {
        api.getActiveProducts()
    }

    suspend fun getCashierDashboard(): Result<CashierDashboardDto> = runCatching {
        api.getCashierDashboard()
    }

    suspend fun getBackendHealth(): Result<BackendHealthDto> = runCatching {
        api.getBackendHealth()
    }

    suspend fun getAwsBilling(): Result<AwsBillingDto> = runCatching {
        api.getAwsBilling()
    }

    suspend fun getInvoiceAnalytics(from: String, to: String): Result<InvoiceAnalyticsDto> = runCatching {
        api.getInvoiceAnalytics(from, to)
    }

    suspend fun getPaymentAnalytics(from: String, to: String): Result<PaymentAnalyticsDto> = runCatching {
        api.getPaymentAnalytics(from, to)
    }
}
