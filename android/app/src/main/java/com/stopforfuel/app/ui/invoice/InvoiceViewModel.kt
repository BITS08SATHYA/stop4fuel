package com.stopforfuel.app.ui.invoice

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

data class InvoiceLineItem(
    val product: ProductDto,
    val nozzle: NozzleDto?,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val amount: BigDecimal
)

data class InvoiceUiState(
    // Step
    val step: Int = 1,

    // Lookup data
    val products: List<ProductDto> = emptyList(),
    val nozzles: List<NozzleDto> = emptyList(),
    val shiftId: Long? = null,
    val shiftLabel: String = "",

    // Customer
    val isWalkIn: Boolean = true,
    val selectedCustomer: CustomerListDto? = null,
    val selectedVehicle: VehicleDto? = null,
    val customerSearchResults: List<CustomerListDto> = emptyList(),
    val customerVehicles: List<VehicleDto> = emptyList(),

    // Current product selection
    val selectedProduct: ProductDto? = null,
    val selectedNozzle: NozzleDto? = null,
    val quantityInput: String = "",
    val isRupeesMode: Boolean = false,

    // Added items
    val lineItems: List<InvoiceLineItem> = emptyList(),

    // Step 2 - Payment
    val paymentMode: String = "CASH",
    val driverName: String = "",
    val driverPhone: String = "",

    // State
    val isLoading: Boolean = false,
    val error: String? = null,
    val successBillNo: String? = null
) {
    val filteredNozzles: List<NozzleDto>
        get() = if (selectedProduct != null && selectedProduct.category.equals("Fuel", ignoreCase = true)) {
            nozzles.filter { it.tank?.productId == selectedProduct.id }
        } else emptyList()

    val totalAmount: BigDecimal
        get() = lineItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }

    val canAddToList: Boolean
        get() = selectedProduct != null
                && quantityInput.isNotBlank()
                && (selectedProduct.category != "Fuel" || selectedNozzle != null)
}

@HiltViewModel
class InvoiceViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val lookupRepository: LookupRepository,
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceUiState())
    val uiState: StateFlow<InvoiceUiState> = _uiState.asStateFlow()

    init {
        loadLookupData()
    }

    private fun loadLookupData() {
        viewModelScope.launch {
            val products = lookupRepository.getProducts()
            val nozzles = lookupRepository.getNozzles()
            val shift = shiftRepository.getCachedShift()
            _uiState.value = _uiState.value.copy(
                products = products,
                nozzles = nozzles,
                shiftId = shift?.id,
                shiftLabel = "Shift #${shift?.id ?: "?"}"
            )
        }
    }

    fun selectProduct(product: ProductDto) {
        _uiState.value = _uiState.value.copy(
            selectedProduct = product,
            selectedNozzle = null,
            quantityInput = "",
            isRupeesMode = false
        )
    }

    fun selectNozzle(nozzle: NozzleDto) {
        _uiState.value = _uiState.value.copy(selectedNozzle = nozzle)
    }

    fun appendQuantity(digit: String) {
        val current = _uiState.value.quantityInput
        // Prevent multiple dots
        if (digit == "." && current.contains(".")) return
        _uiState.value = _uiState.value.copy(quantityInput = current + digit)
    }

    fun clearQuantity() {
        _uiState.value = _uiState.value.copy(quantityInput = "")
    }

    fun deleteLastQuantityDigit() {
        val current = _uiState.value.quantityInput
        if (current.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(quantityInput = current.dropLast(1))
        }
    }

    fun setQuickAmount(amount: Int) {
        _uiState.value = _uiState.value.copy(
            quantityInput = amount.toString(),
            isRupeesMode = true
        )
    }

    fun toggleRupeesMode() {
        _uiState.value = _uiState.value.copy(
            isRupeesMode = !_uiState.value.isRupeesMode,
            quantityInput = ""
        )
    }

    fun addToList() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val inputValue = state.quantityInput.toBigDecimalOrNull() ?: return
        val unitPrice = product.price ?: BigDecimal.ZERO

        val quantity: BigDecimal
        val amount: BigDecimal

        if (state.isRupeesMode && unitPrice > BigDecimal.ZERO) {
            // Input is rupees → calculate quantity
            amount = inputValue
            quantity = inputValue.divide(unitPrice, 3, RoundingMode.HALF_UP)
        } else {
            // Input is liters/units
            quantity = inputValue
            amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP)
        }

        val item = InvoiceLineItem(
            product = product,
            nozzle = state.selectedNozzle,
            quantity = quantity,
            unitPrice = unitPrice,
            amount = amount
        )

        _uiState.value = state.copy(
            lineItems = state.lineItems + item,
            selectedProduct = null,
            selectedNozzle = null,
            quantityInput = "",
            isRupeesMode = false
        )
    }

    fun removeItem(index: Int) {
        val items = _uiState.value.lineItems.toMutableList()
        if (index in items.indices) {
            items.removeAt(index)
            _uiState.value = _uiState.value.copy(lineItems = items)
        }
    }

    fun clearAllItems() {
        _uiState.value = _uiState.value.copy(lineItems = emptyList())
    }

    fun goToStep2() {
        if (_uiState.value.lineItems.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(step = 2)
        }
    }

    fun goToStep1() {
        _uiState.value = _uiState.value.copy(step = 1)
    }

    fun setPaymentMode(mode: String) {
        _uiState.value = _uiState.value.copy(paymentMode = mode)
    }

    fun setDriverName(name: String) {
        _uiState.value = _uiState.value.copy(driverName = name)
    }

    fun setDriverPhone(phone: String) {
        _uiState.value = _uiState.value.copy(driverPhone = phone)
    }

    // Customer search
    fun searchCustomers(query: String) {
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(customerSearchResults = emptyList())
            return
        }
        viewModelScope.launch {
            val results = lookupRepository.searchCustomers(query)
            _uiState.value = _uiState.value.copy(customerSearchResults = results)
        }
    }

    fun selectCustomer(customer: CustomerListDto) {
        viewModelScope.launch {
            val vehicles = lookupRepository.getCustomerVehicles(customer.id)
            _uiState.value = _uiState.value.copy(
                isWalkIn = false,
                selectedCustomer = customer,
                customerVehicles = vehicles,
                customerSearchResults = emptyList()
            )
        }
    }

    fun selectVehicle(vehicle: VehicleDto) {
        _uiState.value = _uiState.value.copy(selectedVehicle = vehicle)
    }

    fun setWalkIn() {
        _uiState.value = _uiState.value.copy(
            isWalkIn = true,
            selectedCustomer = null,
            selectedVehicle = null,
            customerVehicles = emptyList(),
            customerSearchResults = emptyList()
        )
    }

    fun confirmInvoice() {
        val state = _uiState.value
        if (state.lineItems.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)

            val request = CreateInvoiceRequest(
                billType = "CASH",
                paymentMode = state.paymentMode,
                customer = state.selectedCustomer?.let { IdRef(it.id) },
                vehicle = state.selectedVehicle?.let { IdRef(it.id) },
                products = state.lineItems.map { item ->
                    InvoiceProductRequest(
                        product = IdRef(item.product.id),
                        nozzle = item.nozzle?.let { IdRef(it.id) },
                        quantity = item.quantity,
                        unitPrice = item.unitPrice
                    )
                },
                driverName = state.driverName.ifBlank { null },
                driverPhone = state.driverPhone.ifBlank { null }
            )

            val result = invoiceRepository.createInvoice(request)
            result.fold(
                onSuccess = { invoice ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successBillNo = invoice.billNo
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create invoice"
                    )
                }
            )
        }
    }

    fun resetForm() {
        _uiState.value = InvoiceUiState()
        loadLookupData()
    }
}
