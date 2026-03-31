package com.sbssh.ui.vpslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sbssh.data.db.AppDatabase
import com.sbssh.data.db.VpsEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class VpsListUiState(
    val vpsList: List<VpsEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showDeleteDialog: Long? = null
)

class VpsListViewModel : ViewModel() {

    private var dao = runCatching { AppDatabase.getInstance().vpsDao() }.getOrNull()

    private val _uiState = MutableStateFlow(VpsListUiState())
    val uiState: StateFlow<VpsListUiState> = _uiState.asStateFlow()

    init {
        if (dao == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Database not initialized"
            )
        } else {
            viewModelScope.launch {
                dao!!.getAllVps().collect { list ->
                    _uiState.value = _uiState.value.copy(vpsList = list, isLoading = false)
                }
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
            dao?.deleteVpsById(id)
            _uiState.value = _uiState.value.copy(showDeleteDialog = null)
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VpsListViewModel() as T
        }
    }
}
