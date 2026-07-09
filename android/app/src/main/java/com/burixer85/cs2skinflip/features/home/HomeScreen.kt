package com.burixer85.cs2skinflip.features.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.ui.components.ErrorState
import com.burixer85.cs2skinflip.core.ui.components.SkinCardCompact
import com.burixer85.cs2skinflip.core.ui.components.SkinListSkeleton
import com.burixer85.cs2skinflip.core.ui.theme.AccentOrange
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.DividerColor
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary
import com.burixer85.cs2skinflip.core.ads.AdsViewModel
import com.burixer85.cs2skinflip.core.ads.BannerAdView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSkinClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    adsViewModel: AdsViewModel = hiltViewModel(),
) {
    val direction by viewModel.direction.collectAsState()
    val isPremium by adsViewModel.isPremium.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // Swipe -> tell the ViewModel which direction is now showing (idempotent if unchanged).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.selectDirection(pageToDirection(page))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        HomeHeader()

        DirectionTabRow(
            selected = direction,
            onSelect = { newDirection ->
                viewModel.selectDirection(newDirection)
                coroutineScope.launch {
                    pagerState.animateScrollToPage(directionToPage(newDirection))
                }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val pageDirection = pageToDirection(page)
            val uiState by (if (pageDirection == TopMoversDirection.FALLING) viewModel.fallingState else viewModel.risingState)
                .collectAsState()
            val isRefreshing = uiState is HomeUiState.Loading

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { if (pageDirection == direction) viewModel.loadTrending() },
                modifier = Modifier.fillMaxSize()
            ) {
                when (val state = uiState) {
                    is HomeUiState.Loading -> SkinListSkeleton(Modifier.fillMaxSize())
                    is HomeUiState.Error -> ErrorState(
                        message = stringResource(state.messageRes),
                        onRetry = { if (pageDirection == direction) viewModel.loadTrending() },
                        modifier = Modifier.fillMaxSize()
                    )
                    is HomeUiState.Success -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                TrendingHeader(direction = pageDirection)
                            }
                            itemsIndexed(state.trendingSkins) { index, skin ->
                                SkinCardCompact(
                                    rank = index + 1,
                                    skin = skin,
                                    onClick = { onSkinClick(skin.id) }
                                )
                                if (index < state.trendingSkins.lastIndex) {
                                    HorizontalDivider(
                                        color = DividerColor,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
        if (!isPremium) {
            BannerAdView(adUnitId = adsViewModel.bannerAdUnitId)
        }
    }
}

private fun pageToDirection(page: Int): TopMoversDirection =
    if (page == 1) TopMoversDirection.FALLING else TopMoversDirection.RISING

private fun directionToPage(direction: TopMoversDirection): Int =
    if (direction == TopMoversDirection.FALLING) 1 else 0

@Composable
private fun HomeHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AccentOrange
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun TrendingHeader(direction: TopMoversDirection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (direction == TopMoversDirection.FALLING) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = stringResource(R.string.home_top_movers_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = stringResource(R.string.home_vol_by_movement),
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun DirectionTabRow(selected: TopMoversDirection, onSelect: (TopMoversDirection) -> Unit) {
    val selectedIndex = directionToPage(selected)
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Surface,
        contentColor = AccentOrange
    ) {
        Tab(
            selected = selectedIndex == 0,
            onClick = { onSelect(TopMoversDirection.RISING) },
            text = { Text(stringResource(R.string.home_tab_rising)) }
        )
        Tab(
            selected = selectedIndex == 1,
            onClick = { onSelect(TopMoversDirection.FALLING) },
            text = { Text(stringResource(R.string.home_tab_falling)) }
        )
    }
}
