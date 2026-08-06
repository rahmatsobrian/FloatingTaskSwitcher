package com.siroha.resourcetransfer.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siroha.resourcetransfer.data.local.dao.TransferHistoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeStats(val totalTransfers: Int, val totalBytesMoved: Long)

@HiltViewModel
class HomeViewModel @Inject constructor(
    historyDao: TransferHistoryDao
) : ViewModel() {

    val stats: StateFlow<HomeStats> = historyDao.observeAll()
        .map { entries ->
            HomeStats(
                totalTransfers = entries.count { it.status == "COMPLETED" },
                totalBytesMoved = entries.filter { it.status == "COMPLETED" }.sumOf { it.totalSizeBytes }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeStats(0, 0))
}
