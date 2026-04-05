package com.stopforfuel.app.data.remote.dto

data class LoginRequest(
    val phone: String,
    val passcode: String
)

data class LoginResponse(
    val token: String,
    val user: UserDto
)

data class UserDto(
    val id: Long,
    val cognitoId: String?,
    val username: String?,
    val name: String?,
    val email: String?,
    val phone: String?,
    val role: String?,
    val status: String?,
    val designation: String?,
    val permissions: List<String>?
)
