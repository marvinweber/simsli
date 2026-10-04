package net.marvinweber.simsli.data.sync

import android.util.Log
import net.marvinweber.simsli.BuildConfig
import net.marvinweber.simsli.data.local.AuthTokenStorage
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync diagnostics funnel: mirrors sync-related log lines into a bounded in-memory
 * ring buffer so a misbehaving sync (e.g. a refresh loop) can be analyzed after the
 * fact via Settings → Developer options → "Copy sync diagnostics" — without adb.
 *
 * Lines are tagged with the install-stable short device id, so logcats from two
 * devices (and the server's `X-Simsli-Device-Id` request logs) can be told apart.
 * Never put tokens, emails, or item names here — ids and counts only.
 */
@Singleton
class SyncDiagnostics @Inject constructor(
    private val tokenStorage: AuthTokenStorage
) {
    private val buffer = ArrayDeque<String>()
    private val lock = Any()

    fun d(tag: String, message: String) = log(Log.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Log.INFO, tag, message)
    fun w(tag: String, message: String) = log(Log.WARN, tag, message)

    fun w(tag: String, message: String, error: Throwable) {
        log(Log.WARN, tag, "$message :: ${error.javaClass.simpleName}: ${error.message}")
    }

    private fun log(priority: Int, tag: String, message: String) {
        val line = "${Instant.now()} [${tokenStorage.shortDeviceId}] $message"
        when (priority) {
            Log.DEBUG -> Log.d(tag, message)
            Log.INFO -> Log.i(tag, message)
            else -> Log.w(tag, message)
        }
        synchronized(lock) {
            buffer.addLast("${priorityChar(priority)}/$line")
            while (buffer.size > MAX_LINES) {
                buffer.removeFirst()
            }
        }
    }

    /** Full diagnostic dump: header + buffered lines, oldest first. */
    fun snapshot(): String = synchronized(lock) {
        buildString {
            appendLine("Simsli sync diagnostics")
            appendLine("device=${tokenStorage.shortDeviceId} app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) server=${tokenStorage.serverUrl}")
            appendLine("---")
            buffer.forEach { appendLine(it) }
        }
    }

    private fun priorityChar(priority: Int) = when (priority) {
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        else -> 'D'
    }

    private companion object {
        /** ~15 minutes of a 1 Hz sync loop at ~10 lines/run. */
        const val MAX_LINES = 600
    }
}
