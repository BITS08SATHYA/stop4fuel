package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PumpSessionRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun startSession(request: StartSessionRequest): Result<PumpSessionDto> {
        return try {
            Result.success(api.startPumpSession(request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeSession(id: Long, request: CloseSessionRequest): Result<PumpSessionDto> {
        return try {
            Result.success(api.closePumpSession(id, request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getActiveSession(): PumpSessionDto? {
        return try {
            api.getActivePumpSession()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSession(id: Long): Result<PumpSessionDto> {
        return try {
            Result.success(api.getPumpSession(id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
