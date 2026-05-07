package com.burixer85.cs2skinflip.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burixer85.cs2skinflip.core.ui.components.PremiumBanner
import com.burixer85.cs2skinflip.core.ui.theme.AccentBlue
import com.burixer85.cs2skinflip.core.ui.theme.AccentGreen
import com.burixer85.cs2skinflip.core.ui.theme.AccentOrange
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.DividerColor
import com.burixer85.cs2skinflip.core.ui.theme.PremiumGold
import com.burixer85.cs2skinflip.core.ui.theme.PremiumGoldDark
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceVariant
import com.burixer85.cs2skinflip.core.ui.theme.TextPrimary
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary

@Composable
fun SettingsScreen(onAlertsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Box(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Spacer(Modifier.height(12.dp))

        // Premium card
        PremiumFeatureCard()

        Spacer(Modifier.height(8.dp))
        PremiumBanner(onUpgradeClick = {})

        Spacer(Modifier.height(16.dp))

        // Notifications section
        SectionHeader("Notifications")
        SettingsItem(
            icon = Icons.Default.Notifications,
            iconTint = AccentOrange,
            label = "Price Alerts",
            subtitle = "3 active · 5 max (free)",
            onClick = onAlertsClick
        )

        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(8.dp))

        // Market section
        SectionHeader("Market Data")
        SettingsItem(
            icon = Icons.Default.Analytics,
            iconTint = AccentBlue,
            label = "Default Marketplace",
            subtitle = "Steam",
            onClick = {}
        )
        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
        SettingsItem(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            iconTint = AccentGreen,
            label = "Currency",
            subtitle = "USD ($)",
            onClick = {}
        )

        Spacer(Modifier.height(8.dp))

        // About section
        SectionHeader("About")
        SettingsItem(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            iconTint = TextSecondary,
            label = "Privacy Policy",
            subtitle = null,
            onClick = {}
        )
        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
        SettingsItem(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            iconTint = TextSecondary,
            label = "Terms of Service",
            subtitle = null,
            onClick = {}
        )
        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CS2 SkinFlip", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 14.sp)
                Text("Version 1.0.0 · MVP", fontSize = 12.sp, color = TextSecondary)
                Text("Prices are for informational purposes only", fontSize = 11.sp, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PremiumFeatureCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(PremiumGoldDark.copy(0.25f), SurfaceVariant))
                )
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = PremiumGold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Free Plan", fontWeight = FontWeight.Bold, color = PremiumGold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(12.dp))

                val features = listOf(
                    Triple("✓", "Top movers & search", true),
                    Triple("✓", "Portfolio tracking", true),
                    Triple("✓", "5 price alerts", true),
                    Triple("✗", "Unlimited alerts", false),
                    Triple("✗", "Advanced analytics", false),
                    Triple("✗", "Float percentile data", false)
                )
                features.forEach { (icon, text, available) ->
                    Text(
                        "$icon  $text",
                        fontSize = 13.sp,
                        color = if (available) TextPrimary else TextSecondary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = TextPrimary)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}
