package com.stopforfuel.app.ui.pumpsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.*
import com.stopforfuel.app.data.repository.LookupRepository
import com.stopforfuel.app.data.repository.PumpSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class StartSessionUiState(
    val pumps: List<PumpDto> = emptyList(),
    val allNozzles: List<NozzleDto> = emptyList(),
    val selectedPump: PumpDto? = null,
    val pumpNozzles: List<NozzleDto> = emptyList(),
    val openReadings: Map<Long, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionStarted: Boolean = false
)

@HiltViewModel
class PumpSessionViewModel @Inject constructor(
    private val pumpSessionRepository: PumpSessionRepository,
    private val lookupRepository: LookupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StartSessionUiState())
    val uiState: StateFlow<StartSessionUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val pumps = lookupRepository.getPumps()
            val nozzles = lookupRepository.getNozzles()
            _uiState.value = _uiState.value.copy(pumps = pumps, allNozzles = nozzles)
        }
    }

    fun selectPump(pump: PumpDto) {
        val nozzles = _uiState.value.allNozzles.filter { it.pump?.id == pump.id }
        val readings = nozzles.associate { it.id to "" }
        _uiState.value = _uiState.value.copy(
            selectedPump = pump,
            pumpNozzles = nozzles,
            openReadings = readings
        )
    }

    fun updateOpenReading(nozzleId: Long, value: String) {
        val readings = _uiState.value.openReadings.toMutableMap()
        readings[nozzleId] = value
        _uiState.value = _uiState.value.copy(openReadings = readings)
    }

    fun startSession() {
        val state = _uiState.value
        val pump = state.selectedPump ?: return

        val readings = state.pumpNozzles.map { nozzle ->
            val readingStr = state.openReadings[nozzle.id] ?: "0"
            OpenReadingInput(
                nozzleId = nozzle.id,
                openReading = readingStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            )
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)

            val result = pumpSessionRepository.startSession(
                StartSessionRequest(pumpId = pump.id, readings = readings)
            )

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false, sessionStarted = true)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to start session"
                    )
                }
            )
        }
    }
}
