package com.burixer85.cs2skinflip.features.portfolio

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.burixer85.cs2skinflip.core.domain.model.Portfolio
import com.burixer85.cs2skinflip.core.domain.model.PortfolioItem
import com.burixer85.cs2skinflip.core.ui.components.ErrorState
import com.burixer85.cs2skinflip.core.ui.components.RarityBadge
import com.burixer85.cs2skinflip.core.ui.components.SkinListSkeleton
import com.burixer85.cs2skinflip.core.ui.components.rarityColor
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

@Composable
fun PortfolioScreen(
    onSkinClick: (String) -> Unit,
    onWatchlistClick: () -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        // Header
        Box(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Portfolio", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentOrange.copy(0.12f))
                        .clickable(onClick = onWatchlistClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Bookmarks,
                        contentDescription = "Watchlist",
                        tint = AccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Watchlist", color = AccentOrange, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        when (val state = uiState) {
            is PortfolioUiState.NotConnected -> ConnectSteamView(onConnect = viewModel::connectSteam)
            is PortfolioUiState.Connecting -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentOrange)
                        Spacer(Modifier.height(12.dp))
                        Text("Connecting to Steam…", color = TextSecondary)
                    }
                }
            }
            is PortfolioUiState.Loading -> SkinListSkeleton(Modifier.fillMaxSize())
            is PortfolioUiState.Error -> ErrorState(
                message = state.message,
                onRetry = viewModel::connectSteam,
                modifier = Modifier.fillMaxSize()
            )
            is PortfolioUiState.Success -> PortfolioContent(
                portfolio = state.portfolio,
                onSkinClick = onSkinClick
            )
        }
    }
}

@Composable
private fun ConnectSteamView(onConnect: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = TextSecondary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Connect your Steam Account",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "View your CS2 inventory and track the value of your skins in real time.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2838)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    "Sign in with Steam",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun PortfolioContent(portfolio: Portfolio, onSkinClick: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            PortfolioHeader(portfolio)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Items (${portfolio.items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Sorted by value", fontSize = 12.sp, color = TextSecondary)
            }
        }

        items(
            portfolio.items.sortedByDescending { it.currentPrice },
            key = { it.assetId }
        ) { item ->
            PortfolioItemRow(
                item = item,
                onClick = { onSkinClick(item.skinId) }
            )
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PortfolioHeader(portfolio: Portfolio) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(AccentOrange.copy(0.15f), SurfaceVariant)
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = portfolio.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(portfolio.username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Steam ID: ${portfolio.steamId.takeLast(8)}", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "${"$%.2f".format(portfolio.totalValue)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
                Text("Total inventory value", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatChip("${portfolio.items.size} items", AccentOrange)
                    val profit = portfolio.items.sumOf { it.profitLoss ?: 0.0 }
                    val profitColor = if (profit >= 0) AccentGreen else AccentRed
                    StatChip(
                        "${if (profit >= 0) "+" else ""}${"$%.2f".format(profit)} P/L",
                        profitColor
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PortfolioItemRow(item: PortfolioItem, onClick: () -> Unit) {
    val rarityColor = rarityColor(item.rarity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceElevated)
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RarityBadge(item.rarity)
                item.float?.let {
                    Text("Float: ${"%.4f".format(it)}", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${"$%.2f".format(item.currentPrice)}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            item.profitLoss?.let { pl ->
                val color = if (pl >= 0) AccentGreen else AccentRed
                Text(
                    text = "${if (pl >= 0) "+" else ""}${"$%.2f".format(pl)}",
                    fontSize = 11.sp,
                    color = color
                )
            }
        }
    }
}
