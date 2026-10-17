package net.marvinweber.simsli.domain.util

import net.marvinweber.simsli.domain.model.ListEntry

/**
 * LIST-9: whether an entry counts as "new" for the dot indicator — created by
 * another member and not yet displayed on this device.
 *
 * Entries with `createdBy == null` (created offline; the field is only filled
 * at push/pull time) and signed-out usage (no current user) never count, so
 * your own entries never dot.
 */
fun isNewFromOthers(entry: ListEntry, seenEntryIds: Set<String>, currentUserId: String?): Boolean {
    if (currentUserId == null) return false
    val createdBy = entry.createdBy ?: return false
    if (createdBy == currentUserId) return false
    return entry.id !in seenEntryIds
}
