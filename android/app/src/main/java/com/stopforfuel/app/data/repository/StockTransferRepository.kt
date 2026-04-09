package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.CreateStockTransferRequest
import com.stopforfuel.app.data.remote.dto.StockTransferDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockTransferRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun createTransfer(request: CreateStockTransferRequest): Result<StockTransferDto> {
        return try {
            Result.success(api.createStockTransfer(request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTransfers(
        productId: Long? = null,
        from: String? = null,
        to: String? = null
    ): Result<List<StockTransferDto>> {
        return try {
            Result.success(api.getStockTransfers(productId, from, to))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
