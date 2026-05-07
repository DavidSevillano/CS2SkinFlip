package com.burixer85.cs2skinflip.features.skindetail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.burixer85.cs2skinflip.core.domain.model.Marketplace
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.ui.components.ErrorState
import com.burixer85.cs2skinflip.core.ui.components.PriceChangeChip
import com.burixer85.cs2skinflip.core.ui.components.RarityBadge
import com.burixer85.cs2skinflip.core.ui.components.SkeletonBox
import com.burixer85.cs2skinflip.core.ui.components.rarityColor
import com.burixer85.cs2skinflip.core.ui.theme.AccentBlue
import com.burixer85.cs2skinflip.core.ui.theme.AccentGreen
import com.burixer85.cs2skinflip.core.ui.theme.AccentOrange
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.DividerColor
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceElevated
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceVariant
import com.burixer85.cs2skinflip.core.ui.theme.TextPrimary
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinDetailScreen(
    skinId: String,
    onBack: () -> Unit,
    viewModel: SkinDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        when (val state = uiState) {
            is SkinDetailUiState.Loading -> {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
                )
                Column(Modifier.padding(16.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(200.dp))
                    Spacer(Modifier.height(16.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.6f).height(24.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.4f).height(16.dp))
                }
            }
            is SkinDetailUiState.Error -> {
                TopAppBar(
                    title = { Text("Error") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
                )
                ErrorState(message = state.message, modifier = Modifier.fillMaxSize())
            }
            is SkinDetailUiState.Success -> {
                SkinDetailContent(
                    skin = state.skin,
                    isInWatchlist = state.isInWatchlist,
                    onBack = onBack,
                    onToggleWatchlist = viewModel::toggleWatchlist
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkinDetailContent(
    skin: Skin,
    isInWatchlist: Boolean,
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val rarityColor = rarityColor(skin.rarity)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = skin.weapon,
                    fontSize = 16.sp,
                    color = TextSecondary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            },
            actions = {
                IconButton(onClick = onToggleWatchlist) {
                    Icon(
                        imageVector = if (isInWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isInWatchlist) "Remove from watchlist" else "Add to watchlist",
                        tint = if (isInWatchlist) AccentOrange else TextSecondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
        )

        // Skin image hero
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(rarityColor.copy(alpha = 0.12f), Background)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = skin.imageUrl,
                contentDescription = skin.name,
                modifier = Modifier.size(200.dp),
                contentScale = ContentScale.Fit
            )
        }

        // Skin title + rarity
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = skin.skinName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = skin.weapon,
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RarityBadge(rarity = skin.rarity)
                Text(
                    text = skin.wear.displayName,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                PriceChangeChip(change = skin.priceChange24h)
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DividerColor)

        // Marketplace prices
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Marketplace Prices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            val prices = listOf(
                Triple(Marketplace.STEAM, skin.steamPrice, AccentBlue),
                Triple(Marketplace.CSFLOAT, skin.csFloatPrice, AccentGreen),
                Triple(Marketplace.SKINPORT, skin.skinportPrice, AccentOrange),
                Triple(Marketplace.DMARKET, skin.dmarketPrice, Color(0xFF9C59FF))
            )

            prices.forEach { (marketplace, price, color) ->
                MarketplacePriceRow(
                    name = marketplace.displayName,
                    price = price,
                    color = color,
                    isLowest = price != null && price == prices.mapNotNull { it.second }.minOrNull()
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        HorizontalDivider(color = DividerColor)

        // Float info
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Float Range",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            FloatRangeIndicator(
                min = skin.floatMin,
                max = skin.floatMax,
                median = skin.floatMedian
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FloatStat("Min", "%.4f".format(skin.floatMin))
                FloatStat("Median", "%.4f".format(skin.floatMedian))
                FloatStat("Max", "%.4f".format(skin.floatMax))
            }
        }

        HorizontalDivider(color = DividerColor)

        // Price history (simple spark visualization)
        if (skin.priceHistory.isNotEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "30-Day Price History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                SimplePriceChart(pricePoints = skin.priceHistory.takeLast(30).map { it.price })
            }
        }

        // Watchlist button
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onToggleWatchlist,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isInWatchlist) SurfaceElevated else AccentOrange
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = if (isInWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isInWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MarketplacePriceRow(
    name: String,
    price: Double?,
    color: Color,
    isLowest: Boolean
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLowest) color.copy(alpha = 0.08f) else SurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
                Spacer(Modifier.width(10.dp))
                Text(text = name, fontSize = 14.sp, color = TextPrimary)
                if (isLowest) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "LOWEST",
                        fontSize = 10.sp,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (price != null) {
                Text(
                    text = "${"$%.2f".format(price)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isLowest) color else TextPrimary
                )
            } else {
                Text("N/A", fontSize = 14.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun FloatStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun FloatRangeIndicator(min: Float, max: Float, median: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceElevated)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(max)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(AccentGreen.copy(0.4f), AccentOrange.copy(0.8f))
                    )
                )
        )
    }
}

@Composable
private fun SimplePriceChart(pricePoints: List<Double>) {
    if (pricePoints.size < 2) return
    val min = pricePoints.minOrNull() ?: return
    val max = pricePoints.maxOrNull() ?: return
    val range = max - min

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        pricePoints.forEach { price ->
            val fraction = if (range > 0) ((price - min) / range).toFloat() else 0.5f
            val barHeight = (4 + fraction * 52).dp
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(AccentOrange.copy(alpha = 0.6f + fraction * 0.4f))
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("30d ago", fontSize = 10.sp, color = TextSecondary)
        Text("Today", fontSize = 10.sp, color = TextSecondary)
    }
}
