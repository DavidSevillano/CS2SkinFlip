package com.burixer85.cs2skinflip.features.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.burixer85.cs2skinflip.core.domain.model.Alert
import com.burixer85.cs2skinflip.core.domain.model.AlertType
import com.burixer85.cs2skinflip.core.ui.components.ErrorState
import com.burixer85.cs2skinflip.core.ui.components.PremiumBanner
import com.burixer85.cs2skinflip.core.ui.components.SkinListSkeleton
import com.burixer85.cs2skinflip.core.ui.theme.AccentGreen
import com.burixer85.cs2skinflip.core.ui.theme.AccentOrange
import com.burixer85.cs2skinflip.core.ui.theme.AccentRed
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.DividerColor
import com.burixer85.cs2skinflip.core.ui.theme.PremiumGold
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceElevated
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceVariant
import com.burixer85.cs2skinflip.core.ui.theme.TextPrimary
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary

private const val FREE_ALERT_LIMIT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text("Price Alerts") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
        )

        when (val state = uiState) {
            is AlertsUiState.Loading -> SkinListSkeleton(Modifier.fillMaxSize())
            is AlertsUiState.Error -> ErrorState(state.message, modifier = Modifier.fillMaxSize())
            is AlertsUiState.Success -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    // Free tier usage bar
                    if (!state.isPremium) {
                        item {
                            FreeAlertLimitCard(
                                used = state.freeAlertsUsed,
                                limit = FREE_ALERT_LIMIT
                            )
                        }
                        item {
                            PremiumBanner(onUpgradeClick = {})
                        }
                    }

                    if (state.alerts.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No alerts configured", color = TextSecondary)
                            }
                        }
                    } else {
                        item {
                            Text(
                                "${state.alerts.size} alerts",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(state.alerts, key = { it.id }) { alert ->
                            AlertRow(
                                alert = alert,
                                isPremium = state.isPremium,
                                freeUsed = state.freeAlertsUsed,
                                onToggle = { viewModel.toggleAlert(alert) },
                                onDelete = { viewModel.deleteAlert(alert.id) }
                            )
                            HorizontalDivider(
                                color = DividerColor,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FreeAlertLimitCard(used: Int, limit: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Free Alerts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "$used / $limit",
                    fontWeight = FontWeight.Bold,
                    color = if (used >= limit) AccentRed else AccentGreen,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = used.toFloat() / limit,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (used >= limit) AccentRed else AccentOrange,
                trackColor = SurfaceElevated
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (used >= limit) "Upgrade to Premium for unlimited alerts"
                else "${limit - used} alerts remaining on free plan",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun AlertRow(
    alert: Alert,
    isPremium: Boolean,
    freeUsed: Int,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = if (alert.type == AlertType.BUY_BELOW) AccentGreen else AccentOrange
    val typeIcon = if (alert.type == AlertType.BUY_BELOW) Icons.Default.TrendingDown else Icons.Default.TrendingUp
    val canEnable = isPremium || freeUsed < FREE_ALERT_LIMIT || alert.isActive

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (alert.isTriggered)
                    Brush.horizontalGradient(listOf(PremiumGold.copy(0.1f), Background))
                else Brush.horizontalGradient(listOf(Background, Background))
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
            ) {
                AsyncImage(
                    model = alert.skinImageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = alert.skinName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TextPrimary
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeColor.copy(0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(typeIcon, contentDescription = null, tint = typeColor, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(alert.type.displayName, fontSize = 11.sp, color = typeColor, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${"$%.2f".format(alert.targetPrice)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    "Current: ${"$%.2f".format(alert.currentPrice)}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }

            Switch(
                checked = alert.isActive,
                onCheckedChange = { if (canEnable) onToggle() },
                enabled = canEnable,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentOrange,
                    checkedTrackColor = AccentOrange.copy(0.3f)
                ),
                thumbContent = {
                    Icon(
                        imageVector = if (alert.isActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }
    }
}
