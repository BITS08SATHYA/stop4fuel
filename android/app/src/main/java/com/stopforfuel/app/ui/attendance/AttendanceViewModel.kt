package com.stopforfuel.app.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.*
import com.stopforfuel.app.data.repository.AdminRepository
import com.stopforfuel.app.data.repository.AttendanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class EmployeeAttendance(
    val employee: AdminUserDto,
    val status: String = "PRESENT"
)

data class AttendanceUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val employees: List<EmployeeAttendance> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val attendanceRepository: AttendanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val employees = adminRepository.getEmployees().getOrDefault(emptyList())
                val dateStr = _uiState.value.selectedDate.toString()
                val existing = attendanceRepository.getDailyAttendance(dateStr).getOrDefault(emptyList())

                val existingByEmpId = existing.associateBy { it.employee?.id }

                val merged = employees.map { emp ->
                    val att = existingByEmpId[emp.id]
                    EmployeeAttendance(
                        employee = emp,
                        status = att?.status ?: "PRESENT"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    employees = merged
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadData()
    }

    fun updateStatus(employeeId: Long, status: String) {
        val updated = _uiState.value.employees.map {
            if (it.employee.id == employeeId) it.copy(status = status) else it
        }
        _uiState.value = _uiState.value.copy(employees = updated)
    }

    fun saveAll() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, successMessage = null)
            val requests = state.employees.map { ea ->
                AttendanceBulkRequest(
                    employee = IdRef(ea.employee.id),
                    date = state.selectedDate.toString(),
                    status = ea.status
                )
            }
            attendanceRepository.markBulkAttendance(requests).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Attendance saved for ${requests.size} employees"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to save attendance"
                    )
                }
            )
        }
    }
}
