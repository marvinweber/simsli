package net.marvinweber.simsli.ui.screens.onboarding

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import net.marvinweber.simsli.R
import javax.inject.Inject

/** One CAT-4 setup preset: a localized name resource plus its emoji. */
data class CategoryPreset(
    @StringRes val nameRes: Int,
    val emoji: String
)

/**
 * CAT-4: the 11 setup presets, offered wholesale during onboarding household
 * creation (SCREENS-5). Names are resolved to localized plain strings at
 * creation time (I18N-1) — like any user-edited category, they sync as strings.
 */
val CATEGORY_PRESETS = listOf(
    CategoryPreset(R.string.cat_preset_produce, "🍎"),
    CategoryPreset(R.string.cat_preset_bakery, "🥖"),
    CategoryPreset(R.string.cat_preset_dairy, "🥛"),
    CategoryPreset(R.string.cat_preset_meat, "🥩"),
    CategoryPreset(R.string.cat_preset_pantry, "🍝"),
    CategoryPreset(R.string.cat_preset_frozen, "❄️"),
    CategoryPreset(R.string.cat_preset_drinks, "🧃"),
    CategoryPreset(R.string.cat_preset_snacks, "🍫"),
    CategoryPreset(R.string.cat_preset_household, "🧻"),
    CategoryPreset(R.string.cat_preset_personal_care, "🧴"),
    CategoryPreset(R.string.cat_preset_other, "📦"),
)

/**
 * Resolves the preset name resources. Injectable so [OnboardingViewModel] stays
 * context-free; tests pass a plain lambda.
 */
class CategoryPresets(private val resolveName: (Int) -> String) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context::getString)

    /** Localized (name, emoji) pairs in display order; sortOrder = 1000f * (index + 1) at insertion. */
    fun resolved(): List<Pair<String, String>> =
        CATEGORY_PRESETS.map { resolveName(it.nameRes) to it.emoji }
}
