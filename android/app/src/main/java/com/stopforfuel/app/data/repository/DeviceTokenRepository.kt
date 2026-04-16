package com.stopforfuel.app.data.repository

import com.google.firebase.messaging.FirebaseMessaging
import com.stopforfuel.app.data.remote.ApiService
import kotlinx.coroutines.tasks.await
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

    /** Fetch the current FCM token and register it with the backend for the logged-in user. */
    suspend fun registerCurrent(): Result<Unit> = runCatching {
        val token = FirebaseMessaging.getInstance().token.await()
        api.registerDeviceToken(mapOf("fcmToken" to token, "platform" to "ANDROID"))
        Unit
    }

    /** Fetch the current FCM token and ask the backend to forget it. Must run before auth is cleared. */
    suspend fun unregisterCurrent(): Result<Unit> = runCatching {
        val token = FirebaseMessaging.getInstance().token.await()
        api.unregisterDeviceToken(mapOf("fcmToken" to token))
        Unit
    }
}
