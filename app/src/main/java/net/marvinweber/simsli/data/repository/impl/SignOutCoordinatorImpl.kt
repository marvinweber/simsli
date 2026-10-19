package net.marvinweber.simsli.data.repository.impl

import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.SignOutCoordinator
import net.marvinweber.simsli.data.repository.SignOutResult
import net.marvinweber.simsli.data.sync.SyncManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignOutCoordinatorImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager
) : SignOutCoordinator {
    override suspend fun signOutAndWipe(): Result<SignOutResult> =
        authRepository.signOut().onSuccess {
            // The session is gone now, so pending syncs bail out on the missing
            // user id; the wipe itself waits on the sync mutex (DATA-1 — any
            // unsynced writes are lost, accepted by spec).
            syncManager.wipeLocalData()
        }
}
