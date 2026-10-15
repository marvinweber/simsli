package net.marvinweber.simsli.domain.util

import android.icu.text.Collator
import java.util.Locale

/**
 * Locale-aware, case-insensitive alphabetical ordering for user-facing names
 * (LIST-7): Collator instead of raw string comparison, so "Äpfel" sorts next
 * to "Ananas" and case doesn't split words apart.
 *
 * Collator instances are stateful and not thread-safe; getInstance is
 * expensive, so one instance is cached per thread (combine lambdas run on
 * arbitrary dispatcher threads) — same pattern as [SearchFold].
 */
object NameOrder {

    private val collator = object : ThreadLocal<Collator>() {
        override fun initialValue(): Collator =
            Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
    }

    fun <T> byName(getName: (T) -> String): Comparator<T> =
        compareBy(collator.get()!!, getName)
}
