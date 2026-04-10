package com.stopforfuel.app.ui.fastcash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.*
import com.stopforfuel.app.data.repository.InvoiceRepository
import com.stopforfuel.app.data.repository.LookupRepository
import com.stopforfuel.app.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class FastCashUiState(
    val products: List<ProductDto> = emptyList(),
    val nozzles: List<NozzleDto> = emptyList(),
    val selectedProduct: ProductDto? = null,
    val selectedNozzle: NozzleDto? = null,
    val amountInput: String = "",
    val paymentMode: String = "CASH",
    val shiftId: Long? = null,
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val successBillNo: String? = null
)

@HiltViewModel
class FastCashInvoiceViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val lookupRepository: LookupRepository,
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FastCashUiState())
    val uiState: StateFlow<FastCashUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val shift = shiftRepository.fetchActiveShift()
                val products = lookupRepository.getProducts()
                val nozzles = lookupRepository.getNozzles()

                val fuelProducts = products.filter { it.category?.uppercase() == "FUEL" }
                val defaultProduct = fuelProducts.firstOrNull()
                val defaultNozzle = defaultProduct?.let { p ->
                    nozzles.firstOrNull { it.tank?.productId == p.id }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    products = fuelProducts,
                    nozzles = nozzles,
                    selectedProduct = defaultProduct,
                    selectedNozzle = defaultNozzle,
                    shiftId = shift?.id
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun selectProduct(product: ProductDto) {
        val nozzle = _uiState.value.nozzles.firstOrNull { it.tank?.productId == product.id }
        _uiState.value = _uiState.value.copy(
            selectedProduct = product,
            selectedNozzle = nozzle
        )
    }

    fun appendDigit(digit: String) {
        val current = _uiState.value.amountInput
        if (digit == "." && current.contains(".")) return
        if (current == "0" && digit != ".") {
            _uiState.value = _uiState.value.copy(amountInput = digit)
        } else {
            _uiState.value = _uiState.value.copy(amountInput = current + digit)
        }
    }

    fun deleteLastDigit() {
        val current = _uiState.value.amountInput
        _uiState.value = _uiState.value.copy(
            amountInput = if (current.length <= 1) "" else current.dropLast(1)
        )
    }

    fun setQuickAmount(amount: Int) {
        _uiState.value = _uiState.value.copy(amountInput = amount.toString())
    }

    fun selectPaymentMode(mode: String) {
        _uiState.value = _uiState.value.copy(paymentMode = mode)
    }

    fun clearAmount() {
        _uiState.value = _uiState.value.copy(amountInput = "", successBillNo = null, error = null)
    }

    fun createInvoice() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val nozzle = state.selectedNozzle
        val amountStr = state.amountInput.ifBlank { return }
        val amount = amountStr.toBigDecimalOrNull() ?: return
        val unitPrice = product.price ?: return

        if (amount <= BigDecimal.ZERO) return

        val quantity = amount.divide(unitPrice, 3, RoundingMode.HALF_UP)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            val request = CreateInvoiceRequest(
                billType = "CASH",
                paymentMode = state.paymentMode,
                customer = null,
                vehicle = null,
                products = listOf(
                    InvoiceProductRequest(
                        product = IdRef(product.id),
                        nozzle = nozzle?.let { IdRef(it.id) },
                        quantity = quantity,
                        unitPrice = unitPrice
                    )
                ),
                driverName = null,
                driverPhone = null
            )
            invoiceRepository.createInvoice(request).fold(
                onSuccess = { invoice ->
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        successBillNo = invoice.billNo,
                        amountInput = ""
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        error = e.message ?: "Failed to create invoice"
                    )
                }
            )
        }
    }
}
