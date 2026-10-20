package net.marvinweber.simsli.data.repository

/**
 * The immediate pull after joining a household (HH-4): fetches the joined
 * household's data right away instead of waiting for the next sync trigger.
 * Small seam so join flows stay unit-testable without a full SyncManager.
 */
interface PostJoinSyncer {
    suspend fun syncNow()
}
