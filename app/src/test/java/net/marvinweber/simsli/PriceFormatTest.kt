package net.marvinweber.simsli

import net.marvinweber.simsli.domain.util.PriceFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class PriceFormatTest {

    @Test
    fun `formats EUR with locale placement and grouping`() {
        assertEquals("1,09 €", PriceFormat.format(109, "EUR", Locale.GERMANY))
        assertEquals("€1,234.56", PriceFormat.format(123456, "EUR", Locale.US))
    }

    @Test
    fun `formats JPY without fraction digits`() {
        assertEquals("500 ¥", PriceFormat.format(500, "JPY", Locale.GERMANY))
    }

    @Test
    fun `uses the preset symbol for CHF`() {
        assertEquals("2,50 Fr.", PriceFormat.format(250, "CHF", Locale.GERMANY))
    }

    @Test
    fun `fraction digits follow the currency`() {
        assertEquals(2, PriceFormat.maxFractionDigits("EUR"))
        assertEquals(0, PriceFormat.maxFractionDigits("JPY"))
        assertEquals(2, PriceFormat.maxFractionDigits("NOT_A_CURRENCY"))
    }

    @Test
    fun `parses decimal comma and dot`() {
        assertEquals(109L, PriceFormat.parseOrNull("1,09", "EUR"))
        assertEquals(109L, PriceFormat.parseOrNull("1.09", "EUR"))
        assertEquals(109L, PriceFormat.parseOrNull(" 1,09 ", "EUR"))
    }

    @Test
    fun `treats earlier separators as grouping`() {
        assertEquals(123456L, PriceFormat.parseOrNull("1.234,56", "EUR"))
        assertEquals(123456L, PriceFormat.parseOrNull("1,234.56", "EUR"))
        assertEquals(123456L, PriceFormat.parseOrNull("1 234,56", "EUR"))
    }

    @Test
    fun `rejects invalid input`() {
        assertNull(PriceFormat.parseOrNull("", "EUR"))
        assertNull(PriceFormat.parseOrNull("1,", "EUR"))
        assertNull(PriceFormat.parseOrNull("0,009", "EUR")) // more fraction digits than EUR allows
        assertNull(PriceFormat.parseOrNull("1,09a", "EUR"))
        assertNull(PriceFormat.parseOrNull("-1,09", "EUR"))
        assertNull(PriceFormat.parseOrNull("abc", "EUR"))
    }

    @Test
    fun `parses JPY as whole units`() {
        assertEquals(100L, PriceFormat.parseOrNull("100", "JPY"))
        assertNull(PriceFormat.parseOrNull("100,5", "JPY")) // JPY has no fraction digits
    }

    @Test
    fun `formatNumber is symbol-free and parse round-trips`() {
        assertEquals("1,09", PriceFormat.formatNumber(109, "EUR", Locale.GERMANY))
        assertEquals("0", PriceFormat.formatNumber(0, "JPY", Locale.GERMANY))
        for (minor in listOf(0L, 1L, 5L, 99L, 109L, 123456L, 999999999L)) {
            assertEquals(minor, PriceFormat.parseOrNull(PriceFormat.formatNumber(minor, "EUR", Locale.GERMANY), "EUR"))
        }
    }
}
