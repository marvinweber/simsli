package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A local change waiting to be pushed to Supabase. Holds no payload — at flush
 * time the current row state is read from Room (last-write-wins, and repeated
 * edits collapse naturally because pushing is idempotent).
 */
@Entity(tableName = "outbox")
data class DbOutboxEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val createdAt: Instant,
    val attempts: Int = 0
)
