package com.stopforfuel.app.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.InvoiceBillDto
import com.stopforfuel.app.data.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoiceBillExplorerUiState(
    val invoices: List<InvoiceBillDto> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 0,
    val hasMore: Boolean = true,
    val searchQuery: String = "",
    val billTypeFilter: String? = null,
    val paymentStatusFilter: String? = null,
    val error: String? = null
)

@HiltViewModel
class InvoiceBillExplorerViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceBillExplorerUiState())
    val uiState: StateFlow<InvoiceBillExplorerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadInvoices()
    }

    fun loadInvoices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentPage = 0, invoices = emptyList(), hasMore = true)
            fetchPage(0)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            fetchPage(state.currentPage + 1)
        }
    }

    private suspend fun fetchPage(page: Int) {
        val state = _uiState.value
        invoiceRepository.getInvoiceHistory(
            page = page,
            size = 20,
            billType = state.billTypeFilter,
            paymentStatus = state.paymentStatusFilter,
            search = state.searchQuery.ifBlank { null }
        ).fold(
            onSuccess = { response ->
                val currentList = if (page == 0) emptyList() else _uiState.value.invoices
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    invoices = currentList + response.content,
                    currentPage = page,
                    hasMore = (response.number ?: 0) < (response.totalPages ?: 0) - 1
                )
            },
            onFailure = { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = e.message
                )
            }
        )
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadInvoices()
        }
    }

    fun toggleBillType(type: String?) {
        _uiState.value = _uiState.value.copy(
            billTypeFilter = if (_uiState.value.billTypeFilter == type) null else type
        )
        loadInvoices()
    }

    fun togglePaymentStatus(status: String?) {
        _uiState.value = _uiState.value.copy(
            paymentStatusFilter = if (_uiState.value.paymentStatusFilter == status) null else status
        )
        loadInvoices()
    }
}
