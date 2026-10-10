package net.marvinweber.simsli.domain

/**
 * Compares the app's version against a server-advertised minimum (`min_app_version`).
 *
 * Lenient semver-ish parsing: "0.3", "0.3.0", and "0.3.0-beta" all work; anything
 * unparseable means "cannot tell" and is treated as *not* outdated (never nag on garbage).
 */
object AppVersionCheck {

    /** True when [appVersion] is older than the required [minAppVersion]. */
    fun isOutdated(minAppVersion: String, appVersion: String): Boolean {
        val required = parse(minAppVersion) ?: return false
        val current = parse(appVersion) ?: return false
        return compare(required, current) > 0
    }

    /** Element-wise major.minor.patch comparison. */
    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in a.indices) {
            if (a[i] != b[i]) return a[i] - b[i]
        }
        return 0
    }

    /** Major.minor.patch as a comparable list; missing components count as 0. */
    private fun parse(version: String): List<Int>? {
        val core = version.substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.isEmpty() || parts.size > 3) return null
        return parts.map {
            it.toIntOrNull() ?: return null
        }.let { it + List(3 - it.size) { 0 } }
    }
}
