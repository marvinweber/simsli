package net.marvinweber.simsli.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import net.marvinweber.simsli.wear.data.WearShoppingRepository
import net.marvinweber.simsli.wear.presentation.screens.StoreSelectionScreen
import net.marvinweber.simsli.wear.presentation.screens.StoreShoppingListScreen
import net.marvinweber.simsli.wear.presentation.theme.SimsliWearTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private lateinit var repository: WearShoppingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WearShoppingRepository(applicationContext)

        setContent {
            SimsliWearTheme {
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
    val navController = rememberSwipeDismissableNavController()
    val dataPayload by repository.dataState.collectAsState()

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
