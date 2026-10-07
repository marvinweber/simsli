package net.marvinweber.simsli.wear.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.marvinweber.simsli.wear.data.WearShoppingRepository
import net.marvinweber.simsli.wear.presentation.screens.StoreSelectionScreen
import net.marvinweber.simsli.wear.presentation.screens.StoreShoppingListScreen
import net.marvinweber.simsli.wear.presentation.theme.SimsliWearTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private lateinit var repository: WearShoppingRepository
    private val pendingDestination = MutableStateFlow<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WearShoppingRepository(applicationContext)

        handleIntent(intent)

        setContent {
            SimsliWearTheme {
                SimsliWearApp(
                    repository = repository,
                    pendingDestination = pendingDestination,
                    onClearPendingDestination = { pendingDestination.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val storeId = intent?.getStringExtra("store_id")
        val storeName = intent?.getStringExtra("store_name") ?: "Shopping List"
        if (storeId != null) {
            pendingDestination.value = Pair(storeId, storeName)
        } else if (intent != null) {
            pendingDestination.value = Pair("root", "stores")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.destroy()
    }
}

@Composable
fun SimsliWearApp(
    repository: WearShoppingRepository,
    pendingDestination: StateFlow<Pair<String, String>?>,
    onClearPendingDestination: () -> Unit
) {
    val navController = rememberSwipeDismissableNavController()
    val dataPayload by repository.dataState.collectAsState()
    val destination by pendingDestination.collectAsState()

    LaunchedEffect(destination) {
        destination?.let { (storeId, storeName) ->
            if (storeId == "root") {
                navController.popBackStack("stores", inclusive = false)
            } else {
                val encodedName = URLEncoder.encode(storeName, "UTF-8")
                val idParam = if (storeId == "all") "all" else storeId
                val route = "store/$idParam/$encodedName"
                navController.navigate(route) {
                    popUpTo("stores") { inclusive = false }
                }
            }
            onClearPendingDestination()
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "stores"
    ) {
        composable("stores") {
            StoreSelectionScreen(
                stores = dataPayload?.stores ?: emptyList(),
                onStoreSelected = { storeId, storeName ->
                    val encodedName = URLEncoder.encode(storeName, "UTF-8")
                    val idParam = storeId ?: "all"
                    navController.navigate("store/$idParam/$encodedName")
                }
            )
        }

        composable("store/{storeId}/{storeName}") { backStackEntry ->
            val storeIdParam = backStackEntry.arguments?.getString("storeId")
            val storeId = if (storeIdParam == "all") null else storeIdParam
            val rawName = backStackEntry.arguments?.getString("storeName") ?: "Shopping List"
            val storeName = try {
                URLDecoder.decode(rawName, "UTF-8")
            } catch (e: Exception) {
                rawName
            }

            val storeSummary = dataPayload?.stores?.find { it.id == storeId }

            StoreShoppingListScreen(
                storeId = storeId,
                storeName = storeName,
                categories = storeSummary?.orderedCategories ?: emptyList(),
                items = dataPayload?.items ?: emptyList(),
                onToggleItem = { entryId, done ->
                    repository.toggleItem(entryId, done)
                }
            )
        }
    }
}
