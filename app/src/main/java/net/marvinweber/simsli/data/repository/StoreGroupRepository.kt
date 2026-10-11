package net.marvinweber.simsli.data.repository

import kotlinx.coroutines.flow.Flow
import net.marvinweber.simsli.domain.model.StoreGroup

interface StoreGroupRepository {
    fun getGroupsByHousehold(householdId: String): Flow<List<StoreGroup>>

    suspend fun getMaxSortOrder(householdId: String): Float

    suspend fun upsertGroup(group: StoreGroup): Result<StoreGroup>

    suspend fun updateGroupSortOrders(updates: List<Pair<String, Float>>): Result<Unit>

    /**
     * Dissolves the group (STORE-7): stores become ungrouped (never deleted),
     * the group's item selections are removed, exclusions survive.
     */
    suspend fun deleteGroup(groupId: String): Result<Unit>
}
