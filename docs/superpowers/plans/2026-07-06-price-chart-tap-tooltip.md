# Price Chart Tap Tooltip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user tap or drag on the skin detail price chart to see a floating tooltip with the exact price and timestamp of the nearest point, persisting until the next touch and resetting when the range (24H/7D/30D) changes.

**Architecture:** All logic lives inside the existing `PriceLineChart` Composable in `SkinDetailScreen.kt`. A `pointerInput` gesture block tracks touch position and updates a `selectedIndex` state (keyed on `pricePoints` so it resets on range switch); the existing `Canvas` draw block additionally renders a guideline, a dot, and a text-measured tooltip bubble when a point is selected. No new files, no new dependencies, no backend/ViewModel changes.

**Tech Stack:** Jetpack Compose (`Canvas`, `pointerInput`, `TextMeasurer`) — all already part of the project's existing Compose/Material3 dependencies.

---

### Task 1: Tap-to-see-price tooltip on `PriceLineChart`

**Files:**
- Modify: `android/app/src/main/java/com/burixer85/cs2skinflip/features/skindetail/SkinDetailScreen.kt`

- [ ] **Step 1: Add new imports**

Add these imports alongside the existing `androidx.compose.runtime...` imports (after `androidx.compose.runtime.getValue` on line 44):

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

Add these alongside the existing `androidx.compose.ui...` imports (the block currently spans lines 45-58; insert in appropriate alphabetical-ish position near the other `androidx.compose.ui.*` imports):

```kotlin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
```

Add this alongside the existing `java.text.SimpleDateFormat` / `java.util.*` imports (currently lines 80-82):

```kotlin
import kotlin.math.roundToInt
```

- [ ] **Step 2: Replace `PriceLineChart` with the tooltip-enabled version, and add two helper functions**

Replace the entire `PriceLineChart` composable (currently spanning from `@Composable\nprivate fun PriceLineChart(pricePoints: List<PricePoint>, range: PriceRange) {` down through its closing `}`, immediately followed by the existing `formatAxisLabel` function) with:

```kotlin
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
```

This adds `nearestPointIndex` (touch-x to point-index mapping) and `formatTooltipTimestamp` (the detailed date/time string for the tooltip) as new private top-level functions, and keeps the existing `formatAxisLabel` unchanged and in place right after them.

- [ ] **Step 3: Compile check**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual smoke test in the running app**

This requires a running backend and an emulator/device — if unavailable in your environment, note that as a limitation and rely on the compile check instead. If available:
1. `./gradlew installDebug` with the backend running and `BACKEND_URL=http://10.0.2.2:3000` set in `local.properties`.
2. Open a skin detail screen with at least 2 price history points.
3. Tap a point on the chart — confirm a dark tooltip bubble appears above it showing price and a detailed date/time (e.g. "Jul 6, 2:00 PM"), with a vertical guideline and a dot at the point.
4. Press and drag across the chart — confirm the tooltip and guideline follow the finger, snapping to the nearest point.
5. Release — confirm the tooltip stays visible at the last point touched.
6. Tap 7D or 30D on the range selector — confirm the tooltip disappears (resets) since the point list changed.
7. Tap near the left and right edges of the chart — confirm the tooltip box stays fully inside the chart bounds (doesn't clip off-screen).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/burixer85/cs2skinflip/features/skindetail/SkinDetailScreen.kt
git commit -m "feat: show price tooltip on tap/drag over the price history chart"
```

## Spec coverage check

- Tap/drag selects nearest point (spec "Behavior" + `PriceLineChart` pointerInput block) → Step 2.
- Tooltip persists after release, resets on range change (`remember(pricePoints)` keying) → Step 2.
- Tooltip shows price + detailed timestamp, positioned above the point, clamped to canvas bounds → Step 2 (`formatTooltipTimestamp`, `boxLeft`/`boxTop` clamping).
- No new dependencies, no backend/ViewModel/other-composable changes → confirmed by the diff being scoped to `PriceLineChart` plus two new private helper functions in the same file.
