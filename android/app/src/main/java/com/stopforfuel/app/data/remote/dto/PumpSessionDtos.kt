package com.stopforfuel.app.data.remote.dto

import java.math.BigDecimal

data class StartSessionRequest(
    val pumpId: Long,
    val readings: List<OpenReadingInput>
)

data class OpenReadingInput(
    val nozzleId: Long,
    val openReading: BigDecimal
)

data class CloseSessionRequest(
    val readings: List<CloseReadingInput>
)

data class CloseReadingInput(
    val nozzleId: Long,
    val closeReading: BigDecimal
)

data class PumpSessionDto(
    val id: Long,
    val shiftId: Long?,
    val status: String?,
    val startTime: String?,
    val endTime: String?,
    val pump: PumpSummary?,
    val attendant: AttendantSummary?,
    val readings: List<SessionReadingDto>?,
    val totalLiters: BigDecimal?,
    val totalSales: BigDecimal?
)

data class SessionReadingDto(
    val id: Long?,
    val nozzleId: Long?,
    val nozzleName: String?,
    val productName: String?,
    val productPrice: BigDecimal?,
    val openReading: BigDecimal?,
    val closeReading: BigDecimal?,
    val litersSold: BigDecimal?,
    val salesAmount: BigDecimal?
)
