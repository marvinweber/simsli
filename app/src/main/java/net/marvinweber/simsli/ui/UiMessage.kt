package net.marvinweber.simsli.ui

import androidx.annotation.StringRes
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A localizable UI message: a string resource plus optional format args.
 *
 * ViewModels emit these instead of prebuilt strings so the message is resolved
 * (and translated) at render time, inside composition (I18N-1). Args are
 * pre-rendered strings — typically user content (names) or error details,
 * which are never translated.
 */
data class UiMessage(
    @StringRes val resId: Int,
    val args: List<String> = emptyList()
)

/** Resolves the message against the current string resources. */
@Composable
fun UiMessage.text(): String = stringResource(resId, *args.toTypedArray())

/**
 * Resolves the message outside composition (coroutines, Toasts) — use only
 * where the context locale matches the UI locale, e.g. right after collecting
 * an event inside a composable's [androidx.compose.runtime.LaunchedEffect].
 */
fun UiMessage.resolve(context: Context): String = context.getString(resId, *args.toTypedArray())
