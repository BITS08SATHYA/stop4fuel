package com.stopforfuel.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.PumpSessionDto
import com.stopforfuel.app.data.remote.dto.ShiftDto
import com.stopforfuel.app.data.repository.AuthRepository
import com.stopforfuel.app.data.repository.LookupRepository
import com.stopforfuel.app.data.repository.PumpSessionRepository
import com.stopforfuel.app.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val userRole: String = "",
    val activeShift: ShiftDto? = null,
    val activePumpSession: PumpSessionDto? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shiftRepository: ShiftRepository,
    private val lookupRepository: LookupRepository,
    private val pumpSessionRepository: PumpSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                userName = authRepository.getUserName() ?: "",
                userRole = authRepository.getUserRole() ?: ""
            )

            try {
                // Fetch shift + pre-cache lookups in parallel
                val shift = shiftRepository.fetchActiveShift()
                val pumpSession = pumpSessionRepository.getActiveSession()

                // Pre-fetch products and nozzles for invoice creation
                lookupRepository.getProducts()
                lookupRepository.getNozzles()
                lookupRepository.getPumps()

                _uiState.value = _uiState.value.copy(
                    activeShift = shift,
                    activePumpSession = pumpSession,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load data: ${e.message}"
                )
            }
        }
    }

    fun logout() {
        authRepository.logout()
        lookupRepository.invalidateCache()
    }
}
