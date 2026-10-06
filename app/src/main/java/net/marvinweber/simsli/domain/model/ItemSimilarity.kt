package net.marvinweber.simsli.domain.model

enum class ItemMatchType {
    EXACT,
    SIMILAR
}

data class ItemMatch(
    val matchedName: String,
    val type: ItemMatchType
)

object ItemSimilarity {

    /**
     * Finds the closest match between [candidateName] and a list of [existingItems].
     * Prefers [ItemMatchType.EXACT] over [ItemMatchType.SIMILAR].
     * If multiple similar matches exist, returns the one with the lowest edit distance.
     */
    fun findBestMatch(
        candidateName: String,
        existingItems: List<Item>,
        excludeItemId: String? = null
    ): ItemMatch? {
        val trimmedCandidate = candidateName.trim()
        if (trimmedCandidate.isEmpty()) return null

        val candidates = existingItems.filter { item ->
            excludeItemId == null || item.id != excludeItemId
        }

        var bestSimilarMatch: Pair<String, Int>? = null // name to edit distance

        for (item in candidates) {
            val existingName = item.name.trim()
            if (existingName.isEmpty()) continue

            val match = evaluateMatch(trimmedCandidate, existingName) ?: continue
            when (match) {
                is MatchResult.Exact -> {
                    // Exact match takes immediate priority
                    return ItemMatch(item.name, ItemMatchType.EXACT)
                }
                is MatchResult.Similar -> {
                    val currentBestDist = bestSimilarMatch?.second ?: Int.MAX_VALUE
                    if (match.distance < currentBestDist) {
                        bestSimilarMatch = item.name to match.distance
                    }
                }
            }
        }

        return bestSimilarMatch?.let { (name, _) ->
            ItemMatch(name, ItemMatchType.SIMILAR)
        }
    }

    internal sealed class MatchResult {
        data object Exact : MatchResult()
        data class Similar(val distance: Int) : MatchResult()
    }

    internal fun evaluateMatch(candidate: String, existing: String): MatchResult? {
        // 1. Exact match (case-insensitive)
        if (candidate.equals(existing, ignoreCase = true)) {
            return MatchResult.Exact
        }

        // 2. Normalized alphanumeric match (space / hyphen / punctuation variation)
        val normCandidate = normalize(candidate)
        val normExisting = normalize(existing)
        if (normCandidate.isNotEmpty() && normCandidate.equals(normExisting, ignoreCase = true)) {
            return MatchResult.Exact
        }

        val lowerCandidate = candidate.lowercase()
        val lowerExisting = existing.lowercase()
        val minLen = minOf(lowerCandidate.length, lowerExisting.length)
        val maxLen = maxOf(lowerCandidate.length, lowerExisting.length)

        // Short words (< 5 characters, e.g. "Reis", "Mais", "Brot") require exact match
        if (minLen < 5) {
            return null
        }

        // 3. Plural / singular suffix variation (e.g. -n, -en, -s, -er)
        if (isPluralVariation(lowerCandidate, lowerExisting)) {
            return MatchResult.Similar(1)
        }

        // 4. Damerau-Levenshtein edit distance
        val dist = damerauLevenshteinDistance(lowerCandidate, lowerExisting)

        // Medium words (5-7 chars): distance <= 1
        if (maxLen <= 7 && dist <= 1) {
            return MatchResult.Similar(dist)
        }

        // Long words (8+ chars): distance <= 2 with >= 80% similarity
        if (maxLen >= 8 && dist <= 2) {
            val similarity = 1.0 - (dist.toDouble() / maxLen.toDouble())
            if (similarity >= 0.80) {
                return MatchResult.Similar(dist)
            }
        }

        return null
    }

    private fun normalize(s: String): String {
        return s.filter { it.isLetterOrDigit() }.lowercase()
    }

    private fun isPluralVariation(a: String, b: String): Boolean {
        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        val suffixes = listOf("n", "en", "s", "er")
        return suffixes.any { suffix -> longer == shorter + suffix }
    }

    fun damerauLevenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0) return len2
        if (len2 == 0) return len1

        val d = Array(len1 + 1) { IntArray(len2 + 1) }
        for (i in 0..len1) d[i][0] = i
        for (j in 0..len2) d[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                var min = minOf(
                    d[i - 1][j] + 1,       // deletion
                    d[i][j - 1] + 1,       // insertion
                    d[i - 1][j - 1] + cost // substitution
                )
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    min = minOf(min, d[i - 2][j - 2] + 1) // transposition
                }
                d[i][j] = min
            }
        }
        return d[len1][len2]
    }
}
