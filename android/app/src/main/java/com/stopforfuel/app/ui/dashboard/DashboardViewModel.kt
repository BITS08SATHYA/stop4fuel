package com.stopforfuel.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.*
import com.stopforfuel.app.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DashboardUiState(
    val stats: DashboardStatsDto? = null,
    val health: SystemHealthDto? = null,
    val fuelProducts: List<ProductDto> = emptyList(),
    val backendHealth: BackendHealthDto? = null,
    val awsBilling: AwsBillingDto? = null,
    val mtdProductSales: List<ProductBreakdownDto> = emptyList(),
    val mtdInvoiceAnalytics: InvoiceAnalyticsDto? = null,
    val mtdPaymentAnalytics: PaymentAnalyticsDto? = null,
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
            val backendHealthResult = repository.getBackendHealth()
            val awsBillingResult = repository.getAwsBilling()

            val today = LocalDate.now()
            val monthStart = today.withDayOfMonth(1)
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            val mtdFrom = monthStart.format(fmt)
            val mtdTo = today.format(fmt)
            val mtdResult = repository.getInvoiceAnalytics(mtdFrom, mtdTo)
            val mtdPayments = repository.getPaymentAnalytics(mtdFrom, mtdTo)

            statsResult.fold(
                onSuccess = { stats ->
                    val fuelProducts = productsResult.getOrDefault(emptyList())
                        .filter { it.category?.uppercase() == "FUEL" }
                    val mtdAnalytics = mtdResult.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        stats = stats,
                        health = healthResult.getOrNull(),
                        fuelProducts = fuelProducts,
                        backendHealth = backendHealthResult.getOrNull(),
                        awsBilling = awsBillingResult.getOrNull(),
                        mtdProductSales = mtdAnalytics?.productBreakdown ?: emptyList(),
                        mtdInvoiceAnalytics = mtdAnalytics,
                        mtdPaymentAnalytics = mtdPayments.getOrNull(),
                        isLoading = false
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }
}
