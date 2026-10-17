package net.marvinweber.simsli.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.dao.SeenListEntryDao
import net.marvinweber.simsli.data.local.entity.DbSeenListEntry
import net.marvinweber.simsli.data.repository.SeenListEntryRepository
import net.marvinweber.simsli.di.IoDispatcher
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeenListEntryRepositoryImpl @Inject constructor(
    private val seenListEntryDao: SeenListEntryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SeenListEntryRepository {

    override fun observeSeenEntryIds(): Flow<Set<String>> =
        seenListEntryDao.observeAll().map { rows -> rows.map { it.entryId }.toSet() }

    override suspend fun markSeen(entryId: String) {
        withContext(ioDispatcher) {
            // IGNORE: re-marking is a no-op, so repeated marks don't re-emit / re-recompose.
            seenListEntryDao.insert(DbSeenListEntry(entryId = entryId, seenAt = Instant.now()))
        }
    }
}
