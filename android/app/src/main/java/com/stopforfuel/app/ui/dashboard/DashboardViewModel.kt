package com.stopforfuel.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.*
import com.stopforfuel.app.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val stats: DashboardStatsDto? = null,
    val health: SystemHealthDto? = null,
    val fuelProducts: List<ProductDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DashboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val statsResult = repository.getStats()
            val healthResult = repository.getSystemHealth()
            val productsResult = repository.getProducts()

            statsResult.fold(
                onSuccess = { stats ->
                    val fuelProducts = productsResult.getOrDefault(emptyList())
                        .filter { it.category?.uppercase() == "FUEL" }
                    _uiState.value = _uiState.value.copy(
                        stats = stats,
                        health = healthResult.getOrNull(),
                        fuelProducts = fuelProducts,
                        isLoading = false
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }
}
