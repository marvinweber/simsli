package net.marvinweber.simsli

import net.marvinweber.simsli.ui.components.LinkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkUtilsTest {

    @Test
    fun `extractUrls finds https and http links and strips trailing punctuation`() {
        val text = "Check this recipe: https://www.chefkoch.de/rezepte/123/pasta.html. Also see http://rewe.de/offers,"
        val urls = LinkUtils.extractUrls(text)

        assertEquals(2, urls.size)
        assertEquals("https://www.chefkoch.de/rezepte/123/pasta.html", urls[0])
        assertEquals("http://rewe.de/offers", urls[1])
    }

    @Test
    fun `extractUrls normalizes www links to https`() {
        val text = "Buy on www.amazon.de/dp/B123"
        val urls = LinkUtils.extractUrls(text)

        assertEquals(1, urls.size)
        assertEquals("https://www.amazon.de/dp/B123", urls[0])
    }

    @Test
    fun `extractUrls handles parenthesis around links`() {
        val text = "Organic oat milk (https://alpro.com/de/produkte) is preferred."
        val urls = LinkUtils.extractUrls(text)

        assertEquals(1, urls.size)
        assertEquals("https://alpro.com/de/produkte", urls[0])
    }

    @Test
    fun `extractUrls deduplicates identical links`() {
        val text = "Compare https://example.com/item and https://example.com/item"
        val urls = LinkUtils.extractUrls(text)

        assertEquals(1, urls.size)
        assertEquals("https://example.com/item", urls[0])
    }

    @Test
    fun `extractUrls returns empty list for null or plain text`() {
        assertTrue(LinkUtils.extractUrls(null).isEmpty())
        assertTrue(LinkUtils.extractUrls("").isEmpty())
        assertTrue(LinkUtils.extractUrls("Buy 2 apples and whole milk").isEmpty())
    }

    @Test
    fun `containsLink checks presence of url correctly`() {
        assertTrue(LinkUtils.containsLink("https://rewe.de"))
        assertTrue(LinkUtils.containsLink("visit www.dm.de for shampoo"))
        assertFalse(LinkUtils.containsLink("Just plain text with no links"))
        assertFalse(LinkUtils.containsLink(null))
    }

    @Test
    fun `stripUrls extracts and strips single url from text`() {
        val text = "Check out https://oatly.com for details"
        val result = LinkUtils.stripUrls(text)

        assertEquals(listOf("https://oatly.com"), result.extractedUrls)
        assertEquals("Check out for details", result.remainingText)
    }

    @Test
    fun `stripUrls removes url on standalone line without leaving blank line`() {
        val text = "First line\nhttps://oatly.com\nSecond line"
        val result = LinkUtils.stripUrls(text)

        assertEquals(listOf("https://oatly.com"), result.extractedUrls)
        assertEquals("First line\nSecond line", result.remainingText)
    }

    @Test
    fun `stripUrls with only url leaves empty string`() {
        val text = "https://www.oatly.com"
        val result = LinkUtils.stripUrls(text)

        assertEquals(listOf("https://www.oatly.com"), result.extractedUrls)
        assertEquals("", result.remainingText)
    }

    @Test
    fun `stripUrls with multiple urls strips all and normalizes www`() {
        val text = "See www.rewe.de and https://aldi.de/offers"
        val result = LinkUtils.stripUrls(text)

        assertEquals(2, result.extractedUrls.size)
        assertEquals("https://www.rewe.de", result.extractedUrls[0])
        assertEquals("https://aldi.de/offers", result.extractedUrls[1])
        assertEquals("See and", result.remainingText)
    }

    @Test
    fun `formatLinkTitle formats host and title truncated to 30 chars`() {
        // Under 30 chars
        val shortFormatted = LinkUtils.formatLinkTitle("https://oatly.com/milk", "Oatly Barista Edition")
        assertEquals("oatly.com — Oatly Barista Edition", shortFormatted)

        // Over 30 chars: "Oatly Barista Edition — 100% Plant-Based" (40 chars)
        val longTitle = "Oatly Barista Edition — 100% Plant-Based"
        val longFormatted = LinkUtils.formatLinkTitle("https://oatly.com/milk", longTitle)
        // 27 chars + "..." = 30 chars title
        assertEquals("oatly.com — Oatly Barista Edition — 100...", longFormatted)

        // Null title fallback
        val noTitle = LinkUtils.formatLinkTitle("https://oatly.com/milk", null)
        assertEquals("oatly.com", noTitle)
    }

    @Test
    fun `extractTitleFromHtml parses og title and regular title with html entities`() {
        val ogHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Oatly &amp; Alpro Drinks &#8212; Best Milk">
                <title>Fallback Title</title>
            </head>
            <body>Hello</body>
            </html>
        """.trimIndent()
        val ogTitle = net.marvinweber.simsli.ui.components.WebTitleFetcher.extractTitleFromHtml(ogHtml)
        assertEquals("Oatly & Alpro Drinks — Best Milk", ogTitle)

        val standardHtml = """
            <html>
            <head>
                <title>
                    Fresh Apples &lt;Red Delicious&gt; &#39;Special&#39;
                </title>
            </head>
            </html>
        """.trimIndent()
        val standardTitle = net.marvinweber.simsli.ui.components.WebTitleFetcher.extractTitleFromHtml(standardHtml)
        assertEquals("Fresh Apples <Red Delicious> 'Special'", standardTitle)
    }
}
