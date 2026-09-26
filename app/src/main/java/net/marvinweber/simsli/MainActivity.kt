package net.marvinweber.simsli

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthDeepLink(intent)
        setContent {
            SimsliTheme {
                val navController = rememberNavController()
                SimsliNavHost(navController = navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthDeepLink(intent)
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        if (intent == null) return
        lifecycleScope.launch {
            authRepository.handleDeepLink(intent)
        }
    }
}