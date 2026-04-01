package com.stopforfuel.app.ui.pumpsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.CloseReadingInput
import com.stopforfuel.app.data.remote.dto.CloseSessionRequest
import com.stopforfuel.app.data.remote.dto.PumpSessionDto
import com.stopforfuel.app.data.repository.PumpSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class EndSessionUiState(
    val session: PumpSessionDto? = null,
    val closeReadings: Map<Long, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionClosed: Boolean = false,
    val closedSession: PumpSessionDto? = null
)

@HiltViewModel
class EndPumpSessionViewModel @Inject constructor(
    private val pumpSessionRepository: PumpSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EndSessionUiState())
    val uiState: StateFlow<EndSessionUiState> = _uiState.asStateFlow()

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = pumpSessionRepository.getSession(sessionId)
            result.fold(
                onSuccess = { session ->
                    val readings = session.readings?.associate {
                        (it.nozzleId ?: 0L) to ""
                    } ?: emptyMap()
                    _uiState.value = _uiState.value.copy(
                        session = session,
                        closeReadings = readings,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun updateCloseReading(nozzleId: Long, value: String) {
        val readings = _uiState.value.closeReadings.toMutableMap()
        readings[nozzleId] = value
        _uiState.value = _uiState.value.copy(closeReadings = readings)
    }

    fun closeSession() {
        val state = _uiState.value
        val session = state.session ?: return

        val readings = state.closeReadings.map { (nozzleId, value) ->
            CloseReadingInput(
                nozzleId = nozzleId,
                closeReading = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
            )
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)

            val result = pumpSessionRepository.closeSession(
                session.id,
                CloseSessionRequest(readings = readings)
            )

            result.fold(
                onSuccess = { closed ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        sessionClosed = true,
                        closedSession = closed
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to close session"
                    )
                }
            )
        }
    }
}
