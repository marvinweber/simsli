package net.marvinweber.simsli.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.marvinweber.simsli.R

/**
 * Localized display label for a unit value (ADR 0022): known presets resolve
 * to string resources ("pcs" → "Stk." in German); anything else is
 * user-entered content and is shown as-is. The stored DB value always stays
 * the canonical preset string.
 */
@Composable
fun unitPresetLabel(value: String): String = when (value.lowercase()) {
    "pcs" -> stringResource(R.string.unit_preset_pcs)
    "pack" -> stringResource(R.string.unit_preset_pack)
    "g" -> stringResource(R.string.unit_preset_g)
    "kg" -> stringResource(R.string.unit_preset_kg)
    "ml" -> stringResource(R.string.unit_preset_ml)
    "l" -> stringResource(R.string.unit_preset_l)
    else -> value
}
