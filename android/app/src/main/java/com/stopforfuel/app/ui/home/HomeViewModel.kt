package com.stopforfuel.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.*
import com.stopforfuel.app.data.repository.AuthRepository
import com.stopforfuel.app.data.repository.DashboardRepository
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
    val dashboardStats: DashboardStatsDto? = null,
    val systemHealth: SystemHealthDto? = null,
    val cashierDashboard: CashierDashboardDto? = null,
    val fuelProducts: List<ProductDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val isManager: Boolean
        get() = userRole.uppercase().let { it == "OWNER" || it == "MANAGER" }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shiftRepository: ShiftRepository,
    private val lookupRepository: LookupRepository,
    private val pumpSessionRepository: PumpSessionRepository,
    private val dashboardRepository: DashboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val role = authRepository.getUserRole() ?: ""
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                userName = authRepository.getUserName() ?: "",
                userRole = role
            )

            try {
                val shift = shiftRepository.fetchActiveShift()
                val pumpSession = pumpSessionRepository.getActiveSession()

                // Pre-fetch lookups
                lookupRepository.getProducts()
                lookupRepository.getNozzles()
                lookupRepository.getPumps()

                val isManager = role.uppercase().let { it == "OWNER" || it == "MANAGER" }

                if (isManager) {
                    // Owner/Manager: full dashboard
                    val stats = dashboardRepository.getStats().getOrNull()
                    val health = dashboardRepository.getSystemHealth().getOrNull()
                    val products = dashboardRepository.getProducts().getOrNull()
                        ?.filter { it.category.equals("FUEL", ignoreCase = true) }
                        ?: emptyList()

                    _uiState.value = _uiState.value.copy(
                        activeShift = shift,
                        activePumpSession = pumpSession,
                        dashboardStats = stats,
                        systemHealth = health,
                        fuelProducts = products,
                        isLoading = false, error = null
                    )
                } else {
                    // Cashier/Employee: shift-focused dashboard
                    val cashier = dashboardRepository.getCashierDashboard().getOrNull()

                    _uiState.value = _uiState.value.copy(
                        activeShift = shift,
                        activePumpSession = pumpSession,
                        cashierDashboard = cashier,
                        isLoading = false, error = null
                    )
                }
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
