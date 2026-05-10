package com.burixer85.cs2skinflip.features.skindetail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.OpenInNew
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
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
import com.burixer85.cs2skinflip.core.preferences.Currency
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

// Wear-condition float boundaries (CS2 standard values)
private data class WearSegment(val start: Float, val end: Float, val label: String, val color: Color)
private val wearSegments = listOf(
    WearSegment(0.00f, 0.07f, "FN", Color(0xFF4CAF50)),
    WearSegment(0.07f, 0.15f, "MW", Color(0xFF8BC34A)),
    WearSegment(0.15f, 0.38f, "FT", Color(0xFFFFC107)),
    WearSegment(0.38f, 0.45f, "WW", Color(0xFFFF9800)),
    WearSegment(0.45f, 1.00f, "BS", Color(0xFFF44336)),
)

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
                PriceChangeChip(change = skin.priceChange24h ?: 0.0)
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

            val context = LocalContext.current
            val marketHashName = skin.name  // already includes wear in name from backend

            // CS2Cap: item page via their search
            fun cs2capUrl(name: String): String =
                "https://cs2cap.com/en-US/item/${Uri.encode(name)}"

            // CSFloat: market search sorted by lowest price
            fun csfloatUrl(name: String): String =
                Uri.parse("https://csfloat.com/market")
                    .buildUpon()
                    .appendQueryParameter("search", name)
                    .appendQueryParameter("sort", "lowest_price")
                    .appendQueryParameter("type", "buy_now")
                    .build().toString()

            // CS:GO Market: item URL
            fun csgoMarketUrl(name: String): String =
                "https://market.csgo.com/en/730/${Uri.encode(name)}"

            fun openUrl(url: String) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }

            data class MarketEntry(
                val marketplace: Marketplace,
                val price: Double?,
                val color: Color,
                val url: String,
            )

            val entries = listOf(
                MarketEntry(Marketplace.CS2CAP,      skin.cs2capPrice,     AccentOrange,         cs2capUrl(marketHashName)),
                MarketEntry(Marketplace.CSFLOAT,     skin.csfloatPrice,    Color(0xFF9C59FF),    csfloatUrl(marketHashName)),
                MarketEntry(Marketplace.CSGO_MARKET, skin.csgoMarketPrice, AccentGreen,          csgoMarketUrl(marketHashName)),
            )
            val lowestPrice = entries.mapNotNull { it.price }.minOrNull()

            entries.forEach { entry ->
                MarketplacePriceRow(
                    name = entry.marketplace.displayName,
                    displayPrice = entry.price,
                    color = entry.color,
                    isLowest = entry.price != null && entry.price == lowestPrice,
                    onClick = if (entry.price != null) ({ openUrl(entry.url) }) else null
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

        // Price history (always shown; empty-state message when no data yet)
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "30-Day Price History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            if (skin.priceHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Price history not yet available",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontStyle = FontStyle.Italic
                    )
                }
            } else {
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
    displayPrice: Double?,
    color: Color,
    isLowest: Boolean,
    onClick: (() -> Unit)? = null
) {
    val isListed = displayPrice != null
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isLowest  -> color.copy(alpha = 0.08f)
                !isListed -> SurfaceVariant.copy(alpha = 0.5f)
                else      -> SurfaceVariant
            }
        ),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
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
                        .background(if (isListed) color else TextSecondary.copy(alpha = 0.4f))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = name,
                    fontSize = 14.sp,
                    color = if (isListed) TextPrimary else TextSecondary
                )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isListed) {
                    Text(
                        text = Currency.format(displayPrice),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isLowest) color else TextPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "Open in browser",
                        modifier = Modifier.size(14.dp),
                        tint = TextSecondary.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = "Not listed",
                        fontSize = 12.sp,
                        color = TextSecondary.copy(alpha = 0.6f),
                        fontStyle = FontStyle.Italic
                    )
                }
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
    val activeSeg = wearSegments.firstOrNull { median >= it.start && median <= it.end }

    Column {
        // Segmented bar across the full 0–1 float scale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val barWidth = maxWidth

            // Coloured segments — active one is full opacity, others dimmed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                wearSegments.forEach { seg ->
                    Box(
                        modifier = Modifier
                            .weight(seg.end - seg.start)
                            .fillMaxHeight()
                            .background(seg.color.copy(alpha = if (seg == activeSeg) 0.85f else 0.18f))
                    )
                }
            }

            // White marker line at the median float position
            Box(
                modifier = Modifier
                    .offset(x = barWidth * median - 2.dp)
                    .width(4.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }

        Spacer(Modifier.height(4.dp))

        // Wear abbreviation labels under each segment
        Row(Modifier.fillMaxWidth()) {
            wearSegments.forEach { seg ->
                Box(
                    modifier = Modifier.weight(seg.end - seg.start),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = seg.label,
                        fontSize = 9.sp,
                        color = if (seg == activeSeg) seg.color
                                else TextSecondary.copy(alpha = 0.35f),
                        fontWeight = if (seg == activeSeg) FontWeight.Bold
                                     else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimplePriceChart(pricePoints: List<Double>) {
    if (pricePoints.size < 2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Not enough data points",
                fontSize = 11.sp,
                color = TextSecondary,
                fontStyle = FontStyle.Italic
            )
        }
        return
    }
    val minPrice = pricePoints.minOrNull() ?: 0.0
    val maxPrice = pricePoints.maxOrNull() ?: 0.0
    val range = maxPrice - minPrice

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        // Y-axis labels: max price at top, min price at bottom
        Column(
            modifier = Modifier
                .width(44.dp)
                .height(64.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(Currency.format(maxPrice), fontSize = 9.sp, color = TextSecondary, maxLines = 1)
            Text(Currency.format(minPrice), fontSize = 9.sp, color = TextSecondary, maxLines = 1)
        }
        Spacer(Modifier.width(4.dp))
        // Bar chart
        Row(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            pricePoints.forEach { price ->
                val fraction = if (range > 0) ((price - minPrice) / range).toFloat() else 0.5f
                val barHeight = (4 + fraction * 56).dp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(AccentOrange.copy(alpha = 0.6f + fraction * 0.4f))
                )
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, start = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("30d ago", fontSize = 10.sp, color = TextSecondary)
        Text("Today", fontSize = 10.sp, color = TextSecondary)
    }
}
