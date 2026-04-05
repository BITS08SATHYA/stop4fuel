package com.stopforfuel.app.data.remote.dto

data class ShiftDto(
    val id: Long,
    val startTime: String?,
    val endTime: String?,
    val status: String?,
    val attendant: AttendantSummary?,
    val scid: Long?
)

data class AttendantSummary(
    val id: Long?,
    val name: String?,
    val username: String?
)
