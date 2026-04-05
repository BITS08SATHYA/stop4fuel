package com.stopforfuel.app.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.ProductDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ProductManageState(
    val products: List<ProductDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val expandedProductId: Long? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class ProductManageViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductManageState())
    val uiState = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { api.getActiveProducts() }.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(products = it, isLoading = false) },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun toggleExpand(productId: Long) {
        val current = _uiState.value.expandedProductId
        _uiState.value = _uiState.value.copy(expandedProductId = if (current == productId) null else productId)
    }

    fun updatePrice(productId: Long, newPrice: String) {
        val priceVal = newPrice.toBigDecimalOrNull() ?: return
        viewModelScope.launch {
            runCatching {
                api.updateProduct(productId, mapOf("price" to priceVal))
                api.createPriceHistory(mapOf(
                    "product" to mapOf("id" to productId),
                    "price" to priceVal,
                    "effectiveDate" to LocalDate.now().toString()
                ))
            }.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(actionMessage = "Price updated")
                    refresh()
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(actionMessage = null) }
}
