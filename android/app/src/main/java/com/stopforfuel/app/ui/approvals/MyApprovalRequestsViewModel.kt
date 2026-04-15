package com.stopforfuel.app.ui.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.ApprovalRequestDto
import com.stopforfuel.app.data.repository.ApprovalRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyApprovalRequestsUiState(
    val isLoading: Boolean = true,
    val requests: List<ApprovalRequestDto> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class MyApprovalRequestsViewModel @Inject constructor(
    private val repo: ApprovalRequestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyApprovalRequestsUiState())
    val uiState: StateFlow<MyApprovalRequestsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.listMine().fold(
                onSuccess = { list ->
                    _uiState.value = _uiState.value.copy(isLoading = false, requests = list)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Failed to load requests")
                }
            )
        }
    }
}
