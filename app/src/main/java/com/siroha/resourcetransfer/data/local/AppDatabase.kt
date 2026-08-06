package com.siroha.resourcetransfer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.siroha.resourcetransfer.data.local.dao.TransferHistoryDao
import com.siroha.resourcetransfer.data.local.entity.TransferHistoryEntity

@Database(
    entities = [TransferHistoryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferHistoryDao(): TransferHistoryDao

    companion object {
        const val DATABASE_NAME = "resource_transfer.db"
    }
}
