package net.marvinweber.simsli.data.repository

import net.marvinweber.simsli.BuildConfig
import net.marvinweber.simsli.ui.UiMessage

enum class EndpointStatus {
    CHECKING,
    ONLINE,
    OFFLINE,
    ERROR
}

data class EndpointHealth(
    val url: String = BuildConfig.SERVER_URL,
    val status: EndpointStatus = EndpointStatus.CHECKING,
    val detail: UiMessage? = null,
    val serverVersion: String? = null,
    val apiVersion: Int? = null,
    val serverMode: String? = null,
    val registrationOpen: Boolean = true,
    val isOutdatedServer: Boolean = false,
    val isOutdatedApp: Boolean = false,
    val warningMessage: UiMessage? = null
)

/**
 * Server-side settings that are not tied to a household: the hosting choice
 * (AUTH-5), endpoint health probing (BIZ-5), and invite deep-link building (HH-3).
 */
interface ServerRepository {
    /** The endpoint requests currently go to (follows [hostingMode]). */
    val serverUrl: String

    val hostingMode: HostingMode

    /** Applies a hosting choice; cloud ignores [customUrl], self-hosted stores it. */
    fun setHosting(mode: HostingMode, customUrl: String?)

    /** Contacts [customUrl] (default: the current endpoint) and evaluates server info. */
    suspend fun probe(customUrl: String? = null): EndpointHealth

    /**
     * Invite deep link for [code] (HH-3): cloud invites carry no host (they stay
     * valid across endpoint changes); self-hosted invites point at their server.
     */
    fun inviteDeepLink(code: String): String
}
