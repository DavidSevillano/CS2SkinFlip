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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSkinClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    adsViewModel: AdsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing = uiState is HomeUiState.Loading
    val isPremium by adsViewModel.isPremium.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        HomeHeader()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::loadTrending,
            modifier = Modifier.weight(1f)
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> SkinListSkeleton(Modifier.fillMaxSize())
                is HomeUiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = viewModel::loadTrending,
                    modifier = Modifier.fillMaxSize()
                )
                is HomeUiState.Success -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            TrendingHeader()
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
        if (!isPremium) {
            BannerAdView(adUnitId = adsViewModel.bannerAdUnitId)
        }
    }
}

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
private fun TrendingHeader() {
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
                    imageVector = Icons.Default.TrendingUp,
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
