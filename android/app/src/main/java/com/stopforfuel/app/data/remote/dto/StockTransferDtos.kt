package com.stopforfuel.app.data.remote.dto

data class StockTransferDto(
    val id: Long,
    val quantity: Double?,
    val fromLocation: String?,
    val toLocation: String?,
    val transferDate: String?,
    val remarks: String?,
    val transferredBy: String?,
    val product: StockTransferProductSummary?,
    val shiftId: Long?
)

data class StockTransferProductSummary(
    val id: Long?,
    val name: String?
)

data class CreateStockTransferRequest(
    val product: IdRef,
    val quantity: Double,
    val fromLocation: String,
    val toLocation: String,
    val transferDate: String?,
    val remarks: String?
)
