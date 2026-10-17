package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Device-local bookkeeping for the LIST-9 "new" indicator: which list entries
 * have already been displayed on this device. Never synced (not part of
 * [net.marvinweber.simsli.data.sync.SyncContract]) and never enqueued in the
 * outbox — "seen by me" is per-device by definition. Orphaned rows (entry
 * deleted) are removed by SyncManager's GC pass.
 */
@Entity(tableName = "seen_list_entries")
data class DbSeenListEntry(
    @PrimaryKey
    val entryId: String,
    val seenAt: Instant
)
