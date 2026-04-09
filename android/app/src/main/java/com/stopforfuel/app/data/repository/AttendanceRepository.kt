package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.AttendanceBulkRequest
import com.stopforfuel.app.data.remote.dto.AttendanceDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getDailyAttendance(date: String): Result<List<AttendanceDto>> {
        return try {
            Result.success(api.getDailyAttendance(date))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markBulkAttendance(attendances: List<AttendanceBulkRequest>): Result<List<AttendanceDto>> {
        return try {
            Result.success(api.markBulkAttendance(attendances))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
