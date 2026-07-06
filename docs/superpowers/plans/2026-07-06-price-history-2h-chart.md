# Price History 2h Resolution + Range Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record a `PriceHistory` point on every 2h bulk price refresh (instead of once per 24h) and show it on the Android skin detail screen as a time-axis line chart with a 24H/7D/30D range selector.

**Architecture:** Backend writes denser history and serves it pre-aggregated per range (`24h` raw, `7d`/`30d` collapsed to one point per day) so the Android chart never has to do its own aggregation. Android adds a range selector that refetches only the history list and swaps it into the existing `Skin.priceHistory` field, redrawing a new Canvas-based line chart.

**Tech Stack:** Fastify + Prisma (backend), Jetpack Compose (Android). No test framework exists in this repo (see `CLAUDE.md`: backend has "No test suite yet"; Android has no Compose UI test setup) — verification here uses `npx tsc --noEmit`, `./gradlew :app:compileDebugKotlin`, and manual smoke checks against a locally running backend instead of automated tests.

---

### Task 1: Write price history every 2h run instead of once per 24h

**Files:**
- Modify: `backend/src/jobs/populatePrices.ts:207-225`

- [ ] **Step 1: Remove the 24h dedup gate**

Replace lines 207-225 in `backend/src/jobs/populatePrices.ts`:

```ts
  // Save price history — max once per 24h per skin to avoid DB bloat
  const recentIds = new Set(
    (await prisma.priceHistory.findMany({
      where: { timestamp: { gte: new Date(Date.now() - 24 * 60 * 60 * 1000) } },
      select: { skinId: true },
      distinct: ['skinId'],
    })).map((h) => h.skinId),
  )

  const historyRows = skins
    .filter((s) => !recentIds.has(s.id))
    .flatMap((s) => {
      const skinport = skinportMap.get(s.marketHashName) ?? null
      const csgo     = csgoMarketMap.get(s.marketHashName) ?? null
      const waxpeer  = waxpeerMap.get(s.marketHashName) ?? null
      const lowestPrice = calcLowestPrice(skinport, csgo, waxpeer)
      if (!lowestPrice) return []
      return [{ skinId: s.id, price: lowestPrice, source: 'bulk' }]
    })
```

with:

```ts
  // Save a price history point for every skin on every run (every 2h).
  const historyRows = skins
    .flatMap((s) => {
      const skinport = skinportMap.get(s.marketHashName) ?? null
      const csgo     = csgoMarketMap.get(s.marketHashName) ?? null
      const waxpeer  = waxpeerMap.get(s.marketHashName) ?? null
      const lowestPrice = calcLowestPrice(skinport, csgo, waxpeer)
      if (!lowestPrice) return []
      return [{ skinId: s.id, price: lowestPrice, source: 'bulk' }]
    })
```

- [ ] **Step 2: Type-check**

Run: `cd backend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/jobs/populatePrices.ts
git commit -m "feat: write price history on every 2h refresh instead of once per 24h"
```

---

### Task 2: `price-history` endpoint accepts a `range` param with daily aggregation

**Files:**
- Modify: `backend/src/routes/skins.ts:184-198`

- [ ] **Step 1: Replace the `days`-based handler with a `range`-based one**

Replace lines 184-198 in `backend/src/routes/skins.ts`:

```ts
  app.get('/skins/:skinId/price-history', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }
    const { days = '30' } = request.query as { days?: string }

    const daysNum = Math.min(parseInt(days, 10) || 30, 365)
    const since = new Date(Date.now() - daysNum * 24 * 60 * 60 * 1000)

    const history = await prisma.priceHistory.findMany({
      where: { skinId, timestamp: { gte: since } },
      orderBy: { timestamp: 'asc' },
      select: { price: true, timestamp: true, source: true },
    })

    return history.map((h) => ({ price: h.price, timestamp: h.timestamp.toISOString(), source: h.source }))
  })
```

with:

```ts
  app.get('/skins/:skinId/price-history', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }
    const { range: rawRange } = request.query as { range?: string }

    const rangeConfig: Record<string, { sinceMs: number; aggregateDaily: boolean }> = {
      '24h': { sinceMs: 24 * 60 * 60 * 1000, aggregateDaily: false },
      '7d':  { sinceMs: 7 * 24 * 60 * 60 * 1000, aggregateDaily: true },
      '30d': { sinceMs: 30 * 24 * 60 * 60 * 1000, aggregateDaily: true },
    }
    const { sinceMs, aggregateDaily } = rangeConfig[rawRange ?? '24h'] ?? rangeConfig['24h']
    const since = new Date(Date.now() - sinceMs)

    const history = await prisma.priceHistory.findMany({
      where: { skinId, timestamp: { gte: since } },
      orderBy: { timestamp: 'asc' },
      select: { price: true, timestamp: true, source: true },
    })

    if (!aggregateDaily) {
      return history.map((h) => ({ price: h.price, timestamp: h.timestamp.toISOString(), source: h.source }))
    }

    // Collapse to one point per calendar day, keeping the last (most recent)
    // entry of each day. `history` is ordered ascending, so later writes to
    // the same key naturally overwrite earlier ones, and Map iteration order
    // (first-insertion order) stays ascending by day.
    const byDay = new Map<string, (typeof history)[number]>()
    for (const h of history) {
      const dayKey = h.timestamp.toISOString().slice(0, 10)
      byDay.set(dayKey, h)
    }

    return Array.from(byDay.values()).map((h) => ({
      price: h.price,
      timestamp: h.timestamp.toISOString(),
      source: h.source,
    }))
  })
```

- [ ] **Step 2: Type-check**

Run: `cd backend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Manual smoke test against local server**

Run: `cd backend && npm run dev` (leave running), then in another shell:

```bash
curl "http://localhost:3000/skins/<any-existing-skin-id>/price-history?range=24h"
curl "http://localhost:3000/skins/<any-existing-skin-id>/price-history?range=7d"
curl "http://localhost:3000/skins/<any-existing-skin-id>/price-history?range=30d"
curl "http://localhost:3000/skins/<any-existing-skin-id>/price-history"
```

Expected: all four return `200` with a JSON array of `{ price, timestamp, source }`; the no-param call behaves like `range=24h`. Use a real skin id from `GET /skins?limit=1` if you don't have one handy. Since history is fresh from Task 1, don't expect 7d/30d to have many points yet in a freshly-seeded DB — the important thing is no errors and shape is correct.

- [ ] **Step 4: Commit**

```bash
git add backend/src/routes/skins.ts
git commit -m "feat: price-history endpoint takes a range param, aggregates 7d/30d to daily points"
```

---

### Task 3: Shrink price history retention

**Files:**
- Modify: `backend/src/jobs/cleanupPriceHistory.ts:4`

- [ ] **Step 1: Change retention window**

Replace line 4 in `backend/src/jobs/cleanupPriceHistory.ts`:

```ts
const RETENTION_DAYS = 90
```

with:

```ts
const RETENTION_DAYS = 35
```

- [ ] **Step 2: Type-check**

Run: `cd backend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/jobs/cleanupPriceHistory.ts
git commit -m "chore: shrink price history retention to 35 days now that writes are 12x denser"
```

---

### Task 4: Android API + repository support range-based history fetch

**Files:**
- Modify: `android/app/src/main/java/com/burixer85/cs2skinflip/core/data/remote/CS2BackendApiService.kt:44-48`
- Modify: `android/app/src/main/java/com/burixer85/cs2skinflip/core/data/repository/SkinRepository.kt:85-95`

- [ ] **Step 1: Change the Retrofit query param**

Replace lines 44-48 in `CS2BackendApiService.kt`:

```kotlin
    @GET("skins/{id}/price-history")
    suspend fun getPriceHistory(
        @Path("id") id: String,
        @Query("days") days: Int = 30
    ): List<PriceHistoryDto>
```

with:

```kotlin
    @GET("skins/{id}/price-history")
    suspend fun getPriceHistory(
        @Path("id") id: String,
        @Query("range") range: String = "24h"
    ): List<PriceHistoryDto>
```

- [ ] **Step 2: Add a repository method for range-based history, reuse it in `getSkinById`**

Replace lines 85-95 in `SkinRepository.kt`:

```kotlin
    suspend fun getSkinById(id: String): Skin? {
        return runCatching {
            val skin = backendApi.getSkin(id)
            val history = runCatching {
                backendApi.getPriceHistory(id).map { it.toDomain() }
            }.getOrDefault(emptyList())
            skin.toDomain(priceHistory = history)
        }.getOrElse {
            MockData.getSkinById(id)
        }
    }
```

with:

```kotlin
    suspend fun getSkinById(id: String): Skin? {
        return runCatching {
            val skin = backendApi.getSkin(id)
            val history = getPriceHistory(id, range = "24h")
            skin.toDomain(priceHistory = history)
        }.getOrElse {
            MockData.getSkinById(id)
        }
    }

    suspend fun getPriceHistory(skinId: String, range: String): List<PricePoint> = runCatching {
        backendApi.getPriceHistory(skinId, range).map { it.toDomain() }
    }.getOrDefault(emptyList())
```

This requires `PricePoint` to be imported in `SkinRepository.kt` — check the top of the file; if `com.burixer85.cs2skinflip.core.domain.model.PricePoint` isn't already imported (only `Skin`, `SkinRarity`, `SkinWear` are), add it:

```kotlin
import com.burixer85.cs2skinflip.core.domain.model.PricePoint
```

- [ ] **Step 3: Compile check**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/burixer85/cs2skinflip/core/data/remote/CS2BackendApiService.kt android/app/src/main/java/com/burixer85/cs2skinflip/core/data/repository/SkinRepository.kt
git commit -m "feat: fetch price history by range instead of a fixed days window"
```

---

### Task 5: ViewModel range state + refetch

**Files:**
- Modify: `android/app/src/main/java/com/burixer85/cs2skinflip/features/skindetail/SkinDetailViewModel.kt`

- [ ] **Step 1: Add a `PriceRange` enum and range state, wire a range-change handler**

Replace the full contents of `SkinDetailViewModel.kt` with:

```kotlin
package com.burixer85.cs2skinflip.features.skindetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.data.repository.WatchlistRepository
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.WatchlistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SkinDetailUiState {
    object Loading : SkinDetailUiState()
    data class Success(val skin: Skin, val isInWatchlist: Boolean) : SkinDetailUiState()
    data class Error(val message: String) : SkinDetailUiState()
}

enum class PriceRange(val apiValue: String, val label: String) {
    DAY("24h", "24H"),
    WEEK("7d", "7D"),
    MONTH("30d", "30D"),
}

@HiltViewModel
class SkinDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val skinRepository: SkinRepository,
    private val watchlistRepository: WatchlistRepository,
    private val analytics: AnalyticsService,
) : ViewModel() {

    private val skinId: String = checkNotNull(savedStateHandle["skinId"])

    private val _uiState = MutableStateFlow<SkinDetailUiState>(SkinDetailUiState.Loading)
    val uiState: StateFlow<SkinDetailUiState> = _uiState

    private val _selectedRange = MutableStateFlow(PriceRange.DAY)
    val selectedRange: StateFlow<PriceRange> = _selectedRange

    init {
        loadSkin()
    }

    private fun loadSkin() {
        viewModelScope.launch {
            val skin = skinRepository.getSkinById(skinId)
            if (skin == null) {
                _uiState.value = SkinDetailUiState.Error("Skin not found")
            } else {
                val inWatchlist = watchlistRepository.isInWatchlist(skinId)
                _uiState.value = SkinDetailUiState.Success(skin, inWatchlist)
                analytics.logSkinViewed(skin.id, skin.name)
            }
        }
    }

    fun onRangeSelected(range: PriceRange) {
        if (range == _selectedRange.value) return
        _selectedRange.value = range
        viewModelScope.launch {
            val history = skinRepository.getPriceHistory(skinId, range.apiValue)
            val state = _uiState.value as? SkinDetailUiState.Success ?: return@launch
            _uiState.value = state.copy(skin = state.skin.copy(priceHistory = history))
        }
    }

    fun toggleWatchlist() {
        val state = _uiState.value as? SkinDetailUiState.Success ?: return
        viewModelScope.launch {
            if (state.isInWatchlist) {
                watchlistRepository.removeBySkinId(skinId)
            } else {
                analytics.logSkinAddedToWatchlist(state.skin.id, state.skin.name)
                watchlistRepository.add(
                    WatchlistItem(
                        skinId = state.skin.id,
                        skinName = state.skin.name,
                        skinImageUrl = state.skin.imageUrl,
                        targetBuyPrice = null,
                        targetSellPrice = null,
                        currentPrice = state.skin.lowestMarketPrice,
                        priceChange24h = state.skin.priceChange24h ?: 0.0
                    )
                )
            }
            _uiState.value = state.copy(isInWatchlist = !state.isInWatchlist)
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/burixer85/cs2skinflip/features/skindetail/SkinDetailViewModel.kt
git commit -m "feat: add selectable price-history range to SkinDetailViewModel"
```

---

### Task 6: Range selector UI + Canvas line chart replacing the bar chart

**Files:**
- Modify: `android/app/src/main/java/com/burixer85/cs2skinflip/features/skindetail/SkinDetailScreen.kt`

- [ ] **Step 1: Add new imports**

At the top of `SkinDetailScreen.kt`, add these imports alongside the existing ones (after the last `androidx.compose...` import block, before the `com.burixer85...` imports):

```kotlin
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
```

And add these two, alongside the existing `com.burixer85...` imports:

```kotlin
import com.burixer85.cs2skinflip.core.domain.model.PricePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

- [ ] **Step 2: Thread `selectedRange` / `onRangeSelected` from the screen down to the content**

Replace the `SkinDetailScreen` function body (the `is SkinDetailUiState.Success ->` branch, currently lines 127-134):

```kotlin
            is SkinDetailUiState.Success -> {
                SkinDetailContent(
                    skin = state.skin,
                    isInWatchlist = state.isInWatchlist,
                    onBack = onBack,
                    onToggleWatchlist = viewModel::toggleWatchlist
                )
            }
```

with:

```kotlin
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
```

- [ ] **Step 3: Update `SkinDetailContent` signature**

Replace the `SkinDetailContent` signature (currently lines 141-146):

```kotlin
private fun SkinDetailContent(
    skin: Skin,
    isInWatchlist: Boolean,
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
```

with:

```kotlin
private fun SkinDetailContent(
    skin: Skin,
    isInWatchlist: Boolean,
    selectedRange: PriceRange,
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onRangeSelected: (PriceRange) -> Unit
) {
```

- [ ] **Step 4: Replace the price history section**

Replace the price history block (currently lines 313-340):

```kotlin
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
```

with:

```kotlin
        // Price history (always shown; empty-state message when no data yet)
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Price History",
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
                        text = "Price history not yet available",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontStyle = FontStyle.Italic
                    )
                }
            } else {
                PriceLineChart(pricePoints = skin.priceHistory, range = selectedRange)
            }
        }
```

- [ ] **Step 5: Replace `SimplePriceChart` with `PriceRangeSelector` + `PriceLineChart`**

Replace the entire `SimplePriceChart` composable (currently lines 515-585, from `@Composable\nprivate fun SimplePriceChart(pricePoints: List<Double>) {` through its closing `}`) with:

```kotlin
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
                text = "Not enough data points",
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

private fun formatAxisLabel(timestampMillis: Long, range: PriceRange): String {
    val pattern = when (range) {
        PriceRange.DAY -> "h a"
        PriceRange.WEEK -> "EEE"
        PriceRange.MONTH -> "MMM d"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestampMillis))
}
```

Note: `PriceRange` is defined in `SkinDetailViewModel.kt` in the same package (`com.burixer85.cs2skinflip.features.skindetail`), so no import is needed for it in `SkinDetailScreen.kt`.

- [ ] **Step 6: Compile check**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Manual smoke test in the running app**

With the backend running locally and an emulator/device pointed at it (`BACKEND_URL=http://10.0.2.2:3000` in `local.properties`):
1. `./gradlew installDebug`
2. Open any skin's detail screen.
3. Confirm "Price History" header shows a 24H/7D/30D selector with 24H active by default, and the chart is a line/area chart (not bars).
4. Tap 7D and 30D — confirm the chart redraws without crashing and axis labels change format (hour → weekday → date).
5. If a skin has fewer than 2 history points in some range, confirm the "Not enough data points" placeholder shows instead of a broken chart.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/com/burixer85/cs2skinflip/features/skindetail/SkinDetailScreen.kt
git commit -m "feat: replace bar chart with range-selectable line chart on skin detail"
```

---

## Spec coverage check

- Write history every 2h (spec §1) → Task 1.
- `range` param + daily aggregation for 7d/30d (spec §2) → Task 2.
- Retention shrink to 35 days (spec §3) → Task 3.
- API + repository range support (spec §4) → Task 4.
- ViewModel range state + refetch (spec §5) → Task 5.
- UI range selector + line chart with time-axis labels (spec §6) → Task 6.
- Out-of-scope items (alert job, no new charting lib, no backfill) — no task touches the alert job, no new dependency is added (`Canvas`/`Path`/`Stroke` are all built into `androidx.compose.foundation`/`ui.graphics`), and no backfill script is included, consistent with the spec.
