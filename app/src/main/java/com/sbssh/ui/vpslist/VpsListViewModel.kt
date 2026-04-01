package com.sbssh.ui.vpslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbssh.data.db.VpsDao
import com.sbssh.data.db.VpsEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VpsListUiState(
    val vpsList: List<VpsEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showDeleteDialog: Long? = null
)

@HiltViewModel
class VpsListViewModel @Inject constructor(
    private val dao: VpsDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(VpsListUiState())
    val uiState: StateFlow<VpsListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val first = dao.getAllVpsAsList()
                _uiState.value = _uiState.value.copy(vpsList = first, isLoading = false)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load servers"
                )
            }
        }
        viewModelScope.launch {
            dao.getAllVps()
                .catch { e ->
                    if (e is kotlinx.coroutines.CancellationException) return@catch
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load servers"
                    )
                }
                .collect { list ->
                    _uiState.value = _uiState.value.copy(vpsList = list, isLoading = false, error = null)
                }
        }
    }

    fun confirmDelete(id: Long) {
        _uiState.value = _uiState.value.copy(showDeleteDialog = id)
    }

    fun dismissDelete() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = null)
    }

    fun deleteVps(id: Long) {
        viewModelScope.launch {
            dao.deleteVpsById(id)
            _uiState.value = _uiState.value.copy(showDeleteDialog = null)
        }
    }
}
