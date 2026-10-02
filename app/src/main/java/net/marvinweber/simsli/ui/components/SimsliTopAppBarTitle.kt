package net.marvinweber.simsli.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.marvinweber.simsli.R

/**
 * Top app bar title component displaying the Simsli logo alongside the screen title.
 *
 * When synchronization is active (`isSyncing = true`), the logo smoothly animates into
 * a Material 3 Expressive [LoadingIndicator] within the same fixed 32.dp slot, preventing
 * any layout shift or UI flickering.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SimsliTopAppBarTitle(
    title: String,
    isSyncing: Boolean,
    modifier: Modifier = Modifier
) {
    // Keep the sync indicator visible for a minimum duration so fast (100ms) syncs
    // display a smooth, legible animation rather than an instantaneous flicker.
    var displaySyncing by remember { mutableStateOf(false) }

    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            displaySyncing = true
        } else if (displaySyncing) {
            delay(400)
            displaySyncing = false
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = displaySyncing,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(durationMillis = 140, delayMillis = 60)) +
                            scaleIn(initialScale = 0.75f, animationSpec = tween(140, delayMillis = 60)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = 100)) +
                                    scaleOut(targetScale = 0.75f, animationSpec = tween(100))
                        )
                },
                label = "SimsliLogoSyncTransition"
            ) { syncing ->
                if (syncing) {
                    LoadingIndicator(
                        modifier = Modifier.size(30.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_simsli_logo),
                        contentDescription = "Simsli logo",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
