package com.stopforfuel.app.ui.customer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.*
import com.stopforfuel.app.data.repository.CustomerManageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class CustomerDetailState(
    val customer: CustomerListDto? = null,
    val creditInfo: CreditInfoResponse? = null,
    val vehicles: List<VehicleDto> = emptyList(),
    val vehicleTypes: List<VehicleTypeDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null,
    val expandedVehicleId: Long? = null,
    val showAddVehicle: Boolean = false
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CustomerManageRepository
) : ViewModel() {

    private val customerId: Long = savedStateHandle["customerId"] ?: 0L
    private val _uiState = MutableStateFlow(CustomerDetailState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, actionMessage = null)
        viewModelScope.launch {
            val customerResult = repository.getCustomer(customerId)
            val creditResult = repository.getCreditInfo(customerId)
            val vehiclesResult = repository.getVehicles(customerId)

            customerResult.fold(
                onSuccess = { cust ->
                    _uiState.value = _uiState.value.copy(
                        customer = cust,
                        creditInfo = creditResult.getOrNull(),
                        vehicles = vehiclesResult.getOrDefault(emptyList()),
                        isLoading = false
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun updateCreditLimits(amount: String, liters: String) {
        viewModelScope.launch {
            val amtVal = amount.toBigDecimalOrNull()
            val litVal = liters.toBigDecimalOrNull()
            repository.updateCreditLimits(customerId, amtVal, litVal).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(customer = it, actionMessage = "Credit limits updated")
                    loadCreditInfo()
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun toggleStatus() {
        viewModelScope.launch {
            repository.toggleStatus(customerId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(customer = it, actionMessage = "Status changed to ${it.status}")
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun blockCustomer() {
        viewModelScope.launch {
            repository.block(customerId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(customer = it, actionMessage = "Customer blocked")
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun unblockCustomer() {
        viewModelScope.launch {
            repository.unblock(customerId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(customer = it, actionMessage = "Customer unblocked")
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun toggleVehicleExpand(vehicleId: Long) {
        val current = _uiState.value.expandedVehicleId
        _uiState.value = _uiState.value.copy(expandedVehicleId = if (current == vehicleId) null else vehicleId)
    }

    fun toggleVehicleStatus(vehicleId: Long) {
        viewModelScope.launch {
            repository.toggleVehicleStatus(vehicleId).fold(
                onSuccess = { updated ->
                    val newList = _uiState.value.vehicles.map { if (it.id == vehicleId) updated else it }
                    _uiState.value = _uiState.value.copy(vehicles = newList, actionMessage = "Vehicle status: ${updated.status}")
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun blockVehicle(vehicleId: Long) {
        viewModelScope.launch {
            repository.blockVehicle(vehicleId).fold(
                onSuccess = { updated ->
                    val newList = _uiState.value.vehicles.map { if (it.id == vehicleId) updated else it }
                    _uiState.value = _uiState.value.copy(vehicles = newList, actionMessage = "Vehicle blocked")
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun unblockVehicle(vehicleId: Long) {
        viewModelScope.launch {
            repository.unblockVehicle(vehicleId).fold(
                onSuccess = { updated ->
                    val newList = _uiState.value.vehicles.map { if (it.id == vehicleId) updated else it }
                    _uiState.value = _uiState.value.copy(vehicles = newList, actionMessage = "Vehicle unblocked")
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun updateVehicleLiterLimit(vehicleId: Long, limit: String) {
        viewModelScope.launch {
            val limitVal = limit.toBigDecimalOrNull()
            repository.updateVehicleLiterLimit(vehicleId, limitVal).fold(
                onSuccess = { updated ->
                    val newList = _uiState.value.vehicles.map { if (it.id == vehicleId) updated else it }
                    _uiState.value = _uiState.value.copy(vehicles = newList, actionMessage = "Liter limit updated")
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun showAddVehicle() {
        viewModelScope.launch {
            val types = repository.getVehicleTypes().getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(showAddVehicle = true, vehicleTypes = types)
        }
    }

    fun hideAddVehicle() {
        _uiState.value = _uiState.value.copy(showAddVehicle = false)
    }

    fun createVehicle(vehicleNumber: String, vehicleTypeId: Long?, maxLitersPerMonth: String) {
        viewModelScope.launch {
            val limit = maxLitersPerMonth.toBigDecimalOrNull()
            repository.createVehicle(customerId, vehicleNumber, vehicleTypeId, limit).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(showAddVehicle = false, actionMessage = "Vehicle added")
                    loadAll()
                },
                onFailure = { _uiState.value = _uiState.value.copy(actionMessage = "Error: ${it.message}") }
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    private fun loadCreditInfo() {
        viewModelScope.launch {
            repository.getCreditInfo(customerId).onSuccess {
                _uiState.value = _uiState.value.copy(creditInfo = it)
            }
        }
    }
}
