package net.marvinweber.simsli.data.sync

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.local.dao.HouseholdDao
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Realtime as a trigger, not a transport: postgres changes on list_entries and
 * items are turned into sync requests, and the watermark delta pull does the
 * actual work (and remains the backstop for anything missed while disconnected).
 *
 * One channel per household. Events fire only for the signed-in user's household
 * — Realtime authorizes through the same RLS policies as PostgREST.
 */
@Singleton
class RealtimeObserver @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val householdDao: HouseholdDao,
    private val syncScheduler: SyncScheduler,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    fun start() {
        externalScope.launch {
            // Only re-listen when the household ID changes (e.g. adoption on first sync) —
            // any other households-table write must not tear down and rejoin the channel.
            combine(
                authRepository.authState,
                householdDao.getHousehold().map { it?.id }.distinctUntilChanged()
            ) { auth, householdId -> auth to householdId }
                .collectLatest { (auth, householdId) ->
                    if (auth !is AuthState.SignedIn || householdId == null) return@collectLatest
                    // Suspends until sign-out or household change cancels this coroutine —
                    // the finally block in listen() then removes the channel.
                    listen(householdId)
                }
        }
    }

    private suspend fun listen(householdId: String) {
        val channel = supabaseClient.realtime.channel("household:$householdId")
        val entryChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "list_entries"
            filter("household_id", FilterOperator.EQ, householdId)
        }
        val itemChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "items"
            filter("household_id", FilterOperator.EQ, householdId)
        }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            Log.d(TAG, "Realtime channel joined for household $householdId")
            // (Re)joined: backfill anything missed while we were not connected.
            syncScheduler.requestSync("realtime:joined")
            merge(entryChanges, itemChanges).collect { action ->
                Log.d(TAG, "Realtime event: ${action::class.simpleName}")
                syncScheduler.requestSync("realtime:${action::class.simpleName}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Realtime channel failed — falling back to plain delta sync: ${e.message}")
        } finally {
            runCatching { supabaseClient.realtime.removeChannel(channel) }
        }
    }

    private companion object {
        const val TAG = "SimsliRealtime"
    }
}
