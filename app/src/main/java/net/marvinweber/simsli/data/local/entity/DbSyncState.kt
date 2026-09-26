package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Delta-sync watermark per remote table. Holds the newest server-side
 * `updated_at` value seen, so the next pull fetches only newer rows.
 */
@Entity(tableName = "sync_state")
data class DbSyncState(
    @PrimaryKey
    val tableName: String,
    val lastSyncedAt: Instant
)
