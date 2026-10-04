package net.marvinweber.simsli.data.sync

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.marvinweber.simsli.data.local.AuthTokenStorage
import net.marvinweber.simsli.data.local.dao.HouseholdDao
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.di.ApplicationScope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Realtime WebSocket observer: connects to the Simsli server WebSocket endpoint
 * and turns server invalidation events into local sync requests.
 */
@Singleton
class RealtimeObserver @Inject constructor(
    private val httpClient: OkHttpClient,
    private val tokenStorage: AuthTokenStorage,
    private val authRepository: AuthRepository,
    private val householdDao: HouseholdDao,
    private val syncScheduler: SyncScheduler,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    fun start() {
        externalScope.launch {
            combine(
                authRepository.authState,
                householdDao.getHousehold().map { it?.id }.distinctUntilChanged()
            ) { auth, householdId -> auth to householdId }
                .collectLatest { (auth, householdId) ->
                    if (auth !is AuthState.SignedIn || householdId == null) return@collectLatest
                    listen(householdId)
                }
        }
    }

    private suspend fun listen(householdId: String) {
        while (externalScope.isActive) {
            val token = tokenStorage.accessToken
            if (token.isNullOrBlank()) {
                delay(RECONNECT_DELAY_MS)
                continue
            }

            val wsUrl = tokenStorage.serverUrl
                .replaceFirst("^http".toRegex(), "ws")
                .trimEnd('/')

            val request = Request.Builder()
                .url("$wsUrl/api/v1/realtime?token=$token&household_id=$householdId")
                .build()

            val closedNormally = suspendCancellableCoroutine<Boolean> { cont ->
                var socket: WebSocket? = null
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "Realtime WebSocket connected for household $householdId")
                        syncScheduler.requestSync("realtime:connected")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d(TAG, "Realtime event received: $text")
                        syncScheduler.requestSync("realtime:event")
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "Realtime WebSocket closed ($code): $reason")
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "Realtime WebSocket failure: ${t.message}")
                        if (cont.isActive) cont.resume(false)
                    }
                }

                socket = httpClient.newWebSocket(request, listener)

                cont.invokeOnCancellation {
                    socket.close(1000, "Normal closure")
                }
            }

            if (!closedNormally) {
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    private companion object {
        const val TAG = "SimsliRealtime"
        const val RECONNECT_DELAY_MS = 5000L
    }
}
