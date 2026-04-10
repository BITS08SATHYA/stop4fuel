package com.stopforfuel.app.ui.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.PaymentDto
import com.stopforfuel.app.data.remote.dto.PaymentSummaryDto
import com.stopforfuel.app.data.remote.dto.RecordPaymentRequest
import com.stopforfuel.app.data.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class RecordPaymentUiState(
    val paymentTarget: String = "bill", // "bill" or "statement"
    val targetId: Long = 0,
    val isLoading: Boolean = true,
    val summary: PaymentSummaryDto? = null,
    val amount: String = "",
    val paymentMode: String = "CASH",
    val referenceNo: String = "",
    val remarks: String = "",
    val isSubmitting: Boolean = false,
    val success: PaymentDto? = null,
    val error: String? = null
)

val PAYMENT_MODES = listOf("CASH", "UPI", "CARD", "CHEQUE", "NEFT", "CCMS", "BANK_TRANSFER")

@HiltViewModel
class RecordPaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val paymentTarget: String = savedStateHandle["paymentTarget"] ?: "bill"
    private val targetId: Long = savedStateHandle["targetId"] ?: 0L

    private val _uiState = MutableStateFlow(RecordPaymentUiState(
        paymentTarget = paymentTarget,
        targetId = targetId
    ))
    val uiState: StateFlow<RecordPaymentUiState> = _uiState.asStateFlow()

    init {
        loadSummary()
    }

    private fun loadSummary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = if (paymentTarget == "statement") {
                paymentRepository.getStatementPaymentSummary(targetId)
            } else {
                paymentRepository.getBillPaymentSummary(targetId)
            }
            result.onSuccess { summary ->
                _uiState.value = _uiState.value.copy(isLoading = false, summary = summary)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateAmount(value: String) {
        // Allow digits and one decimal point
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _uiState.value = _uiState.value.copy(amount = value)
        }
    }

    fun updatePaymentMode(mode: String) {
        _uiState.value = _uiState.value.copy(paymentMode = mode)
    }

    fun updateReferenceNo(value: String) {
        _uiState.value = _uiState.value.copy(referenceNo = value)
    }

    fun updateRemarks(value: String) {
        _uiState.value = _uiState.value.copy(remarks = value)
    }

    fun fillBalance() {
        val balance = _uiState.value.summary?.balanceAmount
        if (balance != null && balance > BigDecimal.ZERO) {
            _uiState.value = _uiState.value.copy(amount = balance.toPlainString())
        }
    }

    fun submit() {
        val state = _uiState.value
        val amount = state.amount.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _uiState.value = state.copy(error = "Enter a valid amount")
            return
        }

        val balance = state.summary?.balanceAmount ?: BigDecimal.ZERO
        if (amount > balance) {
            _uiState.value = state.copy(error = "Amount exceeds balance of ₹${balance.toPlainString()}")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val request = RecordPaymentRequest(
                amount = amount,
                paymentMode = state.paymentMode,
                referenceNo = state.referenceNo.ifBlank { null },
                remarks = state.remarks.ifBlank { null }
            )
            val result = if (paymentTarget == "statement") {
                paymentRepository.recordStatementPayment(targetId, request)
            } else {
                paymentRepository.recordBillPayment(targetId, request)
            }
            result.onSuccess { payment ->
                _uiState.value = _uiState.value.copy(isSubmitting = false, success = payment)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSubmitting = false, error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
