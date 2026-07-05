package com.burixer85.cs2skinflip.features.alerts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.burixer85.cs2skinflip.BuildConfig
import com.burixer85.cs2skinflip.core.domain.model.Alert
import com.burixer85.cs2skinflip.core.domain.model.AlertType
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.preferences.Currency
import com.burixer85.cs2skinflip.core.ui.components.ErrorState
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
import com.burixer85.cs2skinflip.core.ui.theme.TextTertiary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onSkinClick: (String) -> Unit = {},
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val editState by viewModel.editState.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }

    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Whether granted or denied, proceed — this is informational, not a hard gate.
        viewModel.resetCreateState()
        showCreateSheet = true
    }

    fun openCreateAlertSheet() {
        if (hasNotificationPermission(context)) {
            viewModel.resetCreateState()
            showCreateSheet = true
        } else {
            showNotificationPermissionDialog = true
        }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Price Alerts", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (uiState is AlertsUiState.Success) {
                    val s = uiState as AlertsUiState.Success
                    val count = s.alerts.count { it.isActive }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentOrange.copy(0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bolt, null,
                                tint = AccentOrange, modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$count active", color = AccentOrange,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Free tier banner (only when free + has limit) ─────────────────
            if (uiState is AlertsUiState.Success) {
                val s = uiState as AlertsUiState.Success
                if (!s.isPremium && s.freeLimit != null) {
                    FreeTierBanner(
                        used = s.alerts.size,
                        limit = s.freeLimit,
                        onUpgrade = { showUpgradeDialog = true },
                    )
                }
            }

            // ── Body ──────────────────────────────────────────────────────────
            when (val state = uiState) {
                is AlertsUiState.Loading -> SkinListSkeleton(Modifier.fillMaxSize())
                is AlertsUiState.Error -> ErrorState(state.message, modifier = Modifier.fillMaxSize())
                is AlertsUiState.NotLoggedIn -> NotLoggedInView(viewModel)
                is AlertsUiState.Success -> AlertsList(
                    alerts = state.alerts,
                    onToggle = viewModel::toggleAlert,
                    onDelete = { viewModel.deleteAlert(it) },
                    onEdit = { viewModel.startEdit(it) },
                    onSkinClick = onSkinClick,
                    onAddFirst = {
                        if (state.atFreeLimit) showUpgradeDialog = true
                        else openCreateAlertSheet()
                    },
                )
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────────
        if (uiState is AlertsUiState.Success) {
            val state = uiState as AlertsUiState.Success
            FloatingActionButton(
                onClick = {
                    if (state.atFreeLimit) showUpgradeDialog = true
                    else openCreateAlertSheet()
                },
                containerColor = if (state.atFreeLimit) PremiumGold else AccentOrange,
                contentColor = if (state.atFreeLimit) Color.Black else Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .navigationBarsPadding(),
            ) {
                Icon(
                    if (state.atFreeLimit) Icons.Default.Lock else Icons.Default.Add,
                    contentDescription = if (state.atFreeLimit) "Upgrade" else "Add alert",
                )
            }
        }
    }

    // ── Upgrade dialog ────────────────────────────────────────────────────────
    if (showUpgradeDialog) {
        UpgradeDialog(onDismiss = { showUpgradeDialog = false })
    }

    // ── Notification permission rationale ───────────────────────────────────────
    if (showNotificationPermissionDialog) {
        NotificationPermissionDialog(
            onEnable = {
                showNotificationPermissionDialog = false
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = {
                showNotificationPermissionDialog = false
                viewModel.resetCreateState()
                showCreateSheet = true
            },
        )
    }

    // ── Bottom sheet for creating an alert ────────────────────────────────────
    if (showCreateSheet) {
        CreateAlertSheet(
            viewModel = viewModel,
            onDismiss = {
                showCreateSheet = false
                viewModel.resetCreateState()
            },
        )
    }

    // ── Bottom sheet for editing an existing alert ─────────────────────────────
    if (editState.alert != null) {
        EditAlertSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.cancelEdit() },
        )
    }
}

@Composable
private fun AlertsList(
    alerts: List<Alert>,
    onToggle: (Alert) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (Alert) -> Unit,
    onSkinClick: (String) -> Unit,
    onAddFirst: () -> Unit,
) {
    if (alerts.isEmpty()) {
        EmptyAlertsView(onAddFirst)
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "${alerts.size} alerts",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(alerts, key = { it.id }) { alert ->
            AlertRow(
                alert = alert,
                onToggle = { onToggle(alert) },
                onDelete = { onDelete(alert.id) },
                onEdit = { onEdit(alert) },
                onSkinClick = { onSkinClick(alert.skinId) },
            )
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item { Spacer(Modifier.height(96.dp)) }  // FAB clearance
    }
}

@Composable
private fun EmptyAlertsView(onAddFirst: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AccentOrange.copy(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.NotificationsActive, null,
                    tint = AccentOrange, modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("No alerts yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Get notified when a skin hits your target buy or sell price.",
                color = TextSecondary, textAlign = TextAlign.Center, fontSize = 14.sp,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAddFirst,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create first alert", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ─── Alert row ───────────────────────────────────────────────────────────────

@Composable
private fun AlertRow(alert: Alert, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit, onSkinClick: () -> Unit) {
    val typeColor = if (alert.type == AlertType.BUY_BELOW) AccentGreen else AccentOrange
    val typeIcon = if (alert.type == AlertType.BUY_BELOW) Icons.Default.TrendingDown else Icons.Default.TrendingUp
    val currency = Currency

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSkinClick)
            .background(
                if (alert.isTriggered) Brush.horizontalGradient(listOf(PremiumGold.copy(0.1f), Background))
                else Brush.horizontalGradient(listOf(Background, Background))
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated),
            ) {
                AsyncImage(
                    model = alert.skinImageUrl, contentDescription = null,
                    modifier = Modifier.size(48.dp), contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = alert.skinName.ifBlank { alert.skinId },
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeColor.copy(0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                alert.type.displayName, fontSize = 10.sp,
                                color = typeColor, fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        currency.format(alert.targetPrice),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary,
                    )
                    if (alert.currentPrice > 0) {
                        Spacer(Modifier.width(8.dp))
                        DiffBadge(current = alert.currentPrice, target = alert.targetPrice, type = alert.type)
                    }
                }
                if (alert.currentPrice > 0) {
                    Text(
                        "Current ${currency.format(alert.currentPrice)}",
                        fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "Edit", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }

            Switch(
                checked = alert.isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentOrange,
                    checkedTrackColor = AccentOrange.copy(0.3f),
                ),
                thumbContent = {
                    Icon(
                        imageVector = if (alert.isActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = null, modifier = Modifier.size(14.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun DiffBadge(current: Double, target: Double, type: AlertType) {
    val diffPct = (target - current) / current * 100
    // For BUY_BELOW: target should be lower than current (negative diff = closer to target)
    // For SELL_ABOVE: target should be higher than current (positive diff = closer to target)
    val isReached = when (type) {
        AlertType.BUY_BELOW -> current <= target
        AlertType.SELL_ABOVE -> current >= target
    }
    val color = if (isReached) AccentGreen else AccentRed
    val sign = if (diffPct >= 0) "+" else ""
    Text(
        "$sign%.1f%%".format(diffPct),
        fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold,
    )
}

// ─── States: paywall + not logged in ─────────────────────────────────────────

@Composable
private fun FreeTierBanner(used: Int, limit: Int, onUpgrade: () -> Unit) {
    val atLimit = used >= limit
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (atLimit) PremiumGold.copy(0.12f) else AccentOrange.copy(0.10f)
            )
            .clickable(onClick = onUpgrade)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (atLimit) Icons.Default.Lock else Icons.Default.Star,
            null,
            tint = if (atLimit) PremiumGold else AccentOrange,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (atLimit) "Free limit reached" else "Free plan",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                "$used / $limit alert used · Upgrade for unlimited",
                fontSize = 11.sp,
                color = TextSecondary,
            )
        }
        Text(
            "Upgrade",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (atLimit) PremiumGold else AccentOrange,
        )
    }
}

/** POST_NOTIFICATIONS is only a runtime permission on Android 13+ (API 33). */
private fun hasNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun NotificationPermissionDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Turn on notifications", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Text(
                "Notifications are off. Without them, you won't be notified when this skin hits your target price.",
                color = TextSecondary, fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Enable", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Continue without", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun UpgradeDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
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
                Text(
                    "You're on the free plan with 1 alert. Upgrade to Premium to unlock:",
                    color = TextSecondary, fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                listOf(
                    "Unlimited price alerts",
                    "Real-time push notifications",
                    "Float percentile data",
                    "Advanced analytics",
                ).forEach { benefit ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Star, null, tint = PremiumGold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(benefit, color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { /* TODO: subscription flow */ onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Upgrade", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Maybe later", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun NotLoggedInView(viewModel: AlertsViewModel) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var showForgotPassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.NotificationsActive, null, tint = AccentOrange, modifier = Modifier.size(56.dp))
        }
        Text(
            if (isRegister) "Create your account" else "Welcome back",
            fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPrimary,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Sign in to manage price alerts",
            color = TextSecondary, textAlign = TextAlign.Center, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        // Tab toggle: Login / Register
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AuthTab(label = "Sign in", selected = !isRegister, modifier = Modifier.weight(1f)) { isRegister = false }
            AuthTab(label = "Sign up", selected = isRegister, modifier = Modifier.weight(1f)) { isRegister = true }
        }

        Spacer(Modifier.height(20.dp))

        if (isRegister) {
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                placeholder = { Text("Username (optional)", color = TextTertiary) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            placeholder = { Text("Email", color = TextTertiary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(), colors = textFieldColors(),
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = password, onValueChange = { password = it },
            placeholder = { Text(if (isRegister) "Password (min. 8 characters)" else "Password", color = TextTertiary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), colors = textFieldColors(),
        )

        if (!isRegister) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Forgot password?",
                color = AccentOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { showForgotPassword = true },
            )
        }

        authState.errorMessage?.let { msg ->
            Spacer(Modifier.height(10.dp))
            Text(msg, color = AccentRed, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                scope.launch {
                    if (isRegister) viewModel.register(email.trim(), password, username.trim().ifBlank { null })
                    else viewModel.login(email.trim(), password)
                }
            },
            enabled = !authState.submitting && email.isNotBlank() && password.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentOrange,
                disabledContainerColor = SurfaceElevated,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (authState.submitting) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text(
                    if (isRegister) "Create account" else "Sign in",
                    fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Divider with "or"
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor)
            Text("  or  ", color = TextTertiary, fontSize = 12.sp)
            HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor)
        }

        Spacer(Modifier.height(16.dp))

        // Steam alternative
        Button(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.BACKEND_URL}/auth/steam"))
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2838)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Sign in with Steam", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showForgotPassword) {
        com.burixer85.cs2skinflip.core.ui.components.ForgotPasswordSheet(onDismiss = { showForgotPassword = false })
    }
}

@Composable
private fun AuthTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AccentOrange.copy(0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 14.sp,
            color = if (selected) AccentOrange else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ─── Create alert bottom sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAlertSheet(viewModel: AlertsViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.createState.collectAsState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("New price alert", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Skin search field ─────────────────────────────────────────────
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onCreateQueryChange,
                placeholder = { Text("Search a skin…", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                trailingIcon = {
                    if (state.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentOrange)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors(),
            )

            // ── Search results ────────────────────────────────────────────────
            if (state.results.isNotEmpty() && state.selectedSkin == null) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(state.results, key = { it.id }) { skin ->
                        SkinPickerRow(skin = skin, onClick = { viewModel.onCreateSkinSelected(skin) })
                        HorizontalDivider(color = DividerColor)
                    }
                }
            }

            // ── Selected skin card ────────────────────────────────────────────
            state.selectedSkin?.let { skin ->
                Spacer(Modifier.height(12.dp))
                SelectedSkinCard(skin)

                Spacer(Modifier.height(16.dp))

                // ── Type segmented picker ─────────────────────────────────────
                Text("Alert type", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SegmentedTypeButton(
                        selected = state.type == AlertType.BUY_BELOW,
                        label = "Drops below",
                        icon = Icons.Default.TrendingDown,
                        color = AccentGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onCreateTypeChange(AlertType.BUY_BELOW) },
                    )
                    SegmentedTypeButton(
                        selected = state.type == AlertType.SELL_ABOVE,
                        label = "Rises above",
                        icon = Icons.Default.TrendingUp,
                        color = AccentOrange,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onCreateTypeChange(AlertType.SELL_ABOVE) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.type.description,
                    fontSize = 11.sp,
                    color = TextTertiary,
                )

                Spacer(Modifier.height(16.dp))

                // ── Target price ──────────────────────────────────────────────
                Text(
                    "Target price (USD)",
                    fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.targetPrice,
                    onValueChange = viewModel::onCreateTargetPriceChange,
                    leadingIcon = { Text("$", color = TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("0.00", color = TextTertiary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )

                if (skin.lowestMarketPrice > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Lowest market price: ${Currency.format(skin.lowestMarketPrice)}",
                        fontSize = 11.sp, color = TextTertiary,
                    )
                }
            }

            // ── Error ─────────────────────────────────────────────────────────
            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(msg, color = AccentRed, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))

            // ── Submit button ─────────────────────────────────────────────────
            Button(
                onClick = {
                    scope.launch {
                        if (viewModel.submitCreateAlert()) {
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                },
                enabled = state.selectedSkin != null && state.targetPrice.isNotBlank() && !state.submitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange,
                    disabledContainerColor = SurfaceElevated,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp, color = Color.White,
                    )
                } else {
                    Text(
                        "Create alert",
                        fontWeight = FontWeight.Bold,
                        color = if (state.selectedSkin == null) TextTertiary else Color.White,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Edit alert bottom sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAlertSheet(viewModel: AlertsViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.editState.collectAsState()
    val scope = rememberCoroutineScope()
    val alert = state.alert ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Edit alert", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Skin preview (read-only) ───────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariant)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceElevated)) {
                    AsyncImage(
                        model = alert.skinImageUrl, contentDescription = null,
                        modifier = Modifier.size(44.dp), contentScale = ContentScale.Fit,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    alert.skinName, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Alert type ────────────────────────────────────────────────────
            Text("Alert type", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SegmentedTypeButton(
                    selected = state.type == AlertType.BUY_BELOW,
                    label = "Drops below",
                    icon = Icons.Default.TrendingDown,
                    color = AccentGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.onEditTypeChange(AlertType.BUY_BELOW) },
                )
                SegmentedTypeButton(
                    selected = state.type == AlertType.SELL_ABOVE,
                    label = "Rises above",
                    icon = Icons.Default.TrendingUp,
                    color = AccentOrange,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.onEditTypeChange(AlertType.SELL_ABOVE) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(text = state.type.description, fontSize = 11.sp, color = TextTertiary)

            Spacer(Modifier.height(16.dp))

            // ── Target price ──────────────────────────────────────────────────
            Text("Target price (USD)", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = state.targetPrice,
                onValueChange = viewModel::onEditTargetPriceChange,
                leadingIcon = { Text("$", color = TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                placeholder = { Text("0.00", color = TextTertiary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors(),
            )

            if (alert.currentPrice > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Current market price: ${Currency.format(alert.currentPrice)}",
                    fontSize = 11.sp, color = TextTertiary,
                )
            }

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(msg, color = AccentRed, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        if (viewModel.submitEdit()) {
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                },
                enabled = state.targetPrice.isNotBlank() && !state.submitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange,
                    disabledContainerColor = SurfaceElevated,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Save changes", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SkinPickerRow(skin: Skin, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = skin.imageUrl, contentDescription = null,
                modifier = Modifier.size(40.dp), contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                skin.name, fontSize = 13.sp, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                skin.wear.displayName, fontSize = 11.sp, color = TextTertiary,
            )
        }
        if (skin.lowestMarketPrice > 0) {
            val currency = Currency
            Text(
                currency.format(skin.lowestMarketPrice),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
            )
        }
    }
}

@Composable
private fun SelectedSkinCard(skin: Skin) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariant)
            .border(1.dp, AccentOrange.copy(0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceElevated)) {
            AsyncImage(
                model = skin.imageUrl, contentDescription = null,
                modifier = Modifier.size(44.dp), contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                skin.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(skin.wear.displayName, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SegmentedTypeButton(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) color.copy(0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            tint = if (selected) color else TextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label, fontSize = 13.sp,
            color = if (selected) color else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun textFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceVariant,
    unfocusedContainerColor = SurfaceVariant,
    focusedIndicatorColor = AccentOrange,
    unfocusedIndicatorColor = DividerColor,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentOrange,
)
