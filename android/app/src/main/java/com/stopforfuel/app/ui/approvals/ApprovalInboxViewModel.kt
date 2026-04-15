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

data class ApprovalInboxUiState(
    val isLoading: Boolean = true,
    val pending: List<ApprovalRequestDto> = emptyList(),
    val actioningId: Long? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class ApprovalInboxViewModel @Inject constructor(
    private val repo: ApprovalRequestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApprovalInboxUiState())
    val uiState: StateFlow<ApprovalInboxUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.listPending().fold(
                onSuccess = { list ->
                    _uiState.value = _uiState.value.copy(isLoading = false, pending = list)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Failed to load pending requests")
                }
            )
        }
    }

    fun approve(id: Long, note: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actioningId = id, error = null)
            repo.approve(id, note?.takeIf { it.isNotBlank() }).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(actioningId = null, message = "Request approved")
                    load()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(actioningId = null, error = e.message ?: "Approve failed")
                }
            )
        }
    }

    fun reject(id: Long, note: String) {
        if (note.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "A reason is required to reject")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actioningId = id, error = null)
            repo.reject(id, note).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(actioningId = null, message = "Request rejected")
                    load()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(actioningId = null, error = e.message ?: "Reject failed")
                }
            )
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
