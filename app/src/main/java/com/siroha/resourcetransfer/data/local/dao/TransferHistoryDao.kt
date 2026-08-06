package com.siroha.resourcetransfer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.siroha.resourcetransfer.data.local.entity.TransferHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {
    @Insert
    suspend fun insert(entity: TransferHistoryEntity): Long

    @Query("SELECT * FROM transfer_history ORDER BY timestampEpochMillis DESC")
    fun observeAll(): Flow<List<TransferHistoryEntity>>

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAll()

    @Delete
    suspend fun delete(entity: TransferHistoryEntity)
}
