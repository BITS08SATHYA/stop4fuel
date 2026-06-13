package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.local.TokenStore
import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.LoginRequest
import com.stopforfuel.app.data.remote.dto.LoginResponse
import com.stopforfuel.app.data.remote.dto.MfaVerifyRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val deviceTokenRepository: DeviceTokenRepository
) {
    suspend fun login(phone: String, passcode: String): Result<LoginResponse> {
        return try {
            val response = api.login(LoginRequest(phone, passcode))
            // Only persist a session if one was actually issued. When MFA is required the
            // response carries an mfaToken instead — the caller must complete /mfa/verify.
            persistSessionIfPresent(response)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyMfa(mfaToken: String, totpCode: String): Result<LoginResponse> {
        return try {
            val response = api.verifyMfa(MfaVerifyRequest(mfaToken, totpCode))
            if (response.token != null && response.user != null) {
                persistSessionIfPresent(response)
                Result.success(response)
            } else {
                Result.failure(IllegalStateException("Verification did not return a session"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun persistSessionIfPresent(response: LoginResponse) {
        val token = response.token
        val user = response.user
        if (token != null && user != null) {
            tokenStore.saveAuth(token, user)
            // Register this device for push on every login. Handles the case where
            // FcmService.onNewToken fired before the user was logged in.
            deviceTokenRepository.registerCurrent()
        }
    }

    fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    fun getUserName(): String? = tokenStore.getUserName()
    fun getUserRole(): String? = tokenStore.getUserRole()
    fun getUserPhone(): String? = tokenStore.getUserPhone()

    /**
     * Logout must unregister the device token before clearing auth, since the
     * unregister endpoint requires a valid JWT.
     */
    suspend fun logout() {
        deviceTokenRepository.unregisterCurrent()
        tokenStore.clear()
    }
}
