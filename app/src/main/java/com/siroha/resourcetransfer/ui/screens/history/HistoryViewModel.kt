package com.siroha.resourcetransfer.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siroha.resourcetransfer.data.local.dao.TransferHistoryDao
import com.siroha.resourcetransfer.data.local.entity.TransferHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: TransferHistoryDao
) : ViewModel() {

    val history: StateFlow<List<TransferHistoryEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAll() = viewModelScope.launch { dao.clearAll() }

    fun delete(entity: TransferHistoryEntity) = viewModelScope.launch { dao.delete(entity) }
}
