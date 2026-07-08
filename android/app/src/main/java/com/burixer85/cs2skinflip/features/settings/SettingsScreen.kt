package com.burixer85.cs2skinflip.features.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import coil.compose.AsyncImage
import com.burixer85.cs2skinflip.core.billing.findActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.burixer85.cs2skinflip.BuildConfig
import com.burixer85.cs2skinflip.core.preferences.DefaultMarketplace
import com.burixer85.cs2skinflip.core.ui.components.PremiumBanner
import com.burixer85.cs2skinflip.core.ui.theme.AccentBlue
import com.burixer85.cs2skinflip.core.ui.theme.AccentOrange
import com.burixer85.cs2skinflip.core.ui.theme.AccentRed
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.DividerColor
import com.burixer85.cs2skinflip.core.ui.theme.PremiumGold
import com.burixer85.cs2skinflip.core.ui.theme.PremiumGoldDark
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceVariant
import com.burixer85.cs2skinflip.core.ui.theme.TextPrimary
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary

private const val PRIVACY_URL = "https://davidsevillano.github.io/cs2skinflip-legal/privacy.html"
private const val TERMS_URL = "https://davidsevillano.github.io/cs2skinflip-legal/terms.html"

@Composable
fun SettingsScreen(
    onWatchlistClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val user by viewModel.user.collectAsState()
    val marketplace by viewModel.marketplace.collectAsState()
    val deleteAccountError by viewModel.deleteAccountError.collectAsState()
    val purchaseError by viewModel.purchaseError.collectAsState()

    var showMarketplaceSheet by remember { mutableStateOf(false) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    fun signInWithSteam() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.BACKEND_URL.trimEnd('/')}/auth/steam")))
        }
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Spacer(Modifier.height(12.dp))

        // ── Account ───────────────────────────────────────────────────────────
        SectionHeader("Account")
        if (isLoggedIn && user != null) {
            // Profile info row
            AccountInfoRow(
                username = user!!.username,
                email = user!!.steamId?.let { "Steam ID: ${it.takeLast(10)}" } ?: "",
                avatarUrl = user!!.avatarUrl,
                isPremium = user!!.isPremium,
            )
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            // Plan row — tappable if free
            SettingsItem(
                icon = Icons.Default.Star,
                iconTint = PremiumGold,
                label = "Plan",
                subtitle = if (user!!.isPremium) {
                    val until = user!!.premiumUntil?.let { raw ->
                        runCatching {
                            val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                            val fmt = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
                            "Premium · until ${fmt.format(iso.parse(raw)!!)}"
                        }.getOrElse { "Premium" }
                    } ?: "Premium"
                    until
                } else "Free plan · Upgrade for unlimited alerts",
                onClick = { if (user!!.isPremium == false) showUpgradeDialog = true },
                showChevron = user!!.isPremium == false,
            )
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                iconTint = AccentRed,
                label = "Sign out",
                subtitle = null,
                onClick = { showSignOutDialog = true },
                showChevron = false,
            )
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsItem(
                icon = Icons.Default.Delete,
                iconTint = AccentRed,
                label = "Delete account",
                subtitle = null,
                onClick = { showDeleteAccountDialog = true },
                showChevron = false,
            )
        } else {
            SettingsItem(
                icon = Icons.Default.AccountCircle,
                iconTint = AccentOrange,
                label = "Sign in with Steam",
                subtitle = "Required to manage price alerts",
                onClick = { signInWithSteam() },
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── My Skins ──────────────────────────────────────────────────────────
        SectionHeader("My Skins")
        SettingsItem(
            icon = Icons.Default.Bookmarks,
            iconTint = AccentOrange,
            label = "Watchlist",
            subtitle = "Skins you're tracking",
            onClick = onWatchlistClick,
        )

        Spacer(Modifier.height(8.dp))

        // ── Market data ───────────────────────────────────────────────────────
        SectionHeader("Market Data")
        SettingsItem(
            icon = Icons.Default.Analytics,
            iconTint = AccentBlue,
            label = "Default Marketplace",
            subtitle = marketplace.label,
            onClick = { showMarketplaceSheet = true },
        )

        Spacer(Modifier.height(8.dp))

        // ── About ─────────────────────────────────────────────────────────────
        SectionHeader("About")
        SettingsItem(
            icon = Icons.Default.PrivacyTip,
            iconTint = TextSecondary,
            label = "Privacy Policy",
            subtitle = null,
            onClick = { openUrl(PRIVACY_URL) },
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
        )
        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
        SettingsItem(
            icon = Icons.Default.Description,
            iconTint = TextSecondary,
            label = "Terms of Service",
            subtitle = null,
            onClick = { openUrl(TERMS_URL) },
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
        )

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CS2 SkinFlip", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 14.sp)
                Text("Version 1.0.0 · MVP", fontSize = 12.sp, color = TextSecondary)
                Text("Prices are for informational purposes only", fontSize = 11.sp, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ── Sheets & dialogs ─────────────────────────────────────────────────────
    if (showMarketplaceSheet) {
        MarketplaceSheet(
            current = marketplace,
            onSelect = {
                viewModel.setMarketplace(it)
                showMarketplaceSheet = false
            },
            onDismiss = { showMarketplaceSheet = false },
        )
    }

    if (showUpgradeDialog) {
        UpgradeDialog(
            onDismiss = { showUpgradeDialog = false },
            onUpgradeClick = {
                context.findActivity()?.let { viewModel.purchasePremium(it) }
                showUpgradeDialog = false
            },
        )
    }

    if (showSignOutDialog) {
        SignOutDialog(
            onConfirm = {
                viewModel.signOut()
                showSignOutDialog = false
            },
            onDismiss = { showSignOutDialog = false },
        )
    }

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            onConfirm = {
                viewModel.deleteAccount()
                showDeleteAccountDialog = false
            },
            onDismiss = { showDeleteAccountDialog = false },
        )
    }

    if (deleteAccountError != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearDeleteAccountError,
            containerColor = Surface,
            title = { Text("Something went wrong", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(deleteAccountError!!, color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = viewModel::clearDeleteAccountError) {
                    Text("OK", color = AccentOrange)
                }
            },
        )
    }

    if (purchaseError != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearPurchaseError,
            containerColor = Surface,
            title = { Text("Something went wrong", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(purchaseError!!, color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = viewModel::clearPurchaseError) {
                    Text("OK", color = AccentOrange)
                }
            },
        )
    }
}

// ─── Account info row (inside Account section) ───────────────────────────────

@Composable
private fun AccountInfoRow(
    username: String,
    email: String,
    avatarUrl: String?,
    isPremium: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(AccentOrange.copy(0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.size(42.dp).clip(CircleShape))
            } else {
                Icon(Icons.Default.AccountCircle, null, tint = AccentOrange, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(username, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                if (isPremium) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(PremiumGold.copy(0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = PremiumGold, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("PREMIUM", color = PremiumGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (email.isNotBlank()) Text(email, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

// ─── Plan card ───────────────────────────────────────────────────────────────

@Composable
private fun PlanCard(isPremium: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(PremiumGoldDark.copy(0.25f), SurfaceVariant))
                )
                .padding(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = PremiumGold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isPremium) "Premium Plan" else "Free Plan",
                        fontWeight = FontWeight.Bold, color = PremiumGold, fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val features = listOf(
                    Triple("Top movers & search", true, true),
                    Triple("Watchlist", true, true),
                    Triple("1 free price alert", true, true),
                    Triple("Unlimited price alerts", isPremium, false),
                )
                features.forEach { (text, available, alwaysFree) ->
                    val tag = if (available) "✓" else "✗"
                    Text(
                        "$tag  $text",
                        fontSize = 13.sp,
                        color = if (available) TextPrimary else TextSecondary,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

// ─── Section / item ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailingIcon: ImageVector = Icons.Default.ChevronRight,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = TextPrimary)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
        }
        if (showChevron) {
            Icon(trailingIcon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Sheets ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketplaceSheet(
    current: DefaultMarketplace,
    onSelect: (DefaultMarketplace) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text(
                "Default Marketplace",
                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            DefaultMarketplace.entries.forEach { m ->
                PickerRow(
                    label = m.label,
                    selected = m == current,
                    onClick = { onSelect(m) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 15.sp, color = if (selected) AccentOrange else TextPrimary)
        if (selected) {
            Icon(Icons.Default.Check, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun UpgradeDialog(onDismiss: () -> Unit, onUpgradeClick: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = PremiumGold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Go Premium", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = PremiumGold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Unlimited price alerts", color = TextPrimary, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "$4.99 · one-time payment, no subscription",
                    color = TextSecondary, fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUpgradeClick,
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Upgrade", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe later", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Sign out?", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Text(
                "You'll need to sign in again to manage your alerts.",
                color = TextSecondary, fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Sign out", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Delete account?", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Text(
                "This permanently deletes your account, watchlist, alerts, and portfolio. This can't be undone.",
                color = TextSecondary, fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}
