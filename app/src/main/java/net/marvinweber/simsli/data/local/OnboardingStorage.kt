package net.marvinweber.simsli.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists whether the user completed onboarding (SCREENS-5): created a household
 * or joined one. Not auth state — deliberately separate from [AuthTokenStorage]
 * and untouched by sign-out. Read once per process to pick the start destination.
 */
interface OnboardingStorage {
    /** True once the user completed onboarding (created or joined a household). */
    val isDone: Boolean

    fun markDone()
}

@Singleton
class OnboardingStorageImpl @Inject constructor(
    @ApplicationContext context: Context
) : OnboardingStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val isDone: Boolean
        get() = prefs.getBoolean(KEY_DONE, false)

    override fun markDone() {
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "simsli_onboarding_prefs"
        private const val KEY_DONE = "simsli_onboarding_done"
    }
}
