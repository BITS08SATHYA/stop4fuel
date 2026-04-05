package com.stopforfuel.app.ui.employee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.AdminUserDto
import com.stopforfuel.app.data.remote.dto.PasscodeResetRequestDto
import com.stopforfuel.app.data.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmployeeManageState(
    val employees: List<AdminUserDto> = emptyList(),
    val resetRequests: List<PasscodeResetRequestDto> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null,
    val newPasscode: String? = null,
    val newPasscodeForUser: String? = null
)

@HiltViewModel
class EmployeeManageViewModel @Inject constructor(
    private val repository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmployeeManageState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val empResult = repository.getEmployees(_uiState.value.searchQuery.ifBlank { null })
            val reqResult = repository.getResetRequests("PENDING")

            empResult.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        employees = it,
                        resetRequests = reqResult.getOrDefault(emptyList()),
                        isLoading = false
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        loadAll()
    }

    fun resetPasscode(userId: Long, userName: String?) {
        viewModelScope.launch {
            repository.resetPasscode(userId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        newPasscode = it.passcode,
                        newPasscodeForUser = userName ?: "Employee"
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun approveResetRequest(requestId: Long) {
        viewModelScope.launch {
            repository.approveResetRequest(requestId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        newPasscode = it.passcode,
                        newPasscodeForUser = it.userName ?: "Employee",
                        actionMessage = "Reset approved"
                    )
                    loadAll()
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun rejectResetRequest(requestId: Long) {
        viewModelScope.launch {
            repository.rejectResetRequest(requestId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(actionMessage = "Reset request rejected")
                    loadAll()
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun dismissPasscodeDialog() {
        _uiState.value = _uiState.value.copy(newPasscode = null, newPasscodeForUser = null)
    }

    fun toggleStatus(userId: Long) {
        viewModelScope.launch {
            repository.toggleStatus(userId).fold(
                onSuccess = { updated ->
                    val newList = _uiState.value.employees.map { if (it.id == userId) updated else it }
                    _uiState.value = _uiState.value.copy(employees = newList, actionMessage = "Status: ${updated.status}")
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }
}
