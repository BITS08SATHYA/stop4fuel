package com.stopforfuel.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.stopforfuel.app.data.remote.dto.UserDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "s4f_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAuth(token: String, user: UserDto) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_USER_ROLE, user.role)
            .putString(KEY_USER_PHONE, user.phone)
            .putString(KEY_USER_DESIGNATION, user.designation)
            .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + TOKEN_TTL_MS)
            .apply()
    }

    fun getToken(): String? {
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        if (System.currentTimeMillis() > expiry) {
            clear()
            return null
        }
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getUserId(): Long = prefs.getLong(KEY_USER_ID, 0)
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, null)
    fun getUserPhone(): String? = prefs.getString(KEY_USER_PHONE, null)
    fun getUserDesignation(): String? = prefs.getString(KEY_USER_DESIGNATION, null)

    fun isLoggedIn(): Boolean = getToken() != null

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_DESIGNATION = "user_designation"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val TOKEN_TTL_MS = 8 * 60 * 60 * 1000L // 8 hours
    }
}
