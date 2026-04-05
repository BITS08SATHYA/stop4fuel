package com.stopforfuel.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.DashboardStatsDto
import com.stopforfuel.app.data.remote.dto.ProductDto
import com.stopforfuel.app.data.remote.dto.PumpSessionDto
import com.stopforfuel.app.data.remote.dto.ShiftDto
import com.stopforfuel.app.data.remote.dto.SystemHealthDto
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
    val fuelProducts: List<ProductDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

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

                // Load dashboard data
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
