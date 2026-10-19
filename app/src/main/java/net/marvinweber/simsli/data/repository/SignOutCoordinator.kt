package net.marvinweber.simsli.data.repository

import net.marvinweber.simsli.data.repository.impl.SignOutCoordinatorImpl

/**
 * The one sign-out path (AUTH-2): revoke the session, then wipe all local
 * household data — even when the server revoke fails (the refresh token is
 * left to expire server-side). Shared by the Settings sign-out dialog and the
 * cross-server invite switch (HH-3).
 */
interface SignOutCoordinator {
    /** Revokes the session and wipes local data; wipe happens even on revoke failure. */
    suspend fun signOutAndWipe(): Result<SignOutResult>
}
