package com.stopforfuel.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.InvoiceBillDto
import com.stopforfuel.app.data.repository.InvoiceRepository
import com.stopforfuel.app.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class ShiftInvoicesUiState(
    val invoices: List<InvoiceBillDto> = emptyList(),
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val cashTotal: BigDecimal = BigDecimal.ZERO,
    val upiTotal: BigDecimal = BigDecimal.ZERO,
    val cardTotal: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val error: String? = null
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
}
