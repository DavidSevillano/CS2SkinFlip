package com.burixer85.cs2skinflip.features.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.burixer85.cs2skinflip.BuildConfig
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.preferences.AppLanguage
import com.burixer85.cs2skinflip.core.preferences.DefaultMarketplace
import com.burixer85.cs2skinflip.core.preferences.LocaleHelper
import com.burixer85.cs2skinflip.core.ui.components.PremiumBanner
import com.burixer85.cs2skinflip.core.ui.components.SkeletonBox
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
    onPortfolioClick: () -> Unit,
    onSteamSessionClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val accountState by viewModel.accountState.collectAsState()
    val marketplace by viewModel.marketplace.collectAsState()
    val steamSessionConnected by viewModel.steamSessionConnected.collectAsState()

    // Coming back from the Steam login screen re-enters this composition —
    // re-read the cookie jar so the row reflects the fresh session.
    LaunchedEffect(Unit) { viewModel.refreshSteamSession() }
    val deleteAccountError by viewModel.deleteAccountError.collectAsState()
    val purchaseError by viewModel.purchaseError.collectAsState()

    var showMarketplaceSheet by remember { mutableStateOf(false) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var currentAppLanguage by remember { mutableStateOf(LocaleHelper.getLanguage(context)) }

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
            Text(stringResource(R.string.tab_settings), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Spacer(Modifier.height(12.dp))

        // ── Account ───────────────────────────────────────────────────────────
        SectionHeader(stringResource(R.string.settings_section_account))
        when (val account = accountState) {
            is AccountUiState.Loading -> AccountLoadingRow()
            is AccountUiState.SignedIn -> {
                val user = account.user
                // Profile info row
                AccountInfoRow(
                    username = user.username,
                    email = user.steamId?.let { stringResource(R.string.settings_steam_id_label, it.takeLast(10)) } ?: "",
                    avatarUrl = user.avatarUrl,
                    isPremium = user.isPremium,
                )
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                // Plan row — tappable if free
                val premiumLabel = stringResource(R.string.settings_plan_premium)
                SettingsItem(
                    icon = Icons.Default.Star,
                    iconTint = PremiumGold,
                    label = stringResource(R.string.settings_item_plan),
                    subtitle = if (user.isPremium) {
                        user.premiumUntil?.let { raw ->
                            runCatching {
                                val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                                val fmt = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
                                stringResource(R.string.settings_plan_premium_until, fmt.format(iso.parse(raw)!!))
                            }.getOrElse { premiumLabel }
                        } ?: premiumLabel
                    } else stringResource(R.string.settings_plan_free_subtitle),
                    onClick = { if (user.isPremium == false) showUpgradeDialog = true },
                    showChevron = user.isPremium == false,
                )
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = AccentRed,
                    label = stringResource(R.string.settings_item_sign_out),
                    subtitle = null,
                    onClick = { showSignOutDialog = true },
                    showChevron = false,
                )
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Delete,
                    iconTint = AccentRed,
                    label = stringResource(R.string.settings_item_delete_account),
                    subtitle = null,
                    onClick = { showDeleteAccountDialog = true },
                    showChevron = false,
                )
            }
            is AccountUiState.SignedOut -> SettingsItem(
                icon = Icons.Default.AccountCircle,
                iconTint = AccentOrange,
                label = stringResource(R.string.settings_item_sign_in_steam),
                subtitle = stringResource(R.string.settings_item_sign_in_subtitle),
                onClick = { signInWithSteam() },
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── My Skins ──────────────────────────────────────────────────────────
        SectionHeader(stringResource(R.string.settings_section_my_skins))
        SettingsItem(
            icon = Icons.Default.Bookmarks,
            iconTint = AccentOrange,
            label = stringResource(R.string.watchlist_title),
            subtitle = stringResource(R.string.settings_item_watchlist_subtitle),
            onClick = onWatchlistClick,
        )
        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
        SettingsItem(
            icon = Icons.Default.AttachMoney,
            iconTint = AccentOrange,
            label = stringResource(R.string.tab_portfolio),
            subtitle = stringResource(R.string.settings_item_portfolio_subtitle),
            onClick = onPortfolioClick,
        )

        Spacer(Modifier.height(8.dp))

        // ── General ───────────────────────────────────────────────────────────
        SectionHeader(stringResource(R.string.settings_section_general))
        SettingsItem(
            icon = Icons.Default.Language,
            iconTint = AccentBlue,
            label = stringResource(R.string.settings_language),
            subtitle = currentAppLanguage.displayName(),
            onClick = { showLanguageSheet = true },
        )

        Spacer(Modifier.height(8.dp))

        // ── Market data ───────────────────────────────────────────────────────
        SectionHeader(stringResource(R.string.settings_section_market_data))
        SettingsItem(
            icon = Icons.Default.Analytics,
            iconTint = AccentBlue,
            label = stringResource(R.string.settings_item_default_marketplace),
            subtitle = stringResource(marketplace.labelRes),
            onClick = { showMarketplaceSheet = true },
        )
        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
        SettingsItem(
            icon = Icons.Default.Link,
            iconTint = Color(0xFF66C0F4), // Steam blue, same as the price row
            label = stringResource(R.string.settings_item_steam_session),
            subtitle = stringResource(
                if (steamSessionConnected) R.string.settings_steam_session_connected
                else R.string.settings_steam_session_disconnected
            ),
            onClick = {
                if (steamSessionConnected) viewModel.disconnectSteamSession() else onSteamSessionClick()
            },
            showChevron = !steamSessionConnected,
        )

        Spacer(Modifier.height(8.dp))

        // ── About ─────────────────────────────────────────────────────────────
        SectionHeader(stringResource(R.string.settings_section_about))
        SettingsItem(
            icon = Icons.Default.PrivacyTip,
            iconTint = TextSecondary,
            label = stringResource(R.string.settings_item_privacy_policy),
            subtitle = null,
            onClick = { openUrl(PRIVACY_URL) },
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
        )
        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
        SettingsItem(
            icon = Icons.Default.Description,
            iconTint = TextSecondary,
            label = stringResource(R.string.settings_item_terms_of_service),
            subtitle = null,
            onClick = { openUrl(TERMS_URL) },
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
        )

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 14.sp)
                Text(stringResource(R.string.settings_version_info), fontSize = 12.sp, color = TextSecondary)
                Text(stringResource(R.string.settings_disclaimer), fontSize = 11.sp, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ── Sheets & dialogs ─────────────────────────────────────────────────────
    if (showLanguageSheet) {
        LanguageSheet(
            current = currentAppLanguage,
            onSelect = { language ->
                LocaleHelper.setLanguage(context, language)
                currentAppLanguage = language
                showLanguageSheet = false
                context.findActivity()?.recreate()
            },
            onDismiss = { showLanguageSheet = false },
        )
    }

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
                Toast.makeText(context, R.string.settings_signed_out_toast, Toast.LENGTH_SHORT).show()
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
            title = { Text(stringResource(R.string.error_generic), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(deleteAccountError!!, color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = viewModel::clearDeleteAccountError) {
                    Text(stringResource(R.string.action_ok), color = AccentOrange)
                }
            },
        )
    }

    if (purchaseError != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearPurchaseError,
            containerColor = Surface,
            title = { Text(stringResource(R.string.error_generic), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(purchaseError!!, color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = viewModel::clearPurchaseError) {
                    Text(stringResource(R.string.action_ok), color = AccentOrange)
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
                            Text(stringResource(R.string.settings_premium_badge), color = PremiumGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (email.isNotBlank()) Text(email, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

/**
 * Placeholder shown while the auth check / profile fetch is still in flight.
 * Mirrors the row count of the signed-in layout (profile row + 3 item rows) so the
 * screen doesn't visibly grow/jump once the real content replaces the skeleton.
 */
@Composable
private fun AccountLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(modifier = Modifier.size(42.dp), shape = CircleShape)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
            Spacer(Modifier.height(6.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp))
        }
    }
    repeat(3) {
        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
        SettingsItemSkeletonRow()
    }
}

@Composable
private fun SettingsItemSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(8.dp))
        Spacer(Modifier.width(14.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(15.dp))
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
                        stringResource(if (isPremium) R.string.settings_premium_plan else R.string.settings_free_plan),
                        fontWeight = FontWeight.Bold, color = PremiumGold, fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val features = listOf(
                    Triple(stringResource(R.string.settings_feature_top_movers_search), true, true),
                    Triple(stringResource(R.string.watchlist_title), true, true),
                    Triple(stringResource(R.string.settings_feature_one_free_alert), true, true),
                    Triple(stringResource(R.string.premium_feature_unlimited_alerts), isPremium, false),
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
private fun LanguageSheet(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.settings_language),
                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            AppLanguage.entries.forEach { language ->
                PickerRow(
                    label = language.displayName(),
                    selected = language == current,
                    onClick = { onSelect(language) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

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
                stringResource(R.string.settings_default_marketplace),
                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            DefaultMarketplace.entries.forEach { m ->
                PickerRow(
                    label = stringResource(m.labelRes),
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
                Text(stringResource(R.string.go_premium_title), fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = PremiumGold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.premium_feature_unlimited_alerts), color = TextPrimary, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.premium_price_onetime),
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
                Text(stringResource(R.string.action_upgrade), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.maybe_later), color = TextSecondary)
            }
        },
    )
}

@Composable
private fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(stringResource(R.string.settings_sign_out_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Text(
                stringResource(R.string.settings_sign_out_body),
                color = TextSecondary, fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(stringResource(R.string.settings_sign_out_button), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        },
    )
}

@Composable
private fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(stringResource(R.string.settings_delete_account_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Text(
                stringResource(R.string.settings_delete_account_body),
                color = TextSecondary, fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(stringResource(R.string.action_delete), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        },
    )
}
