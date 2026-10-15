package net.marvinweber.simsli

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.SignOutResult
import net.marvinweber.simsli.ui.navigation.InviteDeepLinkManager
import net.marvinweber.simsli.ui.navigation.InviteNavTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `parseInviteCode parses path-based invite link`() {
        val auth = FakeAuthRepository()
        val manager = InviteDeepLinkManager(auth, CoroutineScope(Dispatchers.Unconfined))

        assertEquals("ABCD1234", manager.parseInviteCode("simsli://invite/ABCD1234"))
        assertEquals("ABCD1234", manager.parseInviteCode("simsli://invite/abcd1234"))
        assertEquals("ABCD1234", manager.parseInviteCode("simsli://invite/ABCD1234/"))
        assertEquals("ABCD1234", manager.parseInviteCode("simsli://invite/ABCD1234?ref=test"))
    }

    @Test
    fun `parseInviteCode parses query-based invite link`() {
        val auth = FakeAuthRepository()
        val manager = InviteDeepLinkManager(auth, CoroutineScope(Dispatchers.Unconfined))

        assertEquals("WXYZ9876", manager.parseInviteCode("simsli://invite?code=WXYZ9876"))
        assertEquals("WXYZ9876", manager.parseInviteCode("simsli://invite?code=wxyz9876&foo=bar"))
    }

    @Test
    fun `parseInviteCode returns null for invalid or empty links`() {
        val auth = FakeAuthRepository()
        val manager = InviteDeepLinkManager(auth, CoroutineScope(Dispatchers.Unconfined))

        assertNull(manager.parseInviteCode(null))
        assertNull(manager.parseInviteCode(""))
        assertNull(manager.parseInviteCode("simsli://invite"))
        assertNull(manager.parseInviteCode("simsli://invite/"))
        assertNull(manager.parseInviteCode("simsli://auth#access_token=123"))
        assertNull(manager.parseInviteCode("https://google.com"))
    }

    @Test
    fun `handleInviteUri when signed in emits JoinHousehold navigation`() = runBlocking {
        val auth = FakeAuthRepository(AuthState.SignedIn("user-1", "user@test.de"))
        val manager = InviteDeepLinkManager(auth, CoroutineScope(Dispatchers.Unconfined))

        val handled = manager.handleInviteUri("simsli://invite/JOIN1234")
        assertTrue(handled)
        assertEquals("JOIN1234", manager.pendingInviteCode.value)

        val event = manager.navEvents.first()
        assertTrue(event is InviteNavTarget.JoinHousehold)
        assertEquals("JOIN1234", (event as InviteNavTarget.JoinHousehold).code)
    }

    @Test
    fun `handleInviteUri when signed out emits SignIn navigation`() = runBlocking {
        val auth = FakeAuthRepository(AuthState.SignedOut)
        val manager = InviteDeepLinkManager(auth, CoroutineScope(Dispatchers.Unconfined))

        val handled = manager.handleInviteUri("simsli://invite/JOIN1234")
        assertTrue(handled)
        assertEquals("JOIN1234", manager.pendingInviteCode.value)

        val event = manager.navEvents.first()
        assertTrue(event is InviteNavTarget.SignIn)
        assertEquals("JOIN1234", (event as InviteNavTarget.SignIn).code)
    }

    @Test
    fun `transitioning to SignedIn triggers JoinHousehold navigation for pending code`() = runBlocking {
        val auth = FakeAuthRepository(AuthState.SignedOut)
        val manager = InviteDeepLinkManager(auth, CoroutineScope(Dispatchers.Unconfined))

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
        val auth = FakeAuthRepository()
        val manager = InviteDeepLinkManager(auth, CoroutineScope(Dispatchers.Unconfined))

        manager.handleInviteUri("simsli://invite/CODE1234")
        assertEquals("CODE1234", manager.pendingInviteCode.value)

        val consumed = manager.consumePendingInviteCode()
        assertEquals("CODE1234", consumed)
        assertNull(manager.pendingInviteCode.value)
    }
}
