package net.marvinweber.simsli.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import net.marvinweber.simsli.wear.data.WearShoppingRepository

class MainActivity : ComponentActivity() {

    private lateinit var repository: WearShoppingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WearShoppingRepository(applicationContext)

        setContent {
            MaterialTheme {
                SimsliWearApp(repository = repository)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.destroy()
    }
}

@Composable
fun SimsliWearApp(repository: WearShoppingRepository) {
    val dataPayload by repository.dataState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Simsli",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        val payload = dataPayload
        if (payload == null) {
            Text(
                text = "Connecting to phone…",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                text = "Synced with phone! ✓",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF81C784),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "${payload.stores.size} stores • ${payload.items.size} items",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
