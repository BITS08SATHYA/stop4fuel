package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.PageResponse
import com.stopforfuel.app.data.remote.dto.StatementDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatementRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getStatements(
        page: Int = 0,
        size: Int = 20,
        customerId: Long? = null,
        status: String? = null,
        fromDate: String? = null,
        toDate: String? = null,
        search: String? = null,
        categoryType: String? = null,
        sort: String? = null
    ): Result<PageResponse<StatementDto>> {
        return try {
            Result.success(api.getStatements(page, size, customerId, status, fromDate, toDate, search, categoryType, sort))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
