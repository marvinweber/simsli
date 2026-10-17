package net.marvinweber.simsli.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import kotlinx.coroutines.delay
import net.marvinweber.simsli.R

/** What the fixed 32.dp slot left of the title currently shows. */
private enum class TitleSlot { Offline, Syncing, Pending, Logo }

/**
 * Top app bar title component displaying the Simsli logo alongside the screen title.
 *
 * The slot shows (in precedence order): a warning icon while the server cannot be
 * reached ([isOffline], tap for an explanatory toast), a Material 3 Expressive
 * [LoadingIndicator] while synchronization is active ([isSyncing]), a subdued
 * upload icon while signed-in writes await their sync run ([hasPendingWrites],
 * tap for an explanatory toast), otherwise the logo — all within the same fixed
 * 32.dp space, preventing any layout shift or UI flickering.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SimsliTopAppBarTitle(
    title: String,
    isSyncing: Boolean,
    isOffline: Boolean,
    hasPendingWrites: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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

    val slot = when {
        isOffline -> TitleSlot.Offline
        displaySyncing -> TitleSlot.Syncing
        hasPendingWrites -> TitleSlot.Pending
        else -> TitleSlot.Logo
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
                targetState = slot,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(durationMillis = 140, delayMillis = 60)) +
                            scaleIn(initialScale = 0.75f, animationSpec = tween(140, delayMillis = 60)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = 100)) +
                                    scaleOut(targetScale = 0.75f, animationSpec = tween(100))
                        )
                },
                label = "SimsliLogoSyncTransition"
            ) { target ->
                when (target) {
                    TitleSlot.Offline -> Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = stringResource(R.string.sync_offline),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.sync_offline_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    )
                    TitleSlot.Syncing -> LoadingIndicator(
                        modifier = Modifier.size(30.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    TitleSlot.Pending -> Icon(
                        imageVector = Icons.Outlined.CloudUpload,
                        contentDescription = stringResource(R.string.sync_pending),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.sync_pending_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    )
                    TitleSlot.Logo -> Image(
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
