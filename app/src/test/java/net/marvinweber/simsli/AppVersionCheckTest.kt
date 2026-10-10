package net.marvinweber.simsli

import net.marvinweber.simsli.domain.AppVersionCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionCheckTest {

    @Test
    fun `older app is outdated`() {
        assertTrue(AppVersionCheck.isOutdated("0.3.0", "0.2.9"))
        assertTrue(AppVersionCheck.isOutdated("1.0.0", "0.9.9"))
        assertTrue(AppVersionCheck.isOutdated("0.10.0", "0.9.5"))
    }

    @Test
    fun `equal or newer app is not outdated`() {
        assertFalse(AppVersionCheck.isOutdated("0.3.0", "0.3.0"))
        assertFalse(AppVersionCheck.isOutdated("0.3.0", "1.0.0"))
    }

    @Test
    fun `two-component minimum is padded with zero`() {
        assertTrue(AppVersionCheck.isOutdated("0.4", "0.3.5"))
        assertFalse(AppVersionCheck.isOutdated("0.4", "0.4.0"))
    }

    @Test
    fun `suffixes are ignored`() {
        assertTrue(AppVersionCheck.isOutdated("0.3.0", "0.2.9-beta"))
        assertFalse(AppVersionCheck.isOutdated("0.3.0-rc1", "0.3.0"))
    }

    @Test
    fun `unparseable versions never warn`() {
        assertFalse(AppVersionCheck.isOutdated("not-a-version", "0.3.0"))
        assertFalse(AppVersionCheck.isOutdated("0.3.0", ""))
        assertFalse(AppVersionCheck.isOutdated("", "0.3.0"))
    }
}
