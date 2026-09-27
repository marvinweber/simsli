package net.marvinweber.simsli.data.repository

import kotlinx.coroutines.flow.Flow
import net.marvinweber.simsli.domain.model.ListEntry

interface ListEntryRepository {
    fun getListEntriesByHousehold(householdId: String): Flow<List<ListEntry>>

    /** Entries whose item is assigned to the given store. */
    fun getListEntriesByHouseholdAndStore(householdId: String, storeId: String): Flow<List<ListEntry>>

    /** Entries whose item has no store assignment at all (the "No Store" filter view). */
    fun getListEntriesByHouseholdWithoutStore(householdId: String): Flow<List<ListEntry>>

    suspend fun createListEntry(listEntry: ListEntry): Result<ListEntry>

    suspend fun updateListEntry(listEntry: ListEntry): Result<ListEntry>

    suspend fun deleteListEntry(listEntryId: String): Result<Unit>

    suspend fun updateListEntryDoneStatus(listEntryId: String, done: Boolean): Result<Unit>

    /**
     * Adds an item to the list with the given entry details (all optional — fast add).
     * If the item already has an active entry this is a no-op; if its entry is in
     * "Recently checked", it is moved back to the active list instead — entered
     * details are applied, blank ones keep the entry's previous values.
     */
    suspend fun addToList(
        householdId: String,
        itemId: String,
        quantity: Double? = null,
        unit: String? = null,
        comment: String? = null
    ): Result<Unit>

    suspend fun updateEntryDetails(
        listEntryId: String,
        quantity: Double?,
        unit: String?,
        comment: String?
    ): Result<Unit>
}
