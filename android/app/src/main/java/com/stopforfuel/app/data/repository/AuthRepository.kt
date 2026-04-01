package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.local.TokenStore
import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.LoginRequest
import com.stopforfuel.app.data.remote.dto.LoginResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore
) {
    suspend fun login(phone: String, passcode: String): Result<LoginResponse> {
        return try {
            val response = api.login(LoginRequest(phone, passcode))
            tokenStore.saveAuth(response.token, response.user)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    fun getUserName(): String? = tokenStore.getUserName()
    fun getUserRole(): String? = tokenStore.getUserRole()
    fun getUserPhone(): String? = tokenStore.getUserPhone()

    fun logout() = tokenStore.clear()
}
