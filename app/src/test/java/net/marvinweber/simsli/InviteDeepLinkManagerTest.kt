package net.marvinweber.simsli

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.EndpointHealth
import net.marvinweber.simsli.data.repository.HostingMode
import net.marvinweber.simsli.data.repository.ServerRepository
import net.marvinweber.simsli.data.repository.SignOutCoordinator
import net.marvinweber.simsli.data.repository.SignOutResult
import net.marvinweber.simsli.data.repository.impl.ServerRepositoryImpl
import net.marvinweber.simsli.ui.navigation.InviteDeepLinkManager
import net.marvinweber.simsli.ui.navigation.InviteNavTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteDeepLinkManagerTest {

    private class FakeAuthRepository(initialState: AuthState = AuthState.SignedOut) : AuthRepository {
        val stateFlow = MutableStateFlow(initialState)
        override val authState: Flow<AuthState> = stateFlow
        override val currentAuthState: AuthState get() = stateFlow.value
        override suspend fun currentUserId(): String? = (stateFlow.value as? AuthState.SignedIn)?.userId
        override suspend fun currentUserEmail(): String? = (stateFlow.value as? AuthState.SignedIn)?.email
        override suspend fun sendMagicLink(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<SignOutResult> = Result.success(SignOutResult.Complete)
        override suspend fun handleDeepLink(intent: android.content.Intent?): Boolean = false
    }

    private class FakeServerRepository(
        private val mode: HostingMode = HostingMode.CLOUD,
        private val url: String = "http://10.0.2.2:8080"
    ) : ServerRepository {
        override val serverUrl: String = url
        override val hostingMode: HostingMode = mode
        var appliedMode: HostingMode? = null
        var appliedUrl: String? = null

        override fun setHosting(mode: HostingMode, customUrl: String?) {
            appliedMode = mode
            appliedUrl = customUrl
        }

        override suspend fun probe(customUrl: String?): EndpointHealth =
            error("probe not needed in these tests")

        override fun inviteDeepLink(code: String): String =
            ServerRepositoryImpl.buildInviteDeepLink(code, hostingMode, serverUrl)
    }

    private class FakeSignOutCoordinator : SignOutCoordinator {
        var signOutCalls: Int = 0
        override suspend fun signOutAndWipe(): Result<SignOutResult> {
            signOutCalls++
            return Result.success(SignOutResult.Complete)
        }
    }

    private fun newManager(
        auth: FakeAuthRepository,
        server: FakeServerRepository = FakeServerRepository(),
        signOut: FakeSignOutCoordinator = FakeSignOutCoordinator()
    ): InviteDeepLinkManager =
        InviteDeepLinkManager(auth, server, signOut, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `parseInviteCode parses path-based invite link`() {
        val manager = newManager(FakeAuthRepository())

        assertEquals("ABCD1234", manager.parseInviteCode("simsli://invite/ABCD1234"))
        assertEquals("ABCD1234", manager.parseInviteCode("simsli://invite/abcd1234"))
        assertEquals("ABCD1234", manager.parseInviteCode("simsli://invite/ABCD1234/"))
        assertEquals("ABCD1234", manager.parseInviteCode("simsli://invite/ABCD1234?ref=test"))
    }

    @Test
    fun `parseInviteCode parses query-based invite link`() {
        val manager = newManager(FakeAuthRepository())

        assertEquals("WXYZ9876", manager.parseInviteCode("simsli://invite?code=WXYZ9876"))
        assertEquals("WXYZ9876", manager.parseInviteCode("simsli://invite?code=wxyz9876&foo=bar"))
    }

    @Test
    fun `parseInviteCode returns null for invalid or empty links`() {
        val manager = newManager(FakeAuthRepository())

        assertNull(manager.parseInviteCode(null))
        assertNull(manager.parseInviteCode(""))
        assertNull(manager.parseInviteCode("simsli://invite"))
        assertNull(manager.parseInviteCode("simsli://invite/"))
        assertNull(manager.parseInviteCode("simsli://auth#access_token=123"))
        assertNull(manager.parseInviteCode("https://google.com"))
    }

    @Test
    fun `parseInviteUri extracts decoded host parameter`() {
        val manager = newManager(FakeAuthRepository())

        val parsed = manager.parseInviteUri(
            "simsli://invite/ABCD1234?host=http%3A%2F%2F10.0.2.2%3A8080"
        )
        assertEquals("ABCD1234", parsed?.first)
        assertEquals("http://10.0.2.2:8080", parsed?.second)
    }

    @Test
    fun `parseInviteUri returns null host for cloud invites`() {
        val manager = newManager(FakeAuthRepository())

        assertEquals("ABCD1234" to null, manager.parseInviteUri("simsli://invite/ABCD1234"))
        assertEquals("ABCD1234" to null, manager.parseInviteUri("simsli://invite/ABCD1234?ref=test"))
        assertNull(manager.parseInviteUri("simsli://invite/ABCD1234?host=")?.second)
    }

    @Test
    fun `handleInviteUri when signed in and link targets current server emits JoinHousehold navigation`() = runBlocking {
        val manager = newManager(FakeAuthRepository(AuthState.SignedIn("user-1", "user@test.de")))

        val handled = manager.handleInviteUri("simsli://invite/JOIN1234")
        assertTrue(handled)
        assertEquals("JOIN1234", manager.pendingInviteCode.value)

        val event = manager.navEvents.first()
        assertTrue(event is InviteNavTarget.JoinHousehold)
        assertEquals("JOIN1234", (event as InviteNavTarget.JoinHousehold).code)
        assertNull(manager.pendingServerSwitch.value)
    }

    @Test
    fun `handleInviteUri when signed out emits SignIn navigation carrying the host`() = runBlocking {
        val manager = newManager(FakeAuthRepository(AuthState.SignedOut))

        val handled = manager.handleInviteUri(
            "simsli://invite/JOIN1234?host=http%3A%2F%2F192%2E168%2E1%2E5%3A8080"
        )
        assertTrue(handled)
        assertEquals("JOIN1234", manager.pendingInviteCode.value)

        val event = manager.navEvents.first()
        assertTrue(event is InviteNavTarget.SignIn)
        assertEquals("JOIN1234", (event as InviteNavTarget.SignIn).code)
        assertEquals("http://192.168.1.5:8080", event.host)
    }

    @Test
    fun `self-hosted invite matching the signed-in server joins directly`() = runBlocking {
        val manager = newManager(
            FakeAuthRepository(AuthState.SignedIn("user-1", "user@test.de")),
            FakeServerRepository(HostingMode.SELF_HOSTED, "http://10.0.2.2:8080/")
        )

        val handled = manager.handleInviteUri(
            "simsli://invite/JOIN1234?host=http%3A%2F%2F10.0.2.2%3A8080"
        )
        assertTrue(handled)
        val event = manager.navEvents.first()
        assertTrue(event is InviteNavTarget.JoinHousehold)
        assertNull(manager.pendingServerSwitch.value)
    }

    @Test
    fun `cross-server invite raises server-switch request`() = runBlocking {
        val manager = newManager(
            FakeAuthRepository(AuthState.SignedIn("user-1", "user@test.de")),
            FakeServerRepository(HostingMode.SELF_HOSTED, "http://10.0.2.2:8080")
        )

        val handled = manager.handleInviteUri("simsli://invite/SWITCH1")
        assertTrue(handled)

        val request = manager.pendingServerSwitch.value
        assertEquals(HostingMode.CLOUD, request?.mode)
        assertEquals("SWITCH1", request?.code)
        // The code must NOT stay pending — no join flow may start before the
        // switch dialog is resolved.
        assertNull(manager.pendingInviteCode.value)
    }

    @Test
    fun `cloud invite while signed in self-hosted requests switch to cloud`() = runBlocking {
        val manager = newManager(
            FakeAuthRepository(AuthState.SignedIn("user-1", "user@test.de")),
            FakeServerRepository(HostingMode.SELF_HOSTED, "http://10.0.2.2:8080")
        )

        manager.handleInviteUri("simsli://invite/SWITCH2")
        assertEquals(HostingMode.CLOUD, manager.pendingServerSwitch.value?.mode)
    }

    @Test
    fun `confirmServerSwitch signs out wipes applies hosting and resumes sign-in`() = runBlocking {
        val signOut = FakeSignOutCoordinator()
        val server = FakeServerRepository(HostingMode.CLOUD)
        val manager = newManager(
            FakeAuthRepository(AuthState.SignedIn("user-1", "user@test.de")),
            server,
            signOut
        )
        manager.handleInviteUri("simsli://invite/SWITCH3?host=http%3A%2F%2F192%2E168%2E1%2E9%3A8080")

        manager.confirmServerSwitch()

        assertEquals(1, signOut.signOutCalls)
        assertEquals(HostingMode.SELF_HOSTED, server.appliedMode)
        assertEquals("http://192.168.1.9:8080", server.appliedUrl)
        assertNull(manager.pendingServerSwitch.value)
        // The code is pending again so sign-in resumes the join flow afterwards.
        assertEquals("SWITCH3", manager.pendingInviteCode.value)

        val event = manager.navEvents.first()
        assertTrue(event is InviteNavTarget.SignIn)
        assertEquals("SWITCH3", (event as InviteNavTarget.SignIn).code)
        assertEquals("http://192.168.1.9:8080", event.host)
    }

    @Test
    fun `dismissServerSwitch clears the request`() = runBlocking {
        val manager = newManager(
            FakeAuthRepository(AuthState.SignedIn("user-1", "user@test.de")),
            FakeServerRepository(HostingMode.CLOUD)
        )
        manager.handleInviteUri("simsli://invite/SWITCH4?host=http%3A%2F%2F192%2E168%2E1%2E9%3A8080")
        assertTrue(manager.pendingServerSwitch.value != null)

        manager.dismissServerSwitch()
        assertNull(manager.pendingServerSwitch.value)
    }

    @Test
    fun `transitioning to SignedIn triggers JoinHousehold navigation for pending code`() = runBlocking {
        val auth = FakeAuthRepository(AuthState.SignedOut)
        val manager = newManager(auth)

        manager.handleInviteUri("simsli://invite/PENDING1")
        assertEquals("PENDING1", manager.pendingInviteCode.value)

        // Sign in now
        auth.stateFlow.value = AuthState.SignedIn("user-2", "new@test.de")

        val event = manager.navEvents.first()
        assertTrue(event is InviteNavTarget.JoinHousehold)
        assertEquals("PENDING1", (event as InviteNavTarget.JoinHousehold).code)
    }

    @Test
    fun `consumePendingInviteCode returns code and clears state`() {
        val manager = newManager(FakeAuthRepository())

        manager.handleInviteUri("simsli://invite/CODE1234")
        assertEquals("CODE1234", manager.pendingInviteCode.value)

        val consumed = manager.consumePendingInviteCode()
        assertEquals("CODE1234", consumed)
        assertNull(manager.pendingInviteCode.value)
    }
}
