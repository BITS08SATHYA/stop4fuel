package com.stopforfuel.app.ui.invoiceupload

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
import java.io.File
import javax.inject.Inject

data class InvoiceUploadUiState(
    val isLoading: Boolean = true,
    val allInvoices: List<InvoiceBillDto> = emptyList(),
    val selectedInvoice: InvoiceBillDto? = null,
    val isUploading: Boolean = false,
    val uploadSuccess: String? = null,
    val error: String? = null,
    val shiftId: Long? = null,
    val showCashBills: Boolean = false
) {
    val invoices: List<InvoiceBillDto>
        get() = if (showCashBills) allInvoices else allInvoices.filter { it.billType == "CREDIT" }

    val creditCount: Int get() = allInvoices.count { it.billType == "CREDIT" }
    val cashCount: Int get() = allInvoices.count { it.billType != "CREDIT" }
}

@HiltViewModel
class InvoiceUploadViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceUploadUiState())
    val uiState: StateFlow<InvoiceUploadUiState> = _uiState.asStateFlow()

    init {
        loadShiftInvoices()
    }

    fun loadShiftInvoices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val shift = shiftRepository.fetchActiveShift()
            if (shift == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "No active shift")
                return@launch
            }
            _uiState.value = _uiState.value.copy(shiftId = shift.id)
            val result = invoiceRepository.getInvoicesByShift(shift.id)
            result.onSuccess { invoices ->
                // Show credit invoices first, then all others
                val sorted = invoices.sortedWith(
                    compareByDescending<InvoiceBillDto> { it.billType == "CREDIT" }
                        .thenByDescending { it.id }
                )
                _uiState.value = _uiState.value.copy(isLoading = false, allInvoices = sorted)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun selectInvoice(invoice: InvoiceBillDto) {
        _uiState.value = _uiState.value.copy(selectedInvoice = invoice)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedInvoice = null, uploadSuccess = null)
    }

    fun uploadPhoto(file: File, type: String) {
        val invoice = _uiState.value.selectedInvoice ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null, uploadSuccess = null)
            val result = invoiceRepository.uploadInvoiceFile(invoice.id, type, file)
            result.onSuccess { updated ->
                // Update the invoice in the list
                val updatedList = _uiState.value.allInvoices.map {
                    if (it.id == updated.id) updated else it
                }
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadSuccess = "Uploaded successfully",
                    allInvoices = updatedList,
                    selectedInvoice = updated
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Upload failed: ${e.message}"
                )
            }
        }
    }

    fun toggleShowCashBills() {
        _uiState.value = _uiState.value.copy(showCashBills = !_uiState.value.showCashBills)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, uploadSuccess = null)
    }
}
