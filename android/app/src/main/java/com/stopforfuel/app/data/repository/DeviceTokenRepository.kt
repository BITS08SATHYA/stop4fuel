package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceTokenRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun register(fcmToken: String): Result<Unit> = runCatching {
        api.registerDeviceToken(mapOf("fcmToken" to fcmToken, "platform" to "ANDROID"))
        Unit
    }
}
