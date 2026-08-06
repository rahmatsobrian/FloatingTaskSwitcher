package com.siroha.resourcetransfer.di

import android.content.Context
import androidx.room.Room
import com.siroha.resourcetransfer.data.local.AppDatabase
import com.siroha.resourcetransfer.data.local.dao.TransferHistoryDao
import com.siroha.resourcetransfer.domain.model.TransportType
import com.siroha.resourcetransfer.domain.transport.BluetoothTransport
import com.siroha.resourcetransfer.domain.transport.HotspotTransport
import com.siroha.resourcetransfer.domain.transport.LanTransport
import com.siroha.resourcetransfer.domain.transport.ManualIpTransport
import com.siroha.resourcetransfer.domain.transport.NearbyConnectionsTransport
import com.siroha.resourcetransfer.domain.transport.QrPairingTransport
import com.siroha.resourcetransfer.domain.transport.Transport
import com.siroha.resourcetransfer.domain.transport.WifiDirectTransport
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration() // acceptable pre-1.0; replace with real migrations post-launch
            .build()

    @Provides
    @Singleton
    fun provideTransferHistoryDao(db: AppDatabase): TransferHistoryDao = db.transferHistoryDao()
}

/**
 * Multibinds every Transport implementation into a Map<TransportType, Transport>
 * so TransportManager can look each one up by priority without an if/else chain.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransportModule {

    @Binds
    @IntoMap
    @TransportKey(TransportType.WIFI_DIRECT)
    abstract fun bindWifiDirect(impl: WifiDirectTransport): Transport

    @Binds
    @IntoMap
    @TransportKey(TransportType.NEARBY_CONNECTIONS)
    abstract fun bindNearby(impl: NearbyConnectionsTransport): Transport

    @Binds
    @IntoMap
    @TransportKey(TransportType.LOCAL_HOTSPOT)
    abstract fun bindHotspot(impl: HotspotTransport): Transport

    @Binds
    @IntoMap
    @TransportKey(TransportType.WIFI_LAN)
    abstract fun bindLan(impl: LanTransport): Transport

    @Binds
    @IntoMap
    @TransportKey(TransportType.QR_PAIRING)
    abstract fun bindQrPairing(impl: QrPairingTransport): Transport

    @Binds
    @IntoMap
    @TransportKey(TransportType.BLUETOOTH)
    abstract fun bindBluetooth(impl: BluetoothTransport): Transport

    @Binds
    @IntoMap
    @TransportKey(TransportType.MANUAL_IP)
    abstract fun bindManualIp(impl: ManualIpTransport): Transport
}

@dagger.MapKey
annotation class TransportKey(val value: TransportType)
