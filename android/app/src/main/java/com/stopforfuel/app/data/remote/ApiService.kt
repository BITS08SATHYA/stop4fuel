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

    // Customer Management
    @GET("api/customers/{id}")
    suspend fun getCustomer(@Path("id") id: Long): CustomerListDto

    @GET("api/customers/{id}/credit-info")
    suspend fun getCreditInfo(@Path("id") id: Long): CreditInfoResponse

    @PATCH("api/customers/{id}/credit-limits")
    suspend fun updateCreditLimits(
        @Path("id") id: Long,
        @Body limits: CreditLimitUpdateRequest
    ): CustomerListDto

    @PATCH("api/customers/{id}/toggle-status")
    suspend fun toggleCustomerStatus(@Path("id") id: Long): CustomerListDto

    @PATCH("api/customers/{id}/block")
    suspend fun blockCustomer(
        @Path("id") id: Long,
        @Body body: Map<String, String> = emptyMap()
    ): CustomerListDto

    @PATCH("api/customers/{id}/unblock")
    suspend fun unblockCustomer(
        @Path("id") id: Long,
        @Body body: Map<String, String> = emptyMap()
    ): CustomerListDto

    // Vehicle Management
    @PATCH("api/vehicles/{id}/toggle-status")
    suspend fun toggleVehicleStatus(@Path("id") id: Long): VehicleDto

    @PATCH("api/vehicles/{id}/block")
    suspend fun blockVehicle(@Path("id") id: Long): VehicleDto

    @PATCH("api/vehicles/{id}/unblock")
    suspend fun unblockVehicle(@Path("id") id: Long): VehicleDto

    @PATCH("api/vehicles/{id}/liter-limit")
    suspend fun updateVehicleLiterLimit(
        @Path("id") id: Long,
        @Body body: LiterLimitRequest
    ): VehicleDto

    // Admin / Employee Management
    @GET("api/admin/users")
    suspend fun getAdminUsers(
        @Query("type") type: String? = "EMPLOYEE",
        @Query("search") search: String? = null
    ): List<AdminUserDto>

    @POST("api/admin/users/{id}/reset-passcode")
    suspend fun resetPasscode(@Path("id") id: Long): PasscodeResetResponse

    @GET("api/admin/users/passcode-reset-requests")
    suspend fun getPasscodeResetRequests(
        @Query("status") status: String? = null
    ): List<PasscodeResetRequestDto>

    @POST("api/admin/users/passcode-reset-requests/{id}/approve")
    suspend fun approveResetRequest(@Path("id") id: Long): PasscodeApproveResponse

    @POST("api/admin/users/passcode-reset-requests/{id}/reject")
    suspend fun rejectResetRequest(@Path("id") id: Long)

    // Dashboard
    @GET("api/dashboard/stats")
    suspend fun getDashboardStats(): DashboardStatsDto

    @GET("api/dashboard/system-health")
    suspend fun getSystemHealth(): SystemHealthDto

    // Products Management
    @PUT("api/products/{id}")
    suspend fun updateProduct(@Path("id") id: Long, @Body product: Map<String, Any?>): ProductDto

    @POST("api/product-price-history")
    suspend fun createPriceHistory(@Body entry: Map<String, Any?>): Any

    // Vehicle Creation
    @POST("api/vehicles")
    suspend fun createVehicle(@Body vehicle: Map<String, Any?>): VehicleDto

    @GET("api/vehicle-types")
    suspend fun getVehicleTypes(): List<VehicleTypeDto>

    // Employee Status Toggle
    @PATCH("api/admin/users/{id}/toggle-status")
    suspend fun toggleUserStatus(@Path("id") id: Long): AdminUserDto

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
