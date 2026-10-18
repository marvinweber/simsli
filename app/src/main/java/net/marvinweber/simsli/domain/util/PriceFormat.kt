package net.marvinweber.simsli.domain.util

import net.marvinweber.simsli.domain.model.CurrencyPresets
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

/**
 * Prices are stored as integer minor units (cents) and formatted with the
 * household currency (STORE-6): locale-aware placement/grouping, symbol from
 * [CurrencyPresets], fraction digits from ISO 4217 (JPY → 0). No floats, no
 * conversion, no totals.
 */
object PriceFormat {

    /** Fraction digits for a currency code (2 for EUR, 0 for JPY); 2 fallback for unknown codes. */
    fun maxFractionDigits(currencyCode: String): Int =
        try {
            Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        } catch (_: IllegalArgumentException) {
            2
        }

    /** "1,09 €"-style render: minor units → locale currency string. */
    fun format(minor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val digits = maxFractionDigits(currencyCode)
        val format = NumberFormat.getCurrencyInstance(locale) as DecimalFormat
        val symbols = DecimalFormatSymbols.getInstance(locale)
        try {
            symbols.currency = Currency.getInstance(currencyCode)
        } catch (_: IllegalArgumentException) {
            // Unknown code: keep the locale's currency object, override the symbol below
        }
        symbols.currencySymbol = CurrencyPresets.symbolFor(currencyCode)
        format.decimalFormatSymbols = symbols
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(BigDecimal.valueOf(minor, digits))
    }

    /**
     * Symbol-free render for editable price fields ("1,09"): same digits as
     * [format] without the currency symbol, so [parseOrNull] round-trips.
     */
    fun formatNumber(minor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val digits = maxFractionDigits(currencyCode)
        val format = NumberFormat.getNumberInstance(locale) as DecimalFormat
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        format.isGroupingUsed = false
        return format.format(BigDecimal.valueOf(minor, digits))
    }

    /**
     * Parses user input ("1,09", "1.234,56", "1 234.56") into minor units, or
     * null when the text is blank or invalid. Only the last `,`/`.` counts as
     * decimal separator (earlier ones are grouping); more fraction digits than
     * the currency allows is rejected, not rounded; a trailing separator
     * ("1,") is invalid.
     */
    fun parseOrNull(text: String, currencyCode: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val lastSeparator = trimmed.indexOfLast { it == ',' || it == '.' }
        val digits = StringBuilder()
        var fractionDigits = 0
        for ((index, c) in trimmed.withIndex()) {
            when {
                c.isDigit() -> {
                    digits.append(c)
                    if (lastSeparator != -1 && index > lastSeparator) fractionDigits++
                }
                c == ',' || c == '.' || c == ' ' || c == '\u00a0' || c == '\u202f' || c == '\u2019' || c == '\'' -> {}
                else -> return null // signs, letters, anything else
            }
        }
        if (digits.isEmpty()) return null
        if (lastSeparator != -1) {
            if (fractionDigits == 0) return null // trailing separator
            if (fractionDigits > maxFractionDigits(currencyCode)) return null
        }
        return digits.toString().toLongOrNull()
    }
}
