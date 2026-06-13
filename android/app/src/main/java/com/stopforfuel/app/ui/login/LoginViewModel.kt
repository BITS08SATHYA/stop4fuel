package com.stopforfuel.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phone: String = "",
    val passcode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    // MFA (second factor) step
    val mfaRequired: Boolean = false,
    val mfaEnrolled: Boolean = true,
    val mfaToken: String? = null,
    val qrDataUri: String? = null,
    val manualKey: String? = null,
    val totpCode: String = ""
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // Check if already logged in
        if (authRepository.isLoggedIn()) {
            _uiState.value = _uiState.value.copy(isLoggedIn = true)
        }
        // Pre-fill phone if available
        authRepository.getUserPhone()?.let { phone ->
            _uiState.value = _uiState.value.copy(phone = phone)
        }
    }

    fun updatePhone(phone: String) {
        _uiState.value = _uiState.value.copy(phone = phone, error = null)
    }

    fun appendPin(digit: String) {
        val current = _uiState.value.passcode
        if (current.length < 4) {
            val newPin = current + digit
            _uiState.value = _uiState.value.copy(passcode = newPin, error = null)
            if (newPin.length == 4) {
                login()
            }
        }
    }

    fun clearPin() {
        _uiState.value = _uiState.value.copy(passcode = "", error = null)
    }

    fun deleteLastDigit() {
        val current = _uiState.value.passcode
        if (current.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(passcode = current.dropLast(1), error = null)
        }
    }

    private fun login() {
        val state = _uiState.value
        if (state.phone.isBlank()) {
            _uiState.value = state.copy(error = "Enter your phone number")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.login(state.phone, state.passcode)
            result.fold(
                onSuccess = { resp ->
                    if (resp.mfaRequired) {
                        // Passcode OK — move to the TOTP step, no session yet.
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            mfaRequired = true,
                            mfaEnrolled = resp.enrolled,
                            mfaToken = resp.mfaToken,
                            qrDataUri = resp.enrollment?.qrDataUri,
                            manualKey = resp.enrollment?.manualKey,
                            totpCode = "",
                            error = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                    }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        passcode = "",
                        error = "Invalid phone or passcode"
                    )
                }
            )
        }
    }

    // ---- TOTP second factor ----

    fun appendTotp(digit: String) {
        val current = _uiState.value.totpCode
        if (current.length < 6) {
            val next = current + digit
            _uiState.value = _uiState.value.copy(totpCode = next, error = null)
            if (next.length == 6) {
                verifyMfa()
            }
        }
    }

    fun clearTotp() {
        _uiState.value = _uiState.value.copy(totpCode = "", error = null)
    }

    fun deleteLastTotpDigit() {
        val current = _uiState.value.totpCode
        if (current.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(totpCode = current.dropLast(1), error = null)
        }
    }

    /** Return from the TOTP step back to phone + passcode entry. */
    fun backToCredentials() {
        _uiState.value = _uiState.value.copy(
            mfaRequired = false,
            mfaEnrolled = true,
            mfaToken = null,
            qrDataUri = null,
            manualKey = null,
            totpCode = "",
            passcode = "",
            error = null
        )
    }

    private fun verifyMfa() {
        val token = _uiState.value.mfaToken ?: return
        val code = _uiState.value.totpCode

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.verifyMfa(token, code)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        totpCode = "",
                        error = "Incorrect code. Please try again."
                    )
                }
            )
        }
    }
}
