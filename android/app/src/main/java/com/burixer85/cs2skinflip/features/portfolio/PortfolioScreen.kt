package com.burixer85.cs2skinflip.features.portfolio

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.domain.model.PortfolioItem
import com.burixer85.cs2skinflip.core.domain.model.PortfolioSummary
import com.burixer85.cs2skinflip.core.preferences.Currency
import com.burixer85.cs2skinflip.core.ui.components.ErrorState
import com.burixer85.cs2skinflip.core.ui.components.SkinListSkeleton
import com.burixer85.cs2skinflip.core.ui.theme.AccentGreen
import com.burixer85.cs2skinflip.core.ui.theme.AccentOrange
import com.burixer85.cs2skinflip.core.ui.theme.AccentRed
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.DividerColor
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceElevated
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceVariant
import com.burixer85.cs2skinflip.core.ui.theme.TextPrimary
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary
import com.burixer85.cs2skinflip.core.ui.theme.TextTertiary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onSkinClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val editState by viewModel.editState.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_portfolio)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            windowInsets = WindowInsets(0.dp),
        )

        when (val state = uiState) {
            is PortfolioUiState.Loading -> SkinListSkeleton(Modifier.fillMaxSize())
            is PortfolioUiState.Error -> ErrorState(stringResource(state.messageRes), modifier = Modifier.fillMaxSize())
            is PortfolioUiState.NotLoggedIn -> NotLoggedInView()
            is PortfolioUiState.Success -> PortfolioBody(
                state = state,
                onSync = viewModel::sync,
                onSkinClick = { onSkinClick(it.skinId) },
                onEdit = { viewModel.startEdit(it) },
                onDelete = { viewModel.deleteItem(it.id) },
            )
        }
    }

    if (editState.item != null) {
        EditPriceDialog(viewModel = viewModel, onDismiss = { viewModel.cancelEdit() })
    }
}

@Composable
private fun PortfolioBody(
    state: PortfolioUiState.Success,
    onSync: () -> Unit,
    onSkinClick: (PortfolioItem) -> Unit,
    onEdit: (PortfolioItem) -> Unit,
    onDelete: (PortfolioItem) -> Unit,
) {
    if (state.items.isEmpty()) {
        EmptyPortfolioView(syncing = state.syncing, syncErrorRes = state.syncErrorRes, onSync = onSync)
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SummaryCard(state.summary)
            Spacer(Modifier.height(8.dp))
            SyncRow(syncing = state.syncing, syncErrorRes = state.syncErrorRes, onSync = onSync)
        }
        items(state.items, key = { it.id }) { item ->
            PortfolioItemRow(
                item = item,
                onClick = { onSkinClick(item) },
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) },
            )
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SummaryCard(summary: PortfolioSummary) {
    val plColor = if (summary.totalProfitLoss >= 0) AccentGreen else AccentRed
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.portfolio_total_value), fontSize = 12.sp, color = TextSecondary)
                    Text(
                        Currency.format(summary.totalValue),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.portfolio_pl_label), fontSize = 12.sp, color = TextSecondary)
                    val sign = if (summary.totalProfitLoss >= 0) "+" else ""
                    Text(
                        "$sign${Currency.format(summary.totalProfitLoss)} ($sign${"%.1f".format(summary.totalProfitLossPct)}%)",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = plColor,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.portfolio_invested, Currency.format(summary.totalInvested)) +
                    " · " + pluralStringResource(R.plurals.portfolio_items_count, summary.itemCount, summary.itemCount),
                fontSize = 12.sp, color = TextTertiary,
            )
        }
    }
}

@Composable
private fun SyncRow(syncing: Boolean, @StringRes syncErrorRes: Int?, onSync: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onSync, enabled = !syncing) {
                if (syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentOrange)
                } else {
                    Icon(Icons.Default.Refresh, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.portfolio_sync_steam), color = AccentOrange, fontSize = 13.sp)
            }
        }
        syncErrorRes?.let {
            Text(
                stringResource(it), color = AccentRed, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyPortfolioView(syncing: Boolean, @StringRes syncErrorRes: Int?, onSync: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(80.dp).clip(CircleShape).background(AccentOrange.copy(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Bookmarks, null, tint = AccentOrange, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.portfolio_empty_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.portfolio_empty_subtitle),
                color = TextSecondary, textAlign = TextAlign.Center, fontSize = 14.sp,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSync,
                enabled = !syncing,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp),
            ) {
                if (syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.portfolio_sync_steam), fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            syncErrorRes?.let {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(it), color = AccentRed, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PortfolioItemRow(
    item: PortfolioItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceElevated),
        ) {
            AsyncImage(
                model = item.skinImageUrl, contentDescription = null,
                modifier = Modifier.size(48.dp), contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
            Text(
                item.skinName, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(R.string.portfolio_paid, Currency.format(item.acquirePrice)),
                fontSize = 11.sp, color = TextTertiary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Currency.format(item.currentPrice),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            )
            val plColor = if (item.profitLoss >= 0) AccentGreen else AccentRed
            val sign = if (item.profitLoss >= 0) "+" else ""
            Text(
                "$sign${"%.1f".format(item.profitLossPct)}%",
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = plColor,
            )
        }
        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, "Delete", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Surface,
            title = { Text(stringResource(R.string.portfolio_remove_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.portfolio_remove_body, item.skinName),
                    color = TextSecondary, fontSize = 14.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.action_remove), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun NotLoggedInView() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Bookmarks, null, tint = AccentOrange, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.portfolio_signin_title),
                fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.portfolio_signin_body),
                color = TextSecondary, textAlign = TextAlign.Center, fontSize = 13.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPriceDialog(viewModel: PortfolioViewModel, onDismiss: () -> Unit) {
    val state by viewModel.editState.collectAsState()
    val item = state.item ?: return
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(stringResource(R.string.portfolio_edit_price_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column {
                Text(item.skinName, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.price,
                    onValueChange = viewModel::onEditPriceChange,
                    leadingIcon = { Text("$", color = TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    placeholder = { Text(stringResource(R.string.price_placeholder), color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceVariant,
                        unfocusedContainerColor = SurfaceVariant,
                        focusedIndicatorColor = AccentOrange,
                        unfocusedIndicatorColor = DividerColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentOrange,
                    ),
                )
                state.errorMessageRes?.let { msgRes ->
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(msgRes), color = AccentRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { scope.launch { if (viewModel.submitEditPrice()) onDismiss() } },
                enabled = state.price.isNotBlank() && !state.submitting,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(stringResource(R.string.action_save), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
        },
    )
}
