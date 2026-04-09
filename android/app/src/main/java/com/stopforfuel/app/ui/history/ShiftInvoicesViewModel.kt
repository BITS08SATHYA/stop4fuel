package com.stopforfuel.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.InvoiceBillDto
import com.stopforfuel.app.data.remote.dto.InvoiceProductDto
import com.stopforfuel.app.data.repository.InvoiceRepository
import com.stopforfuel.app.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class EditableProduct(
    val id: Long?,
    val productId: Long?,
    val productName: String?,
    val nozzleId: Long?,
    val quantity: String,
    val unitPrice: String
)

data class ShiftInvoicesUiState(
    val invoices: List<InvoiceBillDto> = emptyList(),
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val cashTotal: BigDecimal = BigDecimal.ZERO,
    val upiTotal: BigDecimal = BigDecimal.ZERO,
    val cardTotal: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val error: String? = null,
    val expandedInvoiceIds: Set<Long> = emptySet(),
    val editingInvoiceId: Long? = null,
    val editingProducts: List<EditableProduct> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null
)

@HiltViewModel
class ShiftInvoicesViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftInvoicesUiState())
    val uiState: StateFlow<ShiftInvoicesUiState> = _uiState.asStateFlow()

    init {
        loadInvoices()
    }

    fun loadInvoices() {
        val shiftId = shiftRepository.getShiftId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = invoiceRepository.getInvoicesByShift(shiftId)
            result.fold(
                onSuccess = { invoices ->
                    var total = BigDecimal.ZERO
                    var cash = BigDecimal.ZERO
                    var upi = BigDecimal.ZERO
                    var card = BigDecimal.ZERO
                    invoices.forEach { inv ->
                        val amt = inv.netAmount ?: BigDecimal.ZERO
                        total = total.add(amt)
                        when (inv.paymentMode?.uppercase()) {
                            "CASH" -> cash = cash.add(amt)
                            "UPI" -> upi = upi.add(amt)
                            "CARD" -> card = card.add(amt)
                        }
                    }
                    _uiState.value = ShiftInvoicesUiState(
                        invoices = invoices,
                        totalAmount = total,
                        cashTotal = cash,
                        upiTotal = upi,
                        cardTotal = card,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun toggleExpand(invoiceId: Long) {
        val current = _uiState.value.expandedInvoiceIds
        _uiState.value = _uiState.value.copy(
            expandedInvoiceIds = if (current.contains(invoiceId)) current - invoiceId else current + invoiceId
        )
    }

    fun startEdit(invoice: InvoiceBillDto) {
        val products = invoice.products?.map { p ->
            EditableProduct(
                id = p.id,
                productId = p.productId,
                productName = p.productName,
                nozzleId = p.nozzleId,
                quantity = p.quantity?.toPlainString() ?: "",
                unitPrice = p.unitPrice?.toPlainString() ?: ""
            )
        } ?: emptyList()
        _uiState.value = _uiState.value.copy(
            editingInvoiceId = invoice.id,
            editingProducts = products,
            saveError = null
        )
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editingInvoiceId = null, editingProducts = emptyList(), saveError = null)
    }

    fun updateProductQuantity(index: Int, qty: String) {
        val updated = _uiState.value.editingProducts.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(quantity = qty)
            _uiState.value = _uiState.value.copy(editingProducts = updated)
        }
    }

    fun updateProductPrice(index: Int, price: String) {
        val updated = _uiState.value.editingProducts.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(unitPrice = price)
            _uiState.value = _uiState.value.copy(editingProducts = updated)
        }
    }

    fun saveEdit() {
        val invoiceId = _uiState.value.editingInvoiceId ?: return
        val products = _uiState.value.editingProducts

        val productMaps = products.map { p ->
            mapOf(
                "product" to mapOf("id" to p.productId),
                "nozzle" to p.nozzleId?.let { mapOf("id" to it) },
                "quantity" to (p.quantity.toBigDecimalOrNull() ?: BigDecimal.ZERO),
                "unitPrice" to (p.unitPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            )
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            invoiceRepository.updateInvoice(invoiceId, mapOf("products" to productMaps)).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        editingInvoiceId = null,
                        editingProducts = emptyList()
                    )
                    loadInvoices()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveError = e.message ?: "Failed to save"
                    )
                }
            )
        }
    }
}
