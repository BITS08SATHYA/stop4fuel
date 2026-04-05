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
}
