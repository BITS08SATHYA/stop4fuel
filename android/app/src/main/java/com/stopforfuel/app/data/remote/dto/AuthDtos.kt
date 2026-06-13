package com.stopforfuel.app.data.remote.dto

data class LoginRequest(
    val phone: String,
    val passcode: String
)

/**
 * Shared by /api/auth/login and /api/auth/mfa/verify. After a correct passcode the
 * server returns mfaRequired=true with NO token/user yet — the second factor (TOTP)
 * must be verified first. token+user are only present once the session is issued
 * (i.e. after /mfa/verify, or directly from /login when MFA is disabled).
 */
data class LoginResponse(
    val token: String? = null,
    val user: UserDto? = null,
    val mfaRequired: Boolean = false,
    val enrolled: Boolean = false,
    val mfaToken: String? = null,
    val enrollment: MfaEnrollment? = null
)

data class MfaEnrollment(
    val qrDataUri: String? = null,
    val manualKey: String? = null
)

data class MfaVerifyRequest(
    val mfaToken: String,
    val totpCode: String
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
