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
    val isLoggedIn: Boolean = false
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
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        passcode = "",
                        error = "Invalid phone or passcode"
                    )
                }
            )
        }
    }
}
