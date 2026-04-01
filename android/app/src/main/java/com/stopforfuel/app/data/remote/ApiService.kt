package com.stopforfuel.app.data.remote

import com.stopforfuel.app.data.remote.dto.*
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    // Shift
    @GET("api/shifts/active")
    suspend fun getActiveShift(): ShiftDto?

    // Products & Nozzles
    @GET("api/products/active")
    suspend fun getActiveProducts(): List<ProductDto>

    @GET("api/nozzles/active")
    suspend fun getActiveNozzles(): List<NozzleDto>

    @GET("api/pumps/active")
    suspend fun getActivePumps(): List<PumpDto>

    // Customers
    @GET("api/customers")
    suspend fun searchCustomers(
        @Query("search") search: String,
        @Query("size") size: Int = 10
    ): PageResponse<CustomerListDto>

    @GET("api/customers/{id}/vehicles")
    suspend fun getCustomerVehicles(@Path("id") customerId: Long): List<VehicleDto>

    // Invoices
    @POST("api/invoices")
    suspend fun createInvoice(@Body invoice: CreateInvoiceRequest): InvoiceBillDto

    @GET("api/invoices/shift/{shiftId}")
    suspend fun getInvoicesByShift(@Path("shiftId") shiftId: Long): List<InvoiceBillDto>

    // Pump Sessions
    @POST("api/pump-sessions")
    suspend fun startPumpSession(@Body request: StartSessionRequest): PumpSessionDto

    @POST("api/pump-sessions/{id}/close")
    suspend fun closePumpSession(
        @Path("id") id: Long,
        @Body request: CloseSessionRequest
    ): PumpSessionDto

    @GET("api/pump-sessions/active")
    suspend fun getActivePumpSession(): PumpSessionDto?

    @GET("api/pump-sessions/{id}")
    suspend fun getPumpSession(@Path("id") id: Long): PumpSessionDto

    @GET("api/pump-sessions/shift/{shiftId}")
    suspend fun getPumpSessionsByShift(@Path("shiftId") shiftId: Long): List<PumpSessionDto>
}
