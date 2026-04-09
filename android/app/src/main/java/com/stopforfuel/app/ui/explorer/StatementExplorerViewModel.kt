package com.stopforfuel.app.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopforfuel.app.data.remote.dto.StatementDto
import com.stopforfuel.app.data.repository.StatementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatementExplorerUiState(
    val statements: List<StatementDto> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 0,
    val hasMore: Boolean = true,
    val searchQuery: String = "",
    val statusFilter: String? = null,
    val error: String? = null
)

@HiltViewModel
class StatementExplorerViewModel @Inject constructor(
    private val statementRepository: StatementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatementExplorerUiState())
    val uiState: StateFlow<StatementExplorerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadStatements()
    }

    fun loadStatements() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentPage = 0, statements = emptyList(), hasMore = true)
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
        statementRepository.getStatements(
            page = page,
            size = 20,
            status = state.statusFilter,
            search = state.searchQuery.ifBlank { null }
        ).fold(
            onSuccess = { response ->
                val currentList = if (page == 0) emptyList() else _uiState.value.statements
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    statements = currentList + response.content,
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
            loadStatements()
        }
    }

    fun toggleStatus(status: String?) {
        _uiState.value = _uiState.value.copy(
            statusFilter = if (_uiState.value.statusFilter == status) null else status
        )
        loadStatements()
    }
}
