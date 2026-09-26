package net.marvinweber.simsli.data.repository

import net.marvinweber.simsli.domain.model.Household
import kotlinx.coroutines.flow.Flow

interface HouseholdRepository {
    fun getHousehold(): Flow<Household?>

    suspend fun createHousehold(name: String): Result<Household>

    suspend fun updateHousehold(household: Household): Result<Household>

    suspend fun deleteHousehold(householdId: String): Result<Unit>

    /** Generates a single-use invite code (8 chars) and registers it server-side; expires after 24h. */
    suspend fun createInviteToken(): Result<String>

    /** Redeems [token] via the accept_invite RPC, adding the signed-in user to that household. */
    suspend fun joinHousehold(token: String): Result<Unit>
}
