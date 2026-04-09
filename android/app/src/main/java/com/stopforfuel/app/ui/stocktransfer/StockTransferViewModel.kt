package com.stopforfuel.app.ui.stocktransfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.*
import com.stopforfuel.app.data.repository.LookupRepository
import com.stopforfuel.app.data.repository.StockTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class StockTransferUiState(
    val products: List<ProductDto> = emptyList(),
    val selectedProduct: ProductDto? = null,
    val direction: TransferDirection = TransferDirection.GODOWN_TO_CASHIER,
    val quantityInput: String = "",
    val remarks: String = "",
    val recentTransfers: List<StockTransferDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

enum class TransferDirection(val from: String, val to: String, val label: String) {
    GODOWN_TO_CASHIER("GODOWN", "CASHIER", "Godown → Cashier"),
    CASHIER_TO_GODOWN("CASHIER", "GODOWN", "Cashier → Godown")
}

@HiltViewModel
class StockTransferViewModel @Inject constructor(
    private val stockTransferRepository: StockTransferRepository,
    private val lookupRepository: LookupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockTransferUiState())
    val uiState: StateFlow<StockTransferUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val products = lookupRepository.getProducts()
                val transfers = stockTransferRepository.getTransfers().getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    products = products,
                    selectedProduct = products.firstOrNull(),
                    recentTransfers = transfers.take(20)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun selectProduct(product: ProductDto) {
        _uiState.value = _uiState.value.copy(selectedProduct = product)
    }

    fun setDirection(direction: TransferDirection) {
        _uiState.value = _uiState.value.copy(direction = direction)
    }

    fun updateQuantity(qty: String) {
        _uiState.value = _uiState.value.copy(quantityInput = qty)
    }

    fun updateRemarks(text: String) {
        _uiState.value = _uiState.value.copy(remarks = text)
    }

    fun clearForm() {
        _uiState.value = _uiState.value.copy(
            quantityInput = "",
            remarks = "",
            successMessage = null,
            error = null
        )
    }

    fun submitTransfer() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val qty = state.quantityInput.toDoubleOrNull() ?: return
        if (qty <= 0) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, successMessage = null)
            val request = CreateStockTransferRequest(
                product = IdRef(product.id),
                quantity = qty,
                fromLocation = state.direction.from,
                toLocation = state.direction.to,
                transferDate = LocalDate.now().toString(),
                remarks = state.remarks.ifBlank { null }
            )
            stockTransferRepository.createTransfer(request).fold(
                onSuccess = {
                    val transfers = stockTransferRepository.getTransfers().getOrDefault(emptyList())
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = "Transfer created: ${qty}x ${product.name}",
                        quantityInput = "",
                        remarks = "",
                        recentTransfers = transfers.take(20)
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = e.message ?: "Failed to create transfer"
                    )
                }
            )
        }
    }
}
