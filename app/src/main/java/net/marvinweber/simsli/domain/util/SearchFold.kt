package net.marvinweber.simsli.domain.util

import android.icu.text.Transliterator
import java.util.Locale

/**
 * Folds a string into its "search shape" so that name search matches across
 * case, diacritics, and even scripts (SEARCH-1): ICU transliterates any script
 * to Latin ("Хлеб" → "Hleb") and strips the rest down to ASCII ("Äpfel" →
 * "Apfel", "Straße" → "Strasse"). Search compares the folded query against
 * folded names, so users find items regardless of which spelling they typed.
 *
 * Runs on-device only — android.icu has no JVM equivalent, so unit tests
 * inject a stub fold instead (see ListSearch.applyListSearch). The fold itself
 * is deliberately not unit-tested: the behavior is ICU's, ours is the one
 * transform ID below — smoke-tested on the emulator.
 *
 * Transliterator instances are stateful and not thread-safe; getInstance is
 * expensive, so one instance is cached per thread (combine lambdas run on
 * arbitrary dispatcher threads).
 */
object SearchFold {

    private const val TRANSFORM_ID = "Any-Latin; Latin-ASCII"

    private val transliterator = object : ThreadLocal<Transliterator>() {
        override fun initialValue(): Transliterator = Transliterator.getInstance(TRANSFORM_ID)
    }

    fun fold(value: String): String =
        transliterator.get()!!.transliterate(value).lowercase(Locale.ROOT)
}
