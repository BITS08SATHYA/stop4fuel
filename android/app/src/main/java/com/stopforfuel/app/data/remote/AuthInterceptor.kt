package com.stopforfuel.app.data.remote

import com.stopforfuel.app.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Skip auth header for the unauthenticated login steps. /mfa/verify is
        // authorised by the mfaToken in its body, not a Bearer token (none exists yet).
        val path = request.url.encodedPath
        if (path.contains("auth/login") || path.contains("auth/mfa/verify")) {
            return chain.proceed(request)
        }

        val token = tokenStore.getToken()
        return if (token != null) {
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(request)
        }
    }
}
