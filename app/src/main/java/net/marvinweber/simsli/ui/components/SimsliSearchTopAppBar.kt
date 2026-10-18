package net.marvinweber.simsli.ui.components

import androidx.compose.ui.res.stringResource
import net.marvinweber.simsli.R

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat

/**
 * Top app bar with built-in search (SEARCH-1): a search IconButton next to the
 * household chip expands an inline search field — the title animates out to the
 * left, the chip out to the right, mirroring the house specs of
 * [SimsliTopAppBarTitle] and the filter-bar reset button.
 *
 * The component is stateless: [isSearchActive] and [query] are hoisted by the
 * calling screen (rememberSaveable), so search state survives tab switches.
 *
 * The field's trailing ✕ clears the query but stays in search mode; exiting is
 * either the close button in the actions slot or system back (both invoke
 * [onCloseSearch], which the caller uses to also reset the query).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimsliSearchTopAppBar(
    title: String,
    isSyncing: Boolean,
    isOffline: Boolean,
    hasPendingWrites: Boolean,
    isSearchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onCloseSearch: () -> Unit,
    searchPlaceholder: String,
    householdIcon: String?,
    householdName: String?,
    onHouseholdClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = isSearchActive) { onCloseSearch() }

    // Auto-focus only on a fresh activation via the search icon — not when the
    // tab (and with it this bar) re-enters composition with search still active.
    val pendingAutoFocus = remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    // Two-stage back: with the keyboard open it just dismisses the keyboard and
    // unfocuses the field; only from the unfocused state does back leave search.
    // IME visibility is read from the view at press time — compose's consumed
    // WindowInsets can lag behind the real window state here.
    BackHandler(enabled = isSearchActive) {
        val imeVisible = WindowInsetsCompat
            .toWindowInsetsCompat(view.rootWindowInsets)
            .isVisible(WindowInsetsCompat.Type.ime())
        if (imeVisible) {
            keyboard?.hide()
            focusManager.clearFocus()
        } else {
            onCloseSearch()
        }
    }

    LaunchedEffect(isSearchActive) {
        if (!isSearchActive) keyboard?.hide()
    }

    TopAppBar(
        modifier = modifier,
        title = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    if (targetState) {
                        // Entering search: title out to the left, field expands from the end
                        (fadeIn(animationSpec = tween(durationMillis = 140, delayMillis = 60)) +
                                expandHorizontally(
                                    expandFrom = Alignment.End,
                                    animationSpec = tween(durationMillis = 180, delayMillis = 60)
                                )).togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = 100)) +
                                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(100))
                        )
                    } else {
                        // Leaving search: reverse
                        (fadeIn(animationSpec = tween(durationMillis = 140, delayMillis = 60)) +
                                slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(140, delayMillis = 60))).togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = 100)) +
                                    shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(140))
                        )
                    }
                },
                label = "SimsliSearchTitleTransition"
            ) { searching ->
                if (searching) {
                    SearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = searchPlaceholder,
                        autoFocus = pendingAutoFocus.value,
                        onAutoFocusConsumed = { pendingAutoFocus.value = false }
                    )
                } else {
                    SimsliTopAppBarTitle(
                        title = title,
                        isSyncing = isSyncing,
                        isOffline = isOffline,
                        hasPendingWrites = hasPendingWrites
                    )
                }
            }
        },
        actions = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    if (targetState) {
                        // Entering search: chip + search icon out to the right, close button in
                        (fadeIn(animationSpec = tween(durationMillis = 140, delayMillis = 60)) +
                                slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(140, delayMillis = 60))).togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = 100)) +
                                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(100))
                        )
                    } else {
                        // Leaving search: reverse
                        (fadeIn(animationSpec = tween(durationMillis = 140, delayMillis = 60)) +
                                slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(140, delayMillis = 60))).togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = 100)) +
                                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(100))
                        )
                    }
                },
                label = "SimsliSearchActionsTransition"
            ) { searching ->
                if (searching) {
                    IconButton(onClick = onCloseSearch) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = stringResource(R.string.common_close_search)
                        )
                    }
                } else {
                    // AnimatedContent places its content in a Box — without an explicit
                    // Row the icon and chip would overlap instead of sitting side by side.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            pendingAutoFocus.value = true
                            onSearchClick()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.common_search)
                            )
                        }
                        SimsliTopAppBarAccountButton(
                            householdIcon = householdIcon,
                            householdName = householdName,
                            onClick = onHouseholdClick
                        )
                    }
                }
            }
        }
    )
}

/** The inline search field: focuses on fresh activation only, a clear ✕ (only while the field has the cursor), and an IME Search action that just hides the keyboard. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    autoFocus: Boolean,
    onAutoFocusConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(Unit) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboard?.show()
            onAutoFocusConsumed()
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 4.dp)
            .focusRequester(focusRequester),
        shape = RoundedCornerShape(28.dp),
        singleLine = true,
        placeholder = { Text(text = placeholder) },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            // Always keep the slot content composed: conditionally emitting it
            // (e.g. when focus flips right after a tab switch back) can corrupt the
            // text field's subcomposed layout and misplace the icon. Instead the
            // button is hidden via alpha + cleared semantics.
            val visible = isFocused && query.isNotEmpty()
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier
                    .alpha(if (visible) 1f else 0f)
                    .then(if (visible) Modifier else Modifier.clearAndSetSemantics { })
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.common_clear_search))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            // IME action: dismiss the keyboard and drop the cursor, like a manual dismiss
            keyboard?.hide()
            focusManager.clearFocus()
        }),
        interactionSource = interactionSource
    )
}
