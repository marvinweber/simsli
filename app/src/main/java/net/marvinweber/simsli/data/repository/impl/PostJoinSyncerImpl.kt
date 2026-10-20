package net.marvinweber.simsli.data.repository.impl

import net.marvinweber.simsli.data.repository.PostJoinSyncer
import net.marvinweber.simsli.data.sync.SyncManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostJoinSyncerImpl @Inject constructor(
    private val syncManager: SyncManager
) : PostJoinSyncer {
    override suspend fun syncNow() {
        syncManager.syncNow("join")
    }
}
