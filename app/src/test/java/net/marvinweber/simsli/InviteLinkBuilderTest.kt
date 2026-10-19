package net.marvinweber.simsli

import net.marvinweber.simsli.data.repository.HostingMode
import net.marvinweber.simsli.data.repository.impl.ServerRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Test

class InviteLinkBuilderTest {

    @Test
    fun `cloud invite link carries no host`() {
        assertEquals(
            "simsli://invite/ABCD1234",
            ServerRepositoryImpl.buildInviteDeepLink("ABCD1234", HostingMode.CLOUD, "https://api.simsli.app")
        )
    }

    @Test
    fun `self-hosted invite link appends the encoded server url`() {
        assertEquals(
            "simsli://invite/ABCD1234?host=http%3A%2F%2F10.0.2.2%3A8080",
            ServerRepositoryImpl.buildInviteDeepLink("ABCD1234", HostingMode.SELF_HOSTED, "http://10.0.2.2:8080")
        )
    }

    @Test
    fun `self-hosted invite link encodes special characters`() {
        assertEquals(
            "simsli://invite/ABCD1234?host=https%3A%2F%2Fsimsli.example.com%2Fserver",
            ServerRepositoryImpl.buildInviteDeepLink("ABCD1234", HostingMode.SELF_HOSTED, "https://simsli.example.com/server")
        )
    }
}
