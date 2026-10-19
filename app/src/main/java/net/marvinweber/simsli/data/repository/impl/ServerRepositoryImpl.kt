package net.marvinweber.simsli.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.BuildConfig
import net.marvinweber.simsli.R
import net.marvinweber.simsli.data.local.AuthTokenStorage
import net.marvinweber.simsli.data.remote.SimsliRemoteDataSource
import net.marvinweber.simsli.data.repository.EndpointHealth
import net.marvinweber.simsli.data.repository.EndpointStatus
import net.marvinweber.simsli.data.repository.HostingMode
import net.marvinweber.simsli.data.repository.ServerRepository
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.ui.UiMessage
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepositoryImpl @Inject constructor(
    private val remoteDataSource: SimsliRemoteDataSource,
    private val tokenStorage: AuthTokenStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ServerRepository {

    override val serverUrl: String
        get() = tokenStorage.serverUrl

    override val hostingMode: HostingMode
        get() = tokenStorage.hostingMode

    override fun setHosting(mode: HostingMode, customUrl: String?) {
        tokenStorage.setHosting(mode, customUrl)
    }

    override suspend fun probe(customUrl: String?): EndpointHealth = withContext(ioDispatcher) {
        val serverUrl = normalizeUrl(customUrl ?: tokenStorage.serverUrl)
        if (serverUrl == null) {
            return@withContext EndpointHealth(
                url = customUrl.orEmpty(),
                status = EndpointStatus.ERROR,
                detail = UiMessage(R.string.sign_in_error_invalid_url, isError = true)
            )
        }
        val start = System.currentTimeMillis()
        try {
            val info = remoteDataSource.getServerInfo(serverUrl)
            val latency = System.currentTimeMillis() - start
            val mismatch = hasMajorOrMinorMismatch(info.version, BuildConfig.VERSION_NAME)
            val isOutdatedServer = mismatch < 0
            val isOutdatedApp = mismatch > 0
            val warning = when {
                isOutdatedServer -> UiMessage(
                    R.string.settings_endpoint_server_update_recommended,
                    listOf("v" + info.version, "v" + BuildConfig.VERSION_NAME)
                )
                isOutdatedApp -> UiMessage(
                    R.string.settings_endpoint_app_update_recommended,
                    listOf("v" + BuildConfig.VERSION_NAME, "v" + info.version)
                )
                else -> null
            }
            EndpointHealth(
                url = serverUrl,
                status = EndpointStatus.ONLINE,
                detail = UiMessage(R.string.settings_endpoint_online, listOf(latency.toString())),
                serverVersion = info.version,
                apiVersion = info.apiVersion,
                serverMode = info.serverMode,
                registrationOpen = info.registrationOpen,
                isOutdatedServer = isOutdatedServer,
                isOutdatedApp = isOutdatedApp,
                warningMessage = warning
            )
        } catch (e: Exception) {
            EndpointHealth(
                url = serverUrl,
                status = EndpointStatus.OFFLINE,
                detail = UiMessage(R.string.settings_endpoint_unreachable)
            )
        }
    }

    override fun inviteDeepLink(code: String): String =
        buildInviteDeepLink(code, tokenStorage.hostingMode, tokenStorage.serverUrl)

    private fun normalizeUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        return trimmed
    }

    companion object {
        private const val TAG = "SimsliServer"

        /**
         * Invite deep link (HH-3): cloud invites carry no host (they stay valid
         * across cloud endpoint changes); self-hosted invites point at their server.
         */
        internal fun buildInviteDeepLink(code: String, mode: HostingMode, serverUrl: String): String {
            return if (mode == HostingMode.SELF_HOSTED) {
                val host = URLEncoder.encode(serverUrl, "UTF-8")
                "simsli://invite/$code?host=$host"
            } else {
                "simsli://invite/$code"
            }
        }

        /** Sign of v1 vs v2 on major.minor only (patch differences don't warn). */
        fun hasMajorOrMinorMismatch(v1: String, v2: String): Int {
            val p1 = v1.split('.').mapNotNull { it.toIntOrNull() }
            val p2 = v2.split('.').mapNotNull { it.toIntOrNull() }
            val major1 = p1.getOrElse(0) { 0 }
            val major2 = p2.getOrElse(0) { 0 }
            if (major1 != major2) return major1.compareTo(major2)
            val minor1 = p1.getOrElse(1) { 0 }
            val minor2 = p2.getOrElse(1) { 0 }
            if (minor1 != minor2) return minor1.compareTo(minor2)
            return 0
        }
    }
}
