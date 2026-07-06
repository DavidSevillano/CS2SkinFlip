# Price history at 2h resolution + range-selectable chart

## Problem

The price bulk job (`populatePrices`) refreshes prices every 2 hours, but
`PriceHistory` only persists one row per skin per 24h (see the `recentIds`
gate in `backend/src/jobs/populatePrices.ts`). This means the price history
shown on the skin detail screen is effectively a daily snapshot, even though
finer-grained data is available every run. The Android chart itself
(`SimplePriceChart` in `SkinDetailScreen.kt`) is a bar chart with no time
axis and a hardcoded "30-Day Price History" label.

## Goal

Record a price history point on every bulk refresh (every 2h) and surface
that granularity clearly in the Android app via a time-axis line chart with
a user-selectable range (24H / 7D / 30D).

## Backend changes

### 1. Write history every run, not once per 24h

In `backend/src/jobs/populatePrices.ts`, remove the `recentIds` gate (lines
~207-225) that currently skips writing a `PriceHistory` row for a skin if
one was already written in the last 24h. After this change, every bulk run
(every 2h) writes one `PriceHistory` row per skin with a valid `lowestPrice`.

### 2. `GET /skins/:skinId/price-history` — range-based query

Replace the existing `days` query param with `range: '24h' | '7d' | '30d'`
(default `'24h'`, reject/ignore unknown values by falling back to `'24h'`).

- **`24h`**: return raw points where `timestamp >= now - 24h`, ordered
  ascending. With a 2h job cadence this is ~12 points.
- **`7d`** / **`30d`**: query raw points for the window
  (`now - 7d` / `now - 30d`), then collapse to one point per calendar day by
  keeping the **last** (most recent) point of each day — a "closing price"
  per day. Result is ~7 or ~30 points, ordered ascending by day.

Response shape stays the same: `{ price, timestamp, source }[]`.

### 3. Shrink retention

In `backend/src/jobs/cleanupPriceHistory.ts`, change `RETENTION_DAYS` from
`90` to `35` (a small buffer over the 30-day max range exposed by the UI).
Writing 12x more rows per day than before makes the old 90-day retention
needlessly expensive in storage with no UI benefit, since nothing reads
history older than 30 days.

## Android changes

### 4. API + repository

- `CS2BackendApiService.getPriceHistory`: change the `@Query("days") days: Int = 30`
  parameter to `@Query("range") range: String = "24h"`.
- `SkinRepository`: expose a method to fetch price history independently of
  the full skin (`getPriceHistory(skinId: String, range: String): List<PricePoint>`),
  reusing the existing `PriceHistoryDto.toDomain()` mapper. `getSkinById`
  keeps calling it with the default `"24h"` range for the initial load.

### 5. ViewModel: range state + refetch

`SkinDetailViewModel` adds:
- `selectedRange: StateFlow<String>` (values `"24h" | "7d" | "30d"`, default `"24h"`).
- `onRangeSelected(range: String)`: updates `selectedRange`, calls
  `skinRepository.getPriceHistory(skinId, range)`, and replaces
  `priceHistory` on the current `Success` state's `Skin` (via `.copy(...)`)
  without re-fetching the rest of the skin.

### 6. UI: range selector + line chart

In `SkinDetailScreen.kt`:
- Section title becomes dynamic: "Price History" with a segmented control
  (24H / 7D / 30D) directly below it, wired to `onRangeSelected`.
- Replace `SimplePriceChart` (bar chart) with a new `PriceLineChart`
  Composable: a `Canvas`-drawn line/area chart plotting `PricePoint.price`
  against `PricePoint.timestamp`, keeping the existing min/max Y-axis label
  style. Add X-axis labels formatted per range:
  - `24h`: hour of day (e.g. "2 PM")
  - `7d`: weekday abbreviation (e.g. "Mon")
  - `30d`: short date (e.g. "Jul 6")
- Empty-state and "not enough data points" messages are preserved as-is,
  reused by the new chart.

## Out of scope

- No changes to the alert-checking job's use of `PriceHistory` for 24h %
  change calculation — that logic already queries by timestamp directly and
  is unaffected by the new write cadence (it becomes more accurate, if
  anything, since there are more points to find a "closest to 24h ago"
  match against — no code change needed there).
- No new charting library dependency; the line chart is hand-drawn on
  `Canvas`, consistent with the existing hand-drawn bar chart and
  `FloatRangeIndicator`.
- No backfill of historical data — the denser history only starts
  accumulating from when this change ships.
