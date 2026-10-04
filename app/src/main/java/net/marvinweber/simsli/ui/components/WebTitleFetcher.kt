package net.marvinweber.simsli.ui.components

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebTitleFetcher @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val fetcherClient = okHttpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchTitle(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                "https://$url"
            } else {
                url
            }

            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,de;q=0.8")
                .build()

            fetcherClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.isNotEmpty() &&
                    !contentType.contains("text/html", ignoreCase = true) &&
                    !contentType.contains("application/xhtml", ignoreCase = true)
                ) {
                    return@withContext null
                }
                val source = response.body?.source() ?: return@withContext null
                val buffer = okio.Buffer()
                source.read(buffer, 65536L)
                val html = buffer.readUtf8()
                extractTitleFromHtml(html)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun extractTitleFromHtml(html: String): String? {
            // 1. og:title
            val ogMatch = Regex("""<meta\s+[^>]*property=["']og:title["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
                ?: Regex("""<meta\s+[^>]*content=["']([^"']+)["'][^>]*property=["']og:title["']""", RegexOption.IGNORE_CASE).find(html)
            if (ogMatch != null) {
                val title = unescapeHtml(ogMatch.groupValues[1].trim())
                if (title.isNotBlank()) return title
            }

            // 2. <title>...</title>
            val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            if (titleMatch != null) {
                val title = unescapeHtml(titleMatch.groupValues[1].replace(Regex("""\s+"""), " ").trim())
                if (title.isNotBlank()) return title
            }

            return null
        }

        fun unescapeHtml(input: String): String {
            return input
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&#x2F;", "/")
                .replace("&#47;", "/")
                .replace("&nbsp;", " ")
                .replace("&ndash;", "–")
                .replace("&mdash;", "—")
                .replace(Regex("""&#(\d+);""")) { match ->
                    val code = match.groupValues[1].toIntOrNull()
                    if (code != null && code in 32..65535) code.toChar().toString() else match.value
                }
                .trim()
        }
    }
}
