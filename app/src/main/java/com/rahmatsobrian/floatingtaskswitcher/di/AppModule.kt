package com.rahmatsobrian.floatingtaskswitcher.di

import com.rahmatsobrian.floatingtaskswitcher.data.repository.PermissionRepositoryImpl
import com.rahmatsobrian.floatingtaskswitcher.data.repository.RecentAppsRepositoryImpl
import com.rahmatsobrian.floatingtaskswitcher.domain.repository.PermissionRepository
import com.rahmatsobrian.floatingtaskswitcher.domain.repository.RecentAppsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindRecentAppsRepository(impl: RecentAppsRepositoryImpl): RecentAppsRepository

    @Binds
    abstract fun bindPermissionRepository(impl: PermissionRepositoryImpl): PermissionRepository
}
