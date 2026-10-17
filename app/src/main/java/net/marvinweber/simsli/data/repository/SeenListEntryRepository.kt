package net.marvinweber.simsli.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Device-local seen-state for the LIST-9 "new" indicator (LIST-9). Tracks which
 * list entries have already been displayed on this device so entries created by
 * other members can be marked until first seen.
 *
 * Deliberately device-local only: nothing here goes through the outbox or
 * triggers a sync — "seen by me" must never reach the server.
 */
interface SeenListEntryRepository {
    fun observeSeenEntryIds(): Flow<Set<String>>
    suspend fun markSeen(entryId: String)
}
