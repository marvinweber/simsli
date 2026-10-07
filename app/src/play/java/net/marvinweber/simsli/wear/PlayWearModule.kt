package net.marvinweber.simsli.wear

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.marvinweber.simsli.domain.wear.WearSyncBridge
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayWearModule {
    @Binds
    @Singleton
    abstract fun bindWearSyncBridge(impl: PlayWearSyncBridge): WearSyncBridge
}
