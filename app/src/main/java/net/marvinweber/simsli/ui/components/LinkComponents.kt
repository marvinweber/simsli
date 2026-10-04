package net.marvinweber.simsli.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.marvinweber.simsli.domain.model.ItemLink

object LinkUtils {
    // Regex matching http://, https://, or www. links
    private val URL_REGEX = Regex(
        """(?i)(?:https?://|\bwww\.)[a-z0-9]+(?:[-._][a-z0-9]+)*\.[a-z]{2,}(?::[0-9]{1,5})?(?:/[^\s<>"{}|\\^`\[\]]*)?"""
    )

    data class StripResult(
        val remainingText: String,
        val extractedUrls: List<String>
    )

    fun extractUrls(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return URL_REGEX.findAll(text)
            .map { match ->
                val raw = match.value.trimEnd('.', ',', ';', ':', ')', '>', ']', '}')
                if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
            }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    /**
     * Extracts all URLs from the text, strips them out, and returns the cleaned text
     * along with the extracted URLs.
     */
    fun stripUrls(text: String): StripResult {
        val matches = URL_REGEX.findAll(text).toList()
        if (matches.isEmpty()) {
            return StripResult(text, emptyList())
        }

        var result = text
        val urls = mutableListOf<String>()
        for (match in matches.reversed()) {
            val raw = match.value.trimEnd('.', ',', ';', ':', ')', '>', ']', '}')
            val normalized = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
            urls.add(0, normalized)
            val range = match.range.first until (match.range.first + raw.length)
            result = result.removeRange(range)
        }

        val cleanedText = result.lines()
            .map { it.replace(Regex("""[ \t]{2,}"""), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        return StripResult(cleanedText, urls.distinct())
    }

    fun containsLink(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return URL_REGEX.containsMatchIn(text)
    }

    fun getHost(url: String): String {
        return try {
            val normalized = if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                url
            } else {
                "https://$url"
            }
            val javaUri = java.net.URI(normalized)
            val host = javaUri.host.orEmpty()
            if (host.startsWith("www.", ignoreCase = true)) host.substring(4) else host
        } catch (_: Exception) {
            try {
                val uri = Uri.parse(url)
                val host = uri.host.orEmpty()
                if (host.startsWith("www.", ignoreCase = true)) host.substring(4) else host
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun formatLinkTitle(url: String, title: String?): String {
        val host = getHost(url)
        if (title.isNullOrBlank()) {
            return host.ifBlank { "Link" }
        }
        val truncatedTitle = if (title.trim().length > 30) {
            title.trim().take(27).trimEnd() + "..."
        } else {
            title.trim()
        }
        return if (host.isNotBlank()) {
            "$host — $truncatedTitle"
        } else {
            truncatedTitle
        }
    }
}

data class LinkInfo(
    val url: String,
    val host: String,
    val label: String,
    val appIcon: Drawable? = null,
    val faviconUrl: String? = null
)

fun resolveLinkInfo(context: Context, url: String): LinkInfo {
    val uri = try {
        Uri.parse(url)
    } catch (_: Exception) {
        return LinkInfo(url = url, host = "", label = "Link")
    }
    val host = LinkUtils.getHost(url)
    val pm = context.packageManager
    val viewIntent = Intent(Intent.ACTION_VIEW, uri)

    val resolveInfo = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(viewIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    } catch (_: Exception) {
        null
    }

    // Query generic browsers to identify if target is a specialized native app (Amazon, YouTube, etc.)
    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
    val browserPackages = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(browserIntent, PackageManager.ResolveInfoFlags.of(0))
                .map { it.activityInfo.packageName }
                .toSet()
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(browserIntent, 0)
                .map { it.activityInfo.packageName }
                .toSet()
        }
    } catch (_: Exception) {
        emptySet()
    }

    val targetPackage = resolveInfo?.activityInfo?.packageName
    val isDedicatedApp = targetPackage != null &&
        targetPackage != "android" &&
        !browserPackages.contains(targetPackage)

    return if (isDedicatedApp) {
        val appName = try {
            resolveInfo.loadLabel(pm).toString()
        } catch (_: Exception) {
            host.ifBlank { "Open" }
        }
        val appIcon = try {
            resolveInfo.loadIcon(pm)
        } catch (_: Exception) {
            null
        }
        LinkInfo(
            url = url,
            host = host,
            label = appName,
            appIcon = appIcon,
            faviconUrl = null
        )
    } else {
        val faviconUrl = if (host.isNotBlank()) {
            val scheme = if (uri.scheme.isNullOrBlank()) "https" else uri.scheme
            "$scheme://$host/favicon.ico"
        } else {
            null
        }
        LinkInfo(
            url = url,
            host = host,
            label = host.ifBlank { "Link" },
            appIcon = null,
            faviconUrl = faviconUrl
        )
    }
}

@Composable
fun LinkChip(
    url: String,
    title: String? = null,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val linkInfo = remember(url, context) {
        resolveLinkInfo(context, url)
    }

    val chipLabel = remember(url, title, linkInfo.appIcon, linkInfo.label) {
        if (!title.isNullOrBlank()) {
            LinkUtils.formatLinkTitle(url, title)
        } else if (linkInfo.appIcon != null) {
            linkInfo.label
        } else {
            LinkUtils.formatLinkTitle(url, null)
        }
    }

    AssistChip(
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
            }
        },
        leadingIcon = {
            if (linkInfo.appIcon != null) {
                AsyncImage(
                    model = linkInfo.appIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            } else if (!linkInfo.faviconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = linkInfo.faviconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    error = rememberVectorPainter(Icons.Default.Link),
                    placeholder = rememberVectorPainter(Icons.Default.Link)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        label = {
            Text(
                text = chipLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove link",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "Open link",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = modifier
    )
}

@Composable
fun ItemLinkChipsRow(
    links: List<ItemLink>,
    onRemove: ((ItemLink) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (links.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        links.forEach { link ->
            LinkChip(
                url = link.url,
                title = link.title,
                onRemove = onRemove?.let { { it(link) } }
            )
        }
    }
}

@Composable
fun LinkChipsRow(
    urls: List<String>,
    modifier: Modifier = Modifier
) {
    if (urls.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        urls.forEach { url ->
            LinkChip(url = url)
        }
    }
}
