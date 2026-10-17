package net.marvinweber.simsli.domain.model

/**
 * Preset currencies for the household currency setting (HH-9).
 * Display-only: the symbol is rendered next to store prices (STORE-6);
 * no conversion, no calculations.
 */
data class CurrencyPreset(val code: String, val symbol: String)

object CurrencyPresets {
    val ALL = listOf(
        CurrencyPreset("EUR", "€"),
        CurrencyPreset("USD", "$"),
        CurrencyPreset("GBP", "£"),
        CurrencyPreset("CHF", "Fr."),
        CurrencyPreset("SEK", "kr"),
        CurrencyPreset("NOK", "kr"),
        CurrencyPreset("DKK", "kr"),
        CurrencyPreset("PLN", "zł"),
        CurrencyPreset("CZK", "Kč"),
        CurrencyPreset("HUF", "Ft"),
        CurrencyPreset("TRY", "₺"),
        CurrencyPreset("JPY", "¥"),
        CurrencyPreset("CNY", "¥"),
        CurrencyPreset("CAD", "C$"),
        CurrencyPreset("AUD", "A$"),
        CurrencyPreset("INR", "₹"),
    )

    /** Symbol for a stored currency code, falling back to the code itself. */
    fun symbolFor(code: String): String =
        ALL.firstOrNull { it.code == code }?.symbol ?: code
}
