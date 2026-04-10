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
    val invoices: List<InvoiceBillDto> = emptyList(),
    val selectedInvoice: InvoiceBillDto? = null,
    val isUploading: Boolean = false,
    val uploadSuccess: String? = null,
    val error: String? = null,
    val shiftId: Long? = null
)

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
                _uiState.value = _uiState.value.copy(isLoading = false, invoices = sorted)
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
                val updatedList = _uiState.value.invoices.map {
                    if (it.id == updated.id) updated else it
                }
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadSuccess = "Uploaded successfully",
                    invoices = updatedList,
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

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, uploadSuccess = null)
    }
}
