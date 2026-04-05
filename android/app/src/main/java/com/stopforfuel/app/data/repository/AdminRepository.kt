package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getEmployees(search: String? = null): Result<List<AdminUserDto>> = runCatching {
        api.getAdminUsers(type = "EMPLOYEE", search = search)
    }

    suspend fun resetPasscode(userId: Long): Result<PasscodeResetResponse> = runCatching {
        api.resetPasscode(userId)
    }

    suspend fun getResetRequests(status: String? = null): Result<List<PasscodeResetRequestDto>> = runCatching {
        api.getPasscodeResetRequests(status)
    }

    suspend fun approveResetRequest(requestId: Long): Result<PasscodeApproveResponse> = runCatching {
        api.approveResetRequest(requestId)
    }

    suspend fun rejectResetRequest(requestId: Long): Result<Unit> = runCatching {
        api.rejectResetRequest(requestId)
    }
}
