package com.stopforfuel.app.data.remote.dto

data class AttendanceDto(
    val id: Long?,
    val date: String?,
    val status: String?,
    val checkInTime: String?,
    val checkOutTime: String?,
    val remarks: String?,
    val employee: AttendanceEmployeeSummary?
)

data class AttendanceEmployeeSummary(
    val id: Long?,
    val name: String?,
    val employeeCode: String?,
    val designation: String?
)

data class AttendanceBulkRequest(
    val employee: IdRef,
    val date: String,
    val status: String,
    val remarks: String? = null
)
