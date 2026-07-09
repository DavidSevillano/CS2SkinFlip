package com.burixer85.cs2skinflip.features.watchlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.domain.model.WatchlistItem
import com.burixer85.cs2skinflip.core.ui.LocalCurrency
import com.burixer85.cs2skinflip.core.ui.components.ErrorState
import com.burixer85.cs2skinflip.core.ui.components.PriceChangeChip
import com.burixer85.cs2skinflip.core.ui.components.SkinListSkeleton
import com.burixer85.cs2skinflip.core.ui.theme.AccentGreen
import com.burixer85.cs2skinflip.core.ui.theme.AccentRed
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.DividerColor
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceElevated
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onSkinClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text(stringResource(R.string.watchlist_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            windowInsets = WindowInsets(0.dp),
        )

        when (val state = uiState) {
            is WatchlistUiState.Loading -> SkinListSkeleton(Modifier.fillMaxSize())
            is WatchlistUiState.Error -> ErrorState(
                message = state.message,
                modifier = Modifier.fillMaxSize(),
            )
            is WatchlistUiState.Success -> {
                if (state.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.watchlist_empty),
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    pluralStringResource(R.plurals.watchlist_items_tracked, state.items.size, state.items.size),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(stringResource(R.string.watchlist_swipe_hint), fontSize = 11.sp, color = TextSecondary)
                            }
                        }

                        items(state.items, key = { it.id }) { item ->
                            WatchlistItemRow(
                                item = item,
                                onClick = { onSkinClick(item.skinId) },
                                onDelete = { viewModel.removeItem(item.id) },
                            )
                            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchlistItemRow(
    item: WatchlistItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
    )
    val currency = LocalCurrency.current

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    AccentRed.copy(alpha = 0.8f)
                else Color.Transparent,
                label = "swipe_bg",
            )
            Box(
                Modifier.fillMaxSize().background(color).padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = Color.White)
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Background)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated),
            ) {
                AsyncImage(
                    model = item.skinImageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.skinName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        currency.format(item.currentPrice.takeIf { it > 0 }),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    PriceChangeChip(change = item.priceChange24h)
                }
                item.targetBuyPrice?.let { target ->
                    val diff = (item.currentPrice - target) / target * 100
                    val color = if (diff <= 0) AccentGreen else TextSecondary
                    Text(
                        stringResource(
                            R.string.watchlist_target_diff,
                            currency.format(target),
                            "${if (diff <= 0) "" else "+"}${"%.1f".format(diff)}%",
                        ),
                        fontSize = 11.sp,
                        color = color,
                    )
                }
                item.targetSellPrice?.let { target ->
                    Text(
                        stringResource(R.string.watchlist_sell_target, currency.format(target)),
                        fontSize = 11.sp,
                        color = AccentGreen,
                    )
                }
            }
        }
    }
}
