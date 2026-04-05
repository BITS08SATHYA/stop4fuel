package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.ShiftDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepository @Inject constructor(
    private val api: ApiService
) {
    private var activeShift: ShiftDto? = null

    suspend fun fetchActiveShift(): ShiftDto? {
        activeShift = try {
            api.getActiveShift()
        } catch (e: Exception) {
            null
        }
        return activeShift
    }

    fun getCachedShift(): ShiftDto? = activeShift

    fun getShiftId(): Long? = activeShift?.id
}
