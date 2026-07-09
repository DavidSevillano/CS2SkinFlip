package com.burixer85.cs2skinflip.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import com.burixer85.cs2skinflip.core.ui.components.ErrorState
import com.burixer85.cs2skinflip.core.ui.components.SkinCard
import com.burixer85.cs2skinflip.core.ui.components.SkinListSkeleton
import com.burixer85.cs2skinflip.core.ui.theme.AccentGreen
import com.burixer85.cs2skinflip.core.ui.theme.AccentOrange
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.DividerColor
import com.burixer85.cs2skinflip.core.ui.theme.RarityClassified
import com.burixer85.cs2skinflip.core.ui.theme.RarityConsumer
import com.burixer85.cs2skinflip.core.ui.theme.RarityContraband
import com.burixer85.cs2skinflip.core.ui.theme.RarityCovert
import com.burixer85.cs2skinflip.core.ui.theme.RarityIndustrial
import com.burixer85.cs2skinflip.core.ui.theme.RarityKnife
import com.burixer85.cs2skinflip.core.ui.theme.RarityMilSpec
import com.burixer85.cs2skinflip.core.ui.theme.RarityRestricted
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceElevated
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceVariant
import com.burixer85.cs2skinflip.core.ui.theme.TextPrimary
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary
import com.burixer85.cs2skinflip.core.ui.theme.TextTertiary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import com.burixer85.cs2skinflip.core.ads.AdsViewModel
import com.burixer85.cs2skinflip.core.ads.BannerAdView
import androidx.compose.runtime.snapshotFlow

private val MAX_PRICE = 25000f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSkinClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    adsViewModel: AdsViewModel = hiltViewModel(),
) {
    val isPremium by adsViewModel.isPremium.collectAsState()
    val query by viewModel.query.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val weapons by viewModel.availableWeapons.collectAsState()
    val skinSuggestions by viewModel.suggestions.collectAsState()
    val suggestionsLoading by viewModel.suggestionsLoading.collectAsState()

    var searchActive by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // ── Search bar ────────────────────────────────────────────────────────
        // windowInsets = 0 prevents double status-bar padding (Scaffold already handles it)
        val suggestions by remember(query, weapons) {
            derivedStateOf {
                if (query.isBlank()) emptyList()
                else weapons.filter { it.contains(query, ignoreCase = true) }.take(7)
            }
        }

        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = { searchActive = false },
                    expanded = searchActive,
                    onExpandedChange = { searchActive = it },
                    placeholder = { Text(stringResource(R.string.search_hint), color = TextSecondary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear), tint = TextSecondary)
                            }
                        }
                    },
                )
            },
            expanded = searchActive,
            onExpandedChange = { searchActive = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = SearchBarDefaults.colors(
                containerColor = Surface,
                dividerColor = DividerColor,
            ),
            windowInsets = WindowInsets(0.dp),
        ) {
            // ── Autocomplete suggestions ──────────────────────────────────────
            if (query.isNotBlank()) {
                // "Search for [query]" — text search
                SuggestionRow(
                    icon = {
                        if (suggestionsLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AccentOrange,
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null,
                                modifier = Modifier.size(18.dp), tint = TextSecondary)
                        }
                    },
                    primary = "Search for \"$query\"",
                    secondary = "in all skins",
                    onClick = { searchActive = false },
                )

                // Direct skin matches (debounced backend lookup) — tap goes to skin detail.
                if (skinSuggestions.isNotEmpty()) {
                    Divider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    skinSuggestions.forEach { skin ->
                        SkinSuggestionRow(skin = skin) {
                            viewModel.clearSuggestions()
                            searchActive = false
                            onSkinClick(skin.id)
                        }
                    }
                }

                if (suggestions.isNotEmpty()) {
                    Divider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            // Weapon filter suggestions
            suggestions.forEach { weapon ->
                SuggestionRow(
                    icon = {
                        Icon(Icons.Outlined.Tune, contentDescription = null,
                            modifier = Modifier.size(18.dp), tint = AccentOrange)
                    },
                    primary = weapon,
                    secondary = "filter by weapon",
                    onClick = {
                        viewModel.onQueryChange("")
                        viewModel.onFiltersChange(filters.copy(weapon = weapon))
                        searchActive = false
                    },
                )
            }
            // If no text entered, show popular weapon shortcuts
            if (query.isBlank()) {
                Text(
                    stringResource(R.string.search_popular_weapons),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                listOf("AK-47", "AWP", "M4A4", "Glock-18", "USP-S", "Karambit").forEach { w ->
                    SuggestionRow(
                        icon = {
                            Icon(Icons.Outlined.Tune, contentDescription = null,
                                modifier = Modifier.size(18.dp), tint = AccentOrange)
                        },
                        primary = w,
                        secondary = "filter by weapon",
                        onClick = {
                            viewModel.onFiltersChange(filters.copy(weapon = w))
                            searchActive = false
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Filter / Sort bar ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Filter button with active-count badge
            BadgedBox(
                badge = {
                    if (filters.activeCount > 0) {
                        Badge(
                            containerColor = AccentOrange,
                            contentColor = Color.White,
                        ) { Text(filters.activeCount.toString()) }
                    }
                },
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (filters.activeCount > 0) AccentOrange.copy(alpha = 0.15f) else SurfaceVariant)
                        .clickable { showFilterSheet = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = stringResource(R.string.search_filters),
                        modifier = Modifier.size(16.dp),
                        tint = if (filters.activeCount > 0) AccentOrange else TextSecondary,
                    )
                    Text(
                        stringResource(R.string.search_filters),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (filters.activeCount > 0) AccentOrange else TextSecondary,
                    )
                }
            }

            // Sort chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(end = 4.dp),
            ) {
                items(SortBy.entries) { sort ->
                    val selected = filters.sortBy == sort
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) AccentOrange.copy(alpha = 0.15f) else SurfaceVariant)
                            .border(
                                width = 1.dp,
                                color = if (selected) AccentOrange.copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable { viewModel.onFiltersChange(filters.copy(sortBy = sort)) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Outlined.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = AccentOrange,
                            )
                        }
                        Text(
                            stringResource(sort.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) AccentOrange else TextSecondary,
                        )
                    }
                }
            }
        }

        // ── Active filter chips (dismissable) ─────────────────────────────────
        val activeChips = buildActiveChips(filters)
        if (activeChips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                activeChips.forEach { chip ->
                    ActiveFilterChip(label = chip.label, color = chip.color) {
                        viewModel.onFiltersChange(chip.onRemove(filters))
                    }
                }
                // Clear all
                Text(
                    stringResource(R.string.action_clear_all),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentOrange,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { viewModel.clearFilters() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        // ── Results ───────────────────────────────────────────────────────────
        Box(Modifier.weight(1f)) {
        when (val state = uiState) {
            is SearchUiState.Loading -> SkinListSkeleton(Modifier.fillMaxSize())
            is SearchUiState.Error -> ErrorState(message = stringResource(state.messageRes), modifier = Modifier.fillMaxSize())
            is SearchUiState.Success -> {
                if (state.results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.no_results), color = TextSecondary)
                            if (filters.activeCount > 0) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.clearFilters() }) {
                                    Text(stringResource(R.string.search_clear_filters), color = AccentOrange)
                                }
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            val info = listState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
                        }
                            .distinctUntilChanged()
                            .filter { it }
                            .collect { viewModel.loadMore() }
                    }

                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                pluralStringResource(R.plurals.search_results_count, state.totalCount, state.totalCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        items(state.results, key = { it.id }) { skin ->
                            SkinCard(
                                skin = skin,
                                onClick = { onSkinClick(skin.id) },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = AccentOrange,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
        }
        if (!isPremium && !showFilterSheet) {
            BannerAdView(adUnitId = adsViewModel.bannerAdUnitId)
        }
    }

    // ── Filter bottom sheet ───────────────────────────────────────────────────
    if (showFilterSheet) {
        FilterBottomSheet(
            filters = filters,
            weapons = weapons,
            onApply = { newFilters ->
                viewModel.onFiltersChange(newFilters)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
    }
}

// ─── Filter bottom sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    filters: SearchFilters,
    weapons: List<String>,
    onApply: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Local draft state — only applied on "Apply"
    var draft by remember { mutableStateOf(filters) }
    var priceRange by remember {
        mutableStateOf((filters.minPrice?.toFloat() ?: 0f)..(filters.maxPrice?.toFloat() ?: MAX_PRICE))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.search_filters),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                TextButton(onClick = {
                    draft = SearchFilters(sortBy = draft.sortBy)
                    priceRange = 0f..MAX_PRICE
                }) {
                    Text(stringResource(R.string.action_clear_all), color = AccentOrange)
                }
            }

            Spacer(Modifier.height(4.dp))
            Divider(color = DividerColor)
            Spacer(Modifier.height(16.dp))

            // ── Wear ─────────────────────────────────────────────────────────
            FilterSectionLabel(stringResource(R.string.search_filter_wear))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WearChip(label = stringResource(R.string.search_wear_all), selected = draft.wear == null) {
                    draft = draft.copy(wear = null)
                }
                SkinWear.entries.forEach { wear ->
                    WearChip(
                        label = "${wear.abbrev}  ${stringResource(wear.displayNameRes)}",
                        selected = draft.wear == wear,
                    ) { draft = draft.copy(wear = if (draft.wear == wear) null else wear) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Divider(color = DividerColor)
            Spacer(Modifier.height(16.dp))

            // ── Rarity ───────────────────────────────────────────────────────
            FilterSectionLabel(stringResource(R.string.search_filter_rarity))
            Spacer(Modifier.height(8.dp))
            // Two rows of rarity chips
            val rarities = SkinRarity.entries
            val firstRow = rarities.take(4)
            val secondRow = rarities.drop(4)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(firstRow, secondRow).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { rarity ->
                            RarityChip(
                                rarity = rarity,
                                selected = draft.rarity == rarity,
                            ) { draft = draft.copy(rarity = if (draft.rarity == rarity) null else rarity) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Divider(color = DividerColor)
            Spacer(Modifier.height(16.dp))

            // ── Price range ───────────────────────────────────────────────────
            FilterSectionLabel(stringResource(R.string.search_filter_price_range))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val lo = priceRange.start
                val hi = priceRange.endInclusive
                Text(
                    if (lo < 1f) stringResource(R.string.search_price_any) else "$${lo.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    if (hi >= MAX_PRICE) stringResource(R.string.search_price_any) else "$${hi.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            RangeSlider(
                value = priceRange,
                onValueChange = { priceRange = it },
                valueRange = 0f..MAX_PRICE,
                steps = 0,
                colors = SliderDefaults.colors(
                    thumbColor = AccentOrange,
                    activeTrackColor = AccentOrange,
                    inactiveTrackColor = SurfaceElevated,
                ),
            )
            // Quick preset buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    stringResource(R.string.search_price_preset_under_10) to 0f..10f,
                    stringResource(R.string.search_price_preset_10_50) to 10f..50f,
                    stringResource(R.string.search_price_preset_50_200) to 50f..200f,
                    stringResource(R.string.search_price_preset_over_200) to 200f..MAX_PRICE,
                )
                    .forEach { (label, range) ->
                        val active = priceRange == range
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) AccentOrange else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) AccentOrange.copy(0.15f) else SurfaceVariant)
                                .clickable { priceRange = range }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
            }

            Spacer(Modifier.height(20.dp))
            Divider(color = DividerColor)
            Spacer(Modifier.height(16.dp))

            // ── Options ───────────────────────────────────────────────────────
            FilterSectionLabel(stringResource(R.string.search_filter_options))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.search_stattrak_title), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text(stringResource(R.string.search_stattrak_subtitle), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = draft.statTrakOnly,
                    onCheckedChange = { draft = draft.copy(statTrakOnly = it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentOrange,
                        uncheckedTrackColor = SurfaceElevated,
                    ),
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Apply button ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentOrange)
                    .clickable {
                        val minP = if (priceRange.start < 1f) null else priceRange.start.toDouble()
                        val maxP = if (priceRange.endInclusive >= MAX_PRICE) null else priceRange.endInclusive.toDouble()
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onApply(draft.copy(minPrice = minP, maxPrice = maxP))
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.search_apply_filters),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Small composables ────────────────────────────────────────────────────────

@Composable
private fun FilterSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
}

@Composable
private fun WearChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentOrange.copy(0.15f) else SurfaceVariant)
            .border(1.dp, if (selected) AccentOrange.copy(0.6f) else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) AccentOrange else TextSecondary,
        )
    }
}

@Composable
private fun RarityChip(rarity: SkinRarity, selected: Boolean, onClick: () -> Unit) {
    val color = rarityColor(rarity)  // plain fun, no @Composable needed
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color.copy(0.2f) else SurfaceVariant)
            .border(1.dp, if (selected) color.copy(0.7f) else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                stringResource(rarity.displayNameRes),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) color else TextSecondary,
            )
        }
    }
}

@Composable
private fun ActiveFilterChip(label: String, color: Color, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove), modifier = Modifier.size(10.dp), tint = color)
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private data class ActiveChip(
    val label: String,
    val color: Color,
    val onRemove: (SearchFilters) -> SearchFilters,
)

@Composable
private fun buildActiveChips(f: SearchFilters): List<ActiveChip> = buildList {
    f.wear?.let { add(ActiveChip(stringResource(it.displayNameRes), AccentOrange) { f -> f.copy(wear = null) }) }
    f.rarity?.let { add(ActiveChip(stringResource(it.displayNameRes), rarityColor(it)) { f -> f.copy(rarity = null) }) }
    if (f.statTrakOnly) add(ActiveChip("StatTrak™", AccentGreen) { f -> f.copy(statTrakOnly = false) })
    val hasPrice = f.minPrice != null || f.maxPrice != null
    if (hasPrice) {
        val lo = f.minPrice?.let { "$${it.toInt()}" } ?: "$0"
        val hi = f.maxPrice?.let { "$${it.toInt()}" } ?: stringResource(R.string.search_price_any)
        add(ActiveChip("$lo – $hi", AccentOrange) { f -> f.copy(minPrice = null, maxPrice = null) })
    }
    f.weapon?.let { add(ActiveChip(it, AccentOrange) { f -> f.copy(weapon = null) }) }
}

@Composable
private fun SuggestionRow(
    icon: @Composable () -> Unit,
    primary: String,
    secondary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(primary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(secondary, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}

@Composable
private fun SkinSuggestionRow(
    skin: com.burixer85.cs2skinflip.core.domain.model.Skin,
    onClick: () -> Unit,
) {
    val currency = com.burixer85.cs2skinflip.core.ui.LocalCurrency.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            coil.compose.AsyncImage(
                model = skin.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                skin.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                stringResource(skin.wear.displayNameRes),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
        if (skin.lowestMarketPrice > 0) {
            Text(
                currency.format(skin.lowestMarketPrice),
                style = MaterialTheme.typography.labelMedium,
                color = AccentOrange,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun rarityColor(rarity: SkinRarity): Color = when (rarity) {
    SkinRarity.CONSUMER -> RarityConsumer
    SkinRarity.INDUSTRIAL -> RarityIndustrial
    SkinRarity.MIL_SPEC -> RarityMilSpec
    SkinRarity.RESTRICTED -> RarityRestricted
    SkinRarity.CLASSIFIED -> RarityClassified
    SkinRarity.COVERT -> RarityCovert
    SkinRarity.CONTRABAND -> RarityContraband
    SkinRarity.KNIFE -> RarityKnife
}
