package net.marvinweber.simsli

import net.marvinweber.simsli.domain.model.ListEntry
import net.marvinweber.simsli.domain.util.isNewFromOthers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * LIST-9: the "new" dot — entries created by another member count until first
 * displayed on this device. Own entries, offline-created entries (createdBy
 * only filled at push time) and signed-out usage never count.
 */
class NewEntryIndicatorTest {

    private val t = Instant.EPOCH
    private val me = "user-1"
    private val other = "user-2"

    private fun entry(id: String, createdBy: String? = null, done: Boolean = false) = ListEntry(
        id = id, householdId = "hh", itemId = "item-$id", done = done,
        createdAt = t, updatedAt = t, createdBy = createdBy
    )

    @Test
    fun `entry from another member and unseen is new`() {
        assertTrue(isNewFromOthers(entry("e1", createdBy = other), emptySet(), me))
    }

    @Test
    fun `own entry is never new`() {
        assertFalse(isNewFromOthers(entry("e1", createdBy = me), emptySet(), me))
    }

    @Test
    fun `offline-created entry without createdBy is never new`() {
        assertFalse(isNewFromOthers(entry("e1", createdBy = null), emptySet(), me))
    }

    @Test
    fun `signed out never shows the dot`() {
        assertFalse(isNewFromOthers(entry("e1", createdBy = other), emptySet(), currentUserId = null))
    }

    @Test
    fun `already seen entry is not new`() {
        assertFalse(isNewFromOthers(entry("e1", createdBy = other), setOf("e1"), me))
    }

    @Test
    fun `done entry still computes new - rendering is gated to active rows`() {
        // Created AND checked off by another member before we ever saw it: still counts,
        // but RecentlyCheckedRow doesn't render the dot and never marks seen — so reverting
        // it to active brings the dot back.
        assertTrue(isNewFromOthers(entry("e1", createdBy = other, done = true), emptySet(), me))
    }
}
