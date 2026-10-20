package net.marvinweber.simsli

import net.marvinweber.simsli.ui.screens.onboarding.CATEGORY_PRESETS
import net.marvinweber.simsli.ui.screens.onboarding.CategoryPresets
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryPresetsTest {

    @Test
    fun `CAT-4 presets contain exactly 11 entries`() {
        assertEquals(11, CATEGORY_PRESETS.size)
    }

    @Test
    fun `preset emojis match the spec order`() {
        val emojis = CATEGORY_PRESETS.map { it.emoji }
        assertEquals(
            listOf("🍎", "🥖", "🥛", "🥩", "🍝", "❄️", "🧃", "🍫", "🧻", "🧴", "📦"),
            emojis
        )
    }

    @Test
    fun `resolved names are localized in display order`() {
        val presets = CategoryPresets { resId -> "name$resId" }

        val resolved = presets.resolved()

        assertEquals(11, resolved.size)
        resolved.forEachIndexed { index, pair ->
            assertEquals("name${CATEGORY_PRESETS[index].nameRes}", pair.first)
            assertEquals(CATEGORY_PRESETS[index].emoji, pair.second)
        }
    }
}
