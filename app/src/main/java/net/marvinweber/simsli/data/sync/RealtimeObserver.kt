package net.marvinweber.simsli.data.sync

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
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
 * ONLY when the app is in the foreground (via [ProcessLifecycleOwner]).
 *
 * When the app is closed, minimized, or the screen is turned off, the connection
 * is immediately severed to preserve battery and avoid wasteful background network sync.
 */
@Singleton
class RealtimeObserver @Inject constructor(
    private val httpClient: OkHttpClient,
    private val tokenStorage: AuthTokenStorage,
    private val authRepository: AuthRepository,
    private val householdDao: HouseholdDao,
    private val syncScheduler: SyncScheduler,
    private val diag: SyncDiagnostics,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    fun start() {
        externalScope.launch {
            // Suspends when the app is backgrounded/stopped; automatically reconnects on foreground.
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                diag.d(TAG, "App in foreground: activating realtime observer")
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
    }

    private suspend fun listen(householdId: String) = coroutineScope {
        while (isActive) {
            val token = tokenStorage.accessToken
            if (token.isNullOrBlank()) {
                delay(RECONNECT_DELAY_MS)
                continue
            }

            val wsUrl = tokenStorage.serverUrl
                .replaceFirst("^http".toRegex(), "ws")
                .trimEnd('/')

            // Token in the Authorization header, not the URL: query strings end up in
            // access logs and proxies verbatim.
            val request = Request.Builder()
                .url("$wsUrl/api/v1/realtime?household_id=$householdId")
                .header("Authorization", "Bearer $token")
                .header("X-Simsli-Device-Id", tokenStorage.deviceId)
                .build()

            val closedNormally = suspendCancellableCoroutine<Boolean> { cont ->
                var socket: WebSocket? = null
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        diag.d(TAG, "Realtime WebSocket connected for household $householdId")
                        syncScheduler.requestSync("realtime:connected")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        diag.d(TAG, "Realtime event received: $text")
                        syncScheduler.requestSync("realtime:event")
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        diag.d(TAG, "Realtime WebSocket closed ($code): $reason")
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        diag.w(TAG, "Realtime WebSocket failure: ${t.message}")
                        if (cont.isActive) cont.resume(false)
                    }
                }

                socket = httpClient.newWebSocket(request, listener)

                cont.invokeOnCancellation {
                    diag.d(TAG, "Closing realtime WebSocket (app backgrounded or lifecycle stopped)")
                    socket.close(1000, "App backgrounded")
                }
            }

            if (!closedNormally && isActive) {
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    private companion object {
        const val TAG = "SimsliRealtime"
        const val RECONNECT_DELAY_MS = 5000L
    }
}
