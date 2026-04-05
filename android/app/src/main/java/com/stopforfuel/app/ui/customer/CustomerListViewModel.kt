package com.stopforfuel.app.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.CustomerListDto
import com.stopforfuel.app.data.repository.CustomerManageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerListState(
    val customers: List<CustomerListDto> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CustomerListViewModel @Inject constructor(
    private val repository: CustomerManageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerListState())
    val uiState = _uiState.asStateFlow()

    init {
        search("")
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, isLoading = true, error = null)
        viewModelScope.launch {
            repository.searchCustomers(query).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(customers = it, isLoading = false) },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }
}
