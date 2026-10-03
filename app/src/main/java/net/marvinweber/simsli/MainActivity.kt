package net.marvinweber.simsli

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.ui.navigation.SimsliNavHost
import net.marvinweber.simsli.ui.theme.SimsliTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var quickActionManager: net.marvinweber.simsli.ui.navigation.QuickActionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthDeepLink(intent)
        handleQuickAction(intent)
        setContent {
            SimsliTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    SimsliNavHost(navController = navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
        handleQuickAction(intent)
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        if (intent == null) return
        lifecycleScope.launch {
            authRepository.handleDeepLink(intent)
        }
    }

    private fun handleQuickAction(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        val action = intent.action
        if (data?.scheme == "simsli" && data.host == "list" && (data.path == "/add" || data.path == "add")) {
            quickActionManager.trigger(net.marvinweber.simsli.ui.navigation.QuickAction.ADD_ITEM)
        } else if (action == "net.marvinweber.simsli.action.ADD_ITEM") {
            quickActionManager.trigger(net.marvinweber.simsli.ui.navigation.QuickAction.ADD_ITEM)
        }
    }
}