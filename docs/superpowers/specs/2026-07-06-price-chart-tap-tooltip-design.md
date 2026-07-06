# Tap-to-see-price tooltip on the price history chart

## Problem

The `PriceLineChart` Composable added on the skin detail screen (see
`android/app/src/main/java/com/burixer85/cs2skinflip/features/skindetail/SkinDetailScreen.kt`)
draws a line/area chart with only two Y-axis labels (min/max) and a handful
of X-axis time labels. There's no way to see the exact price and timestamp
of any individual point on the chart — a common expectation for a price
chart.

## Goal

Let the user tap (or drag) anywhere on the chart to see the exact price and
timestamp of the nearest data point, rendered as a floating tooltip
attached to that point.

## Behavior

- **Touch and drag ("scrub")**: pressing down anywhere on the chart selects
  the nearest point by X position. Dragging while pressed continuously
  updates the selection to track the finger.
- **Persists after release**: lifting the finger leaves the last-selected
  point's tooltip visible. It stays until the user touches the chart again.
- **Resets on range change**: switching between 24H/7D/30D fetches a new
  `pricePoints` list (a new object reference each time from
  `SkinDetailViewModel.onRangeSelected`); the tooltip selection resets to
  none when this happens, since a previously-selected index would point at
  an unrelated point in the new time window.

## Implementation

### `PriceLineChart` (in `SkinDetailScreen.kt`)

- Add `var selectedIndex by remember(pricePoints) { mutableStateOf<Int?>(null) }`
  inside `PriceLineChart` — keying `remember` on `pricePoints` gives the
  "reset on range change" behavior for free, since the ViewModel always
  supplies a new list instance after a range switch.
- Add `Modifier.pointerInput(pricePoints) { ... }` to the existing `Canvas`,
  using `awaitEachGesture` (via `awaitFirstDown` + a loop over
  `awaitPointerEvent()` while any change is `pressed`) so a plain tap (no
  movement) and a drag are both handled by the same gesture block. On each
  down/move, compute the nearest point index from the touch X position
  (`(offset.x / stepX).roundToInt().coerceIn(0, pricePoints.size - 1)`) and
  update `selectedIndex`. The gesture block does not clear
  `selectedIndex` on release — that's what makes the tooltip persist per
  the "persists after release" behavior above.
- When `selectedIndex != null`, draw, on top of the existing line/area path:
  1. A vertical guideline (accent color, thin stroke, low alpha) at the
     selected point's X, spanning the full canvas height.
  2. A filled circle (accent color) at the selected point's exact (x, y).
  3. A tooltip: a rounded-rect background (drawn via `drawRoundRect`) sized
     to fit two lines of text — price (`Currency.format(point.price)`,
     bold) and a detailed timestamp (`"MMM d, h:mm a"`, e.g. "Jul 6, 2:00
     PM") — rendered with `rememberTextMeasurer()` + the `DrawScope.drawText`
     extension (no new dependency; `TextMeasurer` is part of
     `androidx.compose.ui.text` and already available in Compose Material3).
     Positioned above the selected point, horizontally centered on it but
     clamped so the box never renders outside the canvas bounds (clamp its
     left edge to `0` and its right edge to `size.width`).

### Formatting helper

A small `formatTooltipTimestamp(timestampMillis: Long): String` helper
(next to the existing `formatAxisLabel`) formats the detailed date/time
string with `SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())`. This
is intentionally range-independent (unlike `formatAxisLabel`, which varies
by range) since the tooltip always shows the same level of detail
regardless of which range is selected.

## Out of scope

- No haptic feedback on selection.
- No support for multi-touch/pinch-zoom on the chart.
- No animation on tooltip appearance/movement — it snaps directly to the
  selected point.
- No changes to the backend, ViewModel, or range selector — this is purely
  additive UI/gesture logic inside `PriceLineChart`.
