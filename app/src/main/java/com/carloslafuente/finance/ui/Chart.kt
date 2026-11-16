package com.carloslafuente.finance.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carloslafuente.finance.R
import com.carloslafuente.finance.domain.ValuePoint
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ChartRange(@StringRes val label: Int, private val months: Long?) {
    ONE_MONTH(R.string.range_1m, 1),
    THREE_MONTHS(R.string.range_3m, 3),
    SIX_MONTHS(R.string.range_6m, 6),
    ONE_YEAR(R.string.range_1y, 12),
    MAX(R.string.range_max, null);

    /** First day shown, or null for everything. */
    fun start(today: LocalDate): LocalDate? = months?.let { today.minusMonths(it) }
}

@Composable
fun RangeChips(selected: ChartRange, onSelect: (ChartRange) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        ChartRange.entries.forEach { range ->
            val isSelected = range == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onSelect(range) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(range.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Line chart of [values] in the style of a broker app: no axes, a soft fill under the line and
 * a crosshair that follows the finger. [reference] is a second series on the same scale, drawn
 * dashed; [baseline] a dotted horizontal level; [markers] are indices highlighted with a dot.
 * [scrub] is the index under the finger, owned by the caller so it can show that point's value.
 */
@Composable
fun LineChart(
    values: List<Float>,
    lineColor: Color,
    scrub: Int?,
    onScrub: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    reference: List<Float>? = null,
    baseline: Float? = null,
    markers: List<Int> = emptyList(),
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onSurface
    val currentOnScrub = rememberUpdatedState(onScrub)
    val count = values.size

    Canvas(
        modifier.pointerInput(count) {
            if (count < 2) return@pointerInput
            fun indexAt(x: Float) = (x / size.width * (count - 1)).roundToInt().coerceIn(0, count - 1)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Only a mostly horizontal drag scrubs, so the list underneath can still scroll.
                var horizontal = false
                val start = awaitTouchSlopOrCancellation(down.id) { change, over ->
                    horizontal = abs(over.x) > abs(over.y)
                    if (horizontal) change.consume()
                }
                if (start != null && horizontal) {
                    currentOnScrub.value(indexAt(start.position.x))
                    drag(start.id) { change ->
                        change.consume()
                        currentOnScrub.value(indexAt(change.position.x))
                    }
                    currentOnScrub.value(null)
                }
            }
        },
    ) {
        if (count < 2) return@Canvas
        val all = values + reference.orEmpty() + listOfNotNull(baseline)
        val low = all.min()
        val high = all.max()
        val span = (high - low).takeIf { it > 0f } ?: 1f
        val top = 8.dp.toPx()
        val height = size.height - 2 * top
        fun x(i: Int) = size.width * i / (count - 1)
        fun y(v: Float) = top + height * (1 - (v - low) / span)

        fun path(series: List<Float>) = Path().apply {
            series.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }

        val line = path(values)
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)))

        if (baseline != null) {
            drawLine(
                muted, Offset(0f, y(baseline)), Offset(size.width, y(baseline)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
            )
        }
        if (reference != null && reference.size == count) {
            drawPath(
                path(reference), muted,
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
        drawPath(line, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        for (i in markers) {
            drawCircle(surface, 5.dp.toPx(), Offset(x(i), y(values[i])))
            drawCircle(ink, 3.dp.toPx(), Offset(x(i), y(values[i])))
        }
        if (scrub != null && scrub < count) {
            drawLine(muted, Offset(x(scrub), 0f), Offset(x(scrub), size.height), strokeWidth = 1.dp.toPx())
            drawCircle(surface, 7.dp.toPx(), Offset(x(scrub), y(values[scrub])))
            drawCircle(lineColor, 5.dp.toPx(), Offset(x(scrub), y(values[scrub])))
        }
    }
}

/** One day of a chart. [gain] and [rate] are measured from the first point of the visible range. */
data class ChartPoint(
    val date: LocalDate,
    val value: BigDecimal,
    val gain: BigDecimal,
    val rate: Double?,
    val reference: BigDecimal? = null,
)

@Composable
fun gainColor(signum: Int): Color =
    if (signum >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

/** "+12,30 € (+1,2 %)"; the sign is always written, so the color is never the only cue. */
fun formatGain(gain: BigDecimal, rate: Double?): String = when {
    rate == null -> formatSignedMoney(gain)
    hideAmounts -> formatPercent(rate)
    else -> formatSignedMoney(gain) + " (" + formatPercent(rate) + ")"
}

/** Value-over-time points of [range]; gain and rate are measured from the first visible day. */
fun valuePoints(history: List<ValuePoint>, range: ChartRange): List<ChartPoint> {
    val start = range.start(LocalDate.now())
    val visible = if (start == null) history else history.filter { it.date >= start }
    val first = visible.firstOrNull() ?: return emptyList()
    return visible.map {
        val added = it.invested - first.invested
        val gain = (it.value - first.value) - added
        // Measured against what was at work: the starting value plus the money added since.
        val base = first.value + added.max(BigDecimal.ZERO)
        ChartPoint(it.date, it.value, gain, if (base.signum() > 0) gain.toDouble() / base.toDouble() else null, it.invested)
    }
}

/** Tiny axis-less line for list rows: the shape of the last weeks, nothing to read off it. */
@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val color = gainColor(values.last().compareTo(values.first()))
    Canvas(modifier) {
        val low = values.min()
        val span = (values.max() - low).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * (1 - (v - low) / span)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Headline value, gain over the selected range, chart and range selector. Dragging over the
 * chart moves the headline to that day. [points] must already be limited to [range].
 */
@Composable
fun ChartSection(
    points: List<ChartPoint>,
    range: ChartRange,
    onRange: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
    fallbackValue: BigDecimal? = null,
    showBaseline: Boolean = false,
    markers: List<LocalDate> = emptyList(),
) {
    var scrub by remember(points.size, range) { mutableStateOf<Int?>(null) }
    val haptics = LocalHapticFeedback.current
    val shown = scrub?.let(points::getOrNull) ?: points.lastOrNull()
    Column(modifier) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                (shown?.value ?: fallbackValue)?.let(::displayMoney) ?: "—",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (shown != null && points.size >= 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatGain(shown.gain, shown.rate),
                        style = MaterialTheme.typography.titleSmall,
                        color = gainColor(shown.gain.signum()),
                    )
                    Text(
                        " · " + if (scrub != null) formatDate(shown.date) else stringResource(range.label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (points.size < 2) {
            Text(
                stringResource(R.string.chart_no_history),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        } else {
            LineChart(
                values = points.map { it.value.toFloat() },
                lineColor = gainColor(points.last().gain.signum()),
                scrub = scrub,
                onScrub = {
                    if (it != null && it != scrub) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scrub = it
                },
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 12.dp),
                reference = points.map { it.reference?.toFloat() }.takeIf { null !in it }?.filterNotNull(),
                baseline = points.first().value.toFloat().takeIf { showBaseline },
                markers = markers.mapNotNull { day -> points.indexOfFirst { it.date >= day }.takeIf { it >= 0 } }.distinct(),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    formatDate(points.first().date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDate(points.last().date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RangeChips(range, onRange, Modifier.padding(top = 4.dp))
    }
}
