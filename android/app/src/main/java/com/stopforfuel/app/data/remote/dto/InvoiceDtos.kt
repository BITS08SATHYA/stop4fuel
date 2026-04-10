package com.stopforfuel.app.data.remote.dto

import java.math.BigDecimal

data class CreateInvoiceRequest(
    val billType: String = "CASH",
    val paymentMode: String,
    val customer: IdRef?,
    val vehicle: IdRef?,
    val products: List<InvoiceProductRequest>,
    val driverName: String?,
    val driverPhone: String?
)

data class IdRef(val id: Long)

data class InvoiceProductRequest(
    val product: IdRef,
    val nozzle: IdRef?,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal
)

data class InvoiceBillDto(
    val id: Long,
    val billNo: String?,
    val billType: String?,
    val paymentMode: String?,
    val grossAmount: BigDecimal?,
    val totalDiscount: BigDecimal?,
    val netAmount: BigDecimal?,
    val status: String?,
    val paymentStatus: String?,
    val date: String?,
    val customer: CustomerSummaryDto?,
    val vehicle: VehicleSummaryDto?,
    val raisedBy: UserSummaryDto?,
    val products: List<InvoiceProductDto>?,
    val shiftId: Long?,
    val driverName: String?,
    val driverPhone: String?,
    val billPic: String? = null,
    val pumpBillPic: String? = null,
    val indentPic: String? = null
)

data class CustomerSummaryDto(
    val id: Long?,
    val name: String?,
    val username: String?
)

data class VehicleSummaryDto(
    val id: Long?,
    val vehicleNumber: String?
)

data class UserSummaryDto(
    val id: Long?,
    val name: String?,
    val username: String?
)

data class InvoiceProductDto(
    val id: Long?,
    val productId: Long?,
    val productName: String?,
    val nozzleId: Long?,
    val nozzleName: String?,
    val quantity: BigDecimal?,
    val unitPrice: BigDecimal?,
    val amount: BigDecimal?,
    val grossAmount: BigDecimal?,
    val discountRate: BigDecimal?,
    val discountAmount: BigDecimal?
)
