package net.marvinweber.simsli.wear.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import net.marvinweber.simsli.wear.common.WearStoreSummary

@Composable
fun StoreSelectionScreen(
    stores: List<WearStoreSummary>,
    onStoreSelected: (storeId: String?, storeName: String) -> Unit
) {
    if (stores.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Simsli",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "Connecting to phone…",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader(
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = "Stores",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }

        items(stores, key = { it.id ?: "all_stores" }) { store ->
            val isAllStores = store.id == null
            Button(
                onClick = { onStoreSelected(store.id, store.name) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAllStores) Color(0xFF263238) else Color(0xFF1E2225),
                    contentColor = Color.White,
                    secondaryContentColor = if (store.activeCount > 0) Color(0xFF81C784) else Color(0xFF757575),
                    iconColor = if (isAllStores) Color(0xFFA5D6A7) else Color(0xFF81C784)
                ),
                label = {
                    Text(
                        text = store.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                },
                secondaryLabel = {
                    val countText = when (store.activeCount) {
                        0 -> "Empty"
                        1 -> "1 item"
                        else -> "${store.activeCount} items"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (store.activeCount > 0) Color(0xFF81C784) else Color(0xFF757575)
                    )
                },
                icon = {
                    Icon(
                        imageVector = if (isAllStores) Icons.Default.ShoppingCart else Icons.Default.Storefront,
                        contentDescription = null,
                        tint = if (isAllStores) Color(0xFFA5D6A7) else Color(0xFF81C784)
                    )
                }
            )
        }
    }
}
