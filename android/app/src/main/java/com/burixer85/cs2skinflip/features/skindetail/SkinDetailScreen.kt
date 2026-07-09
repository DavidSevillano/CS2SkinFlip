package com.burixer85.cs2skinflip.features.skindetail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.domain.model.Marketplace
import com.burixer85.cs2skinflip.core.domain.model.PricePoint
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.ui.components.ErrorState
import com.burixer85.cs2skinflip.core.ui.components.PriceChangeChip
import com.burixer85.cs2skinflip.core.ui.components.RarityBadge
import com.burixer85.cs2skinflip.core.ui.components.SkeletonBox
import com.burixer85.cs2skinflip.core.ui.components.rarityColor
import com.burixer85.cs2skinflip.core.preferences.Currency
import com.burixer85.cs2skinflip.core.ui.theme.AccentBlue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.burixer85.cs2skinflip.core.ads.AdsViewModel
import com.burixer85.cs2skinflip.core.billing.findActivity
import com.burixer85.cs2skinflip.core.ui.theme.AccentGreen
import com.burixer85.cs2skinflip.core.ui.theme.AccentOrange
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.DividerColor
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceElevated
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceVariant
import com.burixer85.cs2skinflip.core.ui.theme.TextPrimary
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    viewModel: SkinDetailViewModel = hiltViewModel(),
    adsViewModel: AdsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(Unit) {
        adsViewModel.onSkinDetailEntered()
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { adsViewModel.maybeShowInterstitialOnExit(it) }
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        when (val state = uiState) {
            is SkinDetailUiState.Loading -> {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
                    windowInsets = WindowInsets(0.dp)
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
                    title = { Text(stringResource(R.string.skindetail_error_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
                    windowInsets = WindowInsets(0.dp)
                )
                ErrorState(message = state.message, onRetry = viewModel::retry, modifier = Modifier.fillMaxSize())
            }
            is SkinDetailUiState.Success -> {
                val selectedRange by viewModel.selectedRange.collectAsState()
                SkinDetailContent(
                    skin = state.skin,
                    isInWatchlist = state.isInWatchlist,
                    selectedRange = selectedRange,
                    onBack = onBack,
                    onToggleWatchlist = viewModel::toggleWatchlist,
                    onRangeSelected = viewModel::onRangeSelected
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
    selectedRange: PriceRange,
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onRangeSelected: (PriceRange) -> Unit
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
                    Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = TextPrimary)
                }
            },
            actions = {
                IconButton(onClick = onToggleWatchlist) {
                    Icon(
                        imageVector = if (isInWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(if (isInWatchlist) R.string.remove_from_watchlist else R.string.add_to_watchlist),
                        tint = if (isInWatchlist) AccentOrange else TextSecondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            windowInsets = WindowInsets(0.dp)
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
                text = stringResource(R.string.skindetail_marketplace_prices),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            val context = LocalContext.current
            val marketHashName = skin.marketHashName

            fun skinportUrl(name: String): String =
                Uri.parse("https://skinport.com/market/730")
                    .buildUpon()
                    .appendQueryParameter("search", name)
                    .appendQueryParameter("order", "asc")
                    .build().toString()

            fun csgoMarketUrl(name: String): String =
                "https://market.csgo.com/en/730/${Uri.encode(name)}"

            fun waxpeerUrl(name: String): String {
                val slug = name
                    .lowercase()
                    .replace(Regex("[★✦™]"), "")
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')
                return "https://waxpeer.com/$slug/item"
            }

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
                MarketEntry(Marketplace.SKINPORT,    skin.skinportPrice,   AccentOrange,         skinportUrl(marketHashName)),
                MarketEntry(Marketplace.CSGO_MARKET, skin.csgoMarketPrice, AccentGreen,          csgoMarketUrl(marketHashName)),
                MarketEntry(Marketplace.WAXPEER,     skin.waxpeerPrice,    Color(0xFF4A90E2),    waxpeerUrl(marketHashName)),
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
                text = stringResource(R.string.skindetail_float_range),
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.skindetail_price_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                PriceRangeSelector(selected = selectedRange, onSelect = onRangeSelected)
            }
            Spacer(Modifier.height(12.dp))
            if (skin.priceHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.skindetail_price_history_unavailable),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontStyle = FontStyle.Italic
                    )
                }
            } else {
                PriceLineChart(pricePoints = skin.priceHistory, range = selectedRange)
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
                text = stringResource(if (isInWatchlist) R.string.remove_from_watchlist else R.string.add_to_watchlist),
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
                        text = stringResource(R.string.skindetail_lowest_badge),
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
                        contentDescription = stringResource(R.string.cd_open_browser),
                        modifier = Modifier.size(14.dp),
                        tint = TextSecondary.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.skindetail_not_listed),
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
private fun PriceRangeSelector(
    selected: PriceRange,
    onSelect: (PriceRange) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        PriceRange.values().forEach { range ->
            val isSelected = range == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) AccentOrange else Color.Transparent)
                    .clickable { onSelect(range) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = range.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PriceLineChart(pricePoints: List<PricePoint>, range: PriceRange) {
    if (pricePoints.size < 2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.skindetail_not_enough_data),
                fontSize = 11.sp,
                color = TextSecondary,
                fontStyle = FontStyle.Italic
            )
        }
        return
    }

    val minPrice = pricePoints.minOf { it.price }
    val maxPrice = pricePoints.maxOf { it.price }
    val priceRange = maxPrice - minPrice
    val lineColor = AccentOrange
    val textMeasurer = rememberTextMeasurer()
    var selectedIndex by remember(pricePoints) { mutableStateOf<Int?>(null) }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .width(44.dp)
                .height(120.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(Currency.format(maxPrice), fontSize = 9.sp, color = TextSecondary, maxLines = 1)
            Text(Currency.format(minPrice), fontSize = 9.sp, color = TextSecondary, maxLines = 1)
        }
        Spacer(Modifier.width(4.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariant)
                .padding(8.dp)
                .pointerInput(pricePoints) {
                    while (true) {
                        awaitPointerEventScope {
                            val down = awaitFirstDown()
                            selectedIndex = nearestPointIndex(down.position.x, size.width, pricePoints.size)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null && change.pressed) {
                                    selectedIndex = nearestPointIndex(change.position.x, size.width, pricePoints.size)
                                    change.consume()
                                }
                            } while (change != null && change.pressed)
                        }
                    }
                }
        ) {
            val stepX = if (pricePoints.size > 1) size.width / (pricePoints.size - 1) else 0f
            val points = pricePoints.mapIndexed { index, point ->
                val fraction = if (priceRange > 0) ((point.price - minPrice) / priceRange).toFloat() else 0.5f
                Offset(x = index * stepX, y = size.height - fraction * size.height)
            }

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val areaPath = Path().apply {
                addPath(linePath)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0f))
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )

            val index = selectedIndex
            if (index != null) {
                val point = points[index]
                val dataPoint = pricePoints[index]

                drawLine(
                    color = lineColor.copy(alpha = 0.4f),
                    start = Offset(point.x, 0f),
                    end = Offset(point.x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = point)

                val tooltipText = "${Currency.format(dataPoint.price)}\n${formatTooltipTimestamp(dataPoint.timestamp)}"
                val layoutResult = textMeasurer.measure(
                    text = tooltipText,
                    style = TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                )
                val boxPadding = 6.dp.toPx()
                val boxWidth = layoutResult.size.width + boxPadding * 2
                val boxHeight = layoutResult.size.height + boxPadding * 2
                val boxLeft = (point.x - boxWidth / 2).coerceIn(0f, (size.width - boxWidth).coerceAtLeast(0f))
                val boxTop = (point.y - boxHeight - 12.dp.toPx()).coerceAtLeast(0f)

                drawRoundRect(
                    color = Color(0xFF1A1A1A),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawText(
                    textLayoutResult = layoutResult,
                    topLeft = Offset(boxLeft + boxPadding, boxTop + boxPadding)
                )
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, start = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val labelPoints = listOf(
            pricePoints.first(),
            pricePoints[pricePoints.size / 2],
            pricePoints.last()
        )
        labelPoints.forEach { point ->
            Text(formatAxisLabel(point.timestamp, range), fontSize = 10.sp, color = TextSecondary)
        }
    }
}

private fun nearestPointIndex(touchX: Float, canvasWidthPx: Int, pointCount: Int): Int {
    if (pointCount <= 1) return 0
    val stepX = canvasWidthPx / (pointCount - 1).toFloat()
    return (touchX / stepX).roundToInt().coerceIn(0, pointCount - 1)
}

private fun formatAxisLabel(timestampMillis: Long, range: PriceRange): String {
    val pattern = when (range) {
        PriceRange.DAY -> "h a"
        PriceRange.WEEK -> "EEE"
        PriceRange.MONTH -> "MMM d"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestampMillis))
}

private fun formatTooltipTimestamp(timestampMillis: Long): String {
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestampMillis))
}
