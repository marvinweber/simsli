package net.marvinweber.simsli.ui.screens.household

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Month
import java.util.Locale

/**
 * Monthly shopping activity for the household, rendered as a vertical timeline:
 * month label on the left rail, two rounded bars per month (items added /
 * items checked). Scroll back freely — the window extends as you go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdStatisticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: HouseholdStatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = viewModel::load) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }

            else -> {
                StatisticsTimeline(
                    uiState = uiState,
                    onLoadMore = viewModel::loadMore,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun StatisticsTimeline(
    uiState: HouseholdStatisticsViewModel.HouseholdStatisticsUiState,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp
        )
    ) {
        item(key = "legend") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendDot(color = MaterialTheme.colorScheme.primary, label = "Added")
                LegendDot(color = MaterialTheme.colorScheme.tertiary, label = "Checked")
            }
        }

        if (!uiState.hasAnyActivity) {
            item(key = "empty_hint") {
                Text(
                    text = "No activity recorded yet. Check off or add items while connected to start your household's statistics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
        }

        itemsIndexed(uiState.months, key = { _, m -> "${m.year}-${m.month}" }) { index, month ->
            MonthTimelineRow(
                month = month,
                maxValue = uiState.months.maxOf { maxOf(it.itemsAdded, it.itemsChecked) },
                showYear = index == 0 || month.month == 1,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        // Load-more trigger: LazyColumn disposes off-screen items, so this
        // re-enters composition (and re-fires) every time the user scrolls it
        // back into view, extending the window towards the past.
        item(key = "load_more") {
            LaunchedEffect(Unit) { onLoadMore() }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthTimelineRow(
    month: HouseholdStatisticsViewModel.TimelineMonth,
    maxValue: Int,
    showYear: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = Month.of(month.month).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            if (showYear) {
                Text(
                    text = month.year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ActivityBar(
                value = month.itemsAdded,
                maxValue = maxValue,
                color = MaterialTheme.colorScheme.primary
            )
            ActivityBar(
                value = month.itemsChecked,
                maxValue = maxValue,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun ActivityBar(
    value: Int,
    maxValue: Int,
    color: Color
) {
    val fraction = if (value <= 0) 0f else value.toFloat() / maxOf(maxValue, 1).coerceAtLeast(1)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(400),
        label = "activityBar"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction.coerceAtLeast(if (value > 0) 0.04f else 0f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (value > 0) value.toString() else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}
