package net.marvinweber.simsli.ui.screens.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.R
import net.marvinweber.simsli.domain.util.PriceFormat

/**
 * Store prices for one catalog item (STORE-6): reference size (ITEM-6) on
 * top, then one price field per store in the item's effective store set.
 * Every field commits on focus loss / IME done — into the shared
 * [ItemDetailViewModel]'s pending edits, not the repository: changes are
 * saved together with the item via its Save button, so leaving this screen
 * needs no confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemPricesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.existingItem?.name ?: stringResource(R.string.prices_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading || uiState.isWaitingForItem -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.prices_reference_size),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    ReferenceSizeField(
                        initial = uiState.priceEdits
                            .effectiveReferenceSize(uiState.existingItem?.referenceSize)
                            .orEmpty(),
                        onSave = { viewModel.saveReferenceSizeDraft(it) }
                    )

                    Spacer(modifier = Modifier.padding(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.padding(12.dp))

                    Text(
                        text = stringResource(R.string.prices_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.padding(4.dp))

                    if (uiState.priceRows.isEmpty()) {
                        Text(
                            text = stringResource(R.string.prices_no_stores),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        uiState.priceRows.forEach { row ->
                            PriceFieldRow(
                                storeName = row.storeName,
                                isFavorite = row.isFavorite,
                                priceMinor = uiState.priceEdits.prices.getOrElse(row.storeId) { row.priceMinor },
                                currencyCode = uiState.household?.currency ?: "EUR",
                                onCommit = { minor -> viewModel.savePriceDraft(row.storeId, minor) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Whether the reference-size field currently holds focus (for commit-on-blur). */
@Composable
private fun ReferenceSizeField(
    initial: String,
    onSave: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                val wasFocused = focused
                focused = state.isFocused
                if (wasFocused && !state.isFocused && text != initial) {
                    onSave(text)
                }
            },
        placeholder = { Text(stringResource(R.string.prices_reference_placeholder)) },
        supportingText = { Text(stringResource(R.string.prices_reference_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        keyboardActions = KeyboardActions(
            onDone = {
                if (text != initial) onSave(text)
            }
        )
    )
}

@Composable
private fun PriceFieldRow(
    storeName: String,
    isFavorite: Boolean,
    priceMinor: Long?,
    currencyCode: String,
    onCommit: (Long?) -> Unit
) {
    val committedText = remember(priceMinor, currencyCode) {
        priceMinor?.let { PriceFormat.formatNumber(it, currencyCode) }.orEmpty()
    }
    var text by remember(committedText) { mutableStateOf(committedText) }
    var focused by remember { mutableStateOf(false) }

    fun commit() {
        val minor = if (text.isBlank()) null else PriceFormat.parseOrNull(text, currencyCode)
        if (text.isNotBlank() && minor == null) {
            // Invalid input: revert to the committed value — no error spam
            text = committedText
        } else {
            onCommit(minor)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.common_favorite_store),
                tint = Color(0xFFFFB300),
                modifier = Modifier.width(20.dp)
            )
        }
        Text(
            text = storeName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .width(140.dp)
                .onFocusChanged { state ->
                    val wasFocused = focused
                    focused = state.isFocused
                    if (wasFocused && !state.isFocused) {
                        commit()
                    }
                },
            placeholder = { Text("—") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            keyboardActions = KeyboardActions(onDone = { commit() })
        )
    }
}
