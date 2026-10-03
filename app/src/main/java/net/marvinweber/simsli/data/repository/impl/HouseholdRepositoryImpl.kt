package net.marvinweber.simsli.data.repository.impl

import io.github.jan.supabase.exceptions.RestException
import net.marvinweber.simsli.data.local.dao.HouseholdDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.mapper.toDomain
import net.marvinweber.simsli.data.local.mapper.toDb
import net.marvinweber.simsli.data.remote.SupabaseRemoteDataSource
import net.marvinweber.simsli.data.remote.mapper.toDb
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.sync.SyncContract
import net.marvinweber.simsli.data.sync.SyncManager
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.Household
import net.marvinweber.simsli.domain.model.HouseholdMember
import net.marvinweber.simsli.domain.model.MemberRole
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class HouseholdRepositoryImpl @Inject constructor(
    private val householdDao: HouseholdDao,
    private val outboxDao: OutboxDao,
    private val syncScheduler: SyncScheduler,
    private val remoteDataSource: SupabaseRemoteDataSource,
    private val authRepository: AuthRepository,
    private val syncManagerProvider: Provider<SyncManager>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : HouseholdRepository {

    override fun getHousehold(): Flow<Household?> {
        return householdDao.getHousehold().map { it?.toDomain() }
    }

    override suspend fun createHousehold(name: String): Result<Household> {
        return try {
            val now = Instant.now()
            val household = Household(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = now,
                updatedAt = now
            )
            householdDao.insert(household.toDb())
            Result.success(household)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateHousehold(household: Household): Result<Household> {
        return try {
            val updatedHousehold = household.copy(updatedAt = Instant.now())
            householdDao.update(updatedHousehold.toDb())
            outboxDao.enqueue(
                DbOutboxEntry(
                    entityType = SyncContract.ENTITY_HOUSEHOLD,
                    entityId = updatedHousehold.id,
                    operation = SyncContract.OP_UPSERT,
                    createdAt = Instant.now()
                )
            )
            syncScheduler.requestSync("household:update")
            Result.success(updatedHousehold)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteHousehold(householdId: String): Result<Unit> {
        return try {
            householdDao.delete(householdId, Instant.now())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createInviteToken(): Result<String> {
        return try {
            val userId = authRepository.currentUserId()
                ?: return Result.failure(IllegalStateException("Not signed in — invites need an account."))
            val household = getHousehold().first()
                ?: return Result.failure(IllegalStateException("No household set up yet"))
            val token = generateInviteToken()
            remoteDataSource.insertInviteToken(
                token = token,
                householdId = household.id,
                createdBy = userId
            )
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinHousehold(token: String): Result<Unit> {
        return try {
            if (authRepository.currentUserId() == null) {
                return Result.failure(IllegalStateException("Not signed in — joining needs an account."))
            }
            // 1. Redeems token on server: deletes old household if sole owner, or leaves it if member,
            //    adds user to new household, marks token used, and returns new household ID.
            val newHouseholdId = remoteDataSource.acceptInvite(token)

            // 2. Fetch authoritative new household from remote
            val newHousehold = remoteDataSource.getHousehold(newHouseholdId)

            // 3. Clear all local tables (items, stores, categories, entries, outbox, watermarks)
            syncManagerProvider.get().wipeLocalData()

            // 4. Insert authoritative new household
            householdDao.insert(newHousehold.toDb())

            Result.success(Unit)
        } catch (e: RestException) {
            // e.error is the PostgREST error message, e.g. "Invalid or expired invite token"
            Result.failure(IllegalStateException(e.error, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMembers(householdId: String): Result<List<HouseholdMember>> {
        return try {
            val currentUserId = authRepository.currentUserId()
            val dtos = remoteDataSource.getHouseholdMembers(householdId)
            val members = dtos.map { dto ->
                HouseholdMember(
                    id = dto.id,
                    householdId = dto.householdId,
                    userId = dto.userId,
                    role = MemberRole.fromString(dto.role),
                    email = dto.email,
                    name = dto.name,
                    joinedAt = Instant.parse(dto.joinedAt),
                    isCurrentUser = dto.userId == currentUserId
                )
            }
            Result.success(members)
        } catch (e: RestException) {
            Result.failure(IllegalStateException(e.error, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeMember(householdId: String, userId: String): Result<Unit> {
        return try {
            remoteDataSource.removeHouseholdMember(householdId, userId)
            Result.success(Unit)
        } catch (e: RestException) {
            Result.failure(IllegalStateException(e.error, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        /** No 0/O/1/I/L — codes are transcribed by hand. */
        val INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        const val INVITE_TOKEN_LENGTH = 8
    }

    private fun generateInviteToken(): String {
        val random = SecureRandom()
        return buildString {
            repeat(INVITE_TOKEN_LENGTH) { append(INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)]) }
        }
    }
}
