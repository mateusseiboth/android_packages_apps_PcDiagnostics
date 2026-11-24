package org.lineageos.pcdiagnostics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.lineageos.pcdiagnostics.core.ChartPoint
import org.lineageos.pcdiagnostics.core.ChartScale

data class ChartSeries(val values: List<Double>, val color: Color)

private const val GRID_LINES = 5

/** Percent values over time, newest on the right, over a grid of [GRID_LINES] lines. */
@Composable
fun LineChart(series: List<ChartSeries>, capacity: Int, modifier: Modifier = Modifier) {
    val scale = ChartScale(capacity, maxValue = 100.0)
    val gridColors = listOf(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.outlineVariant)
    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        drawGrid(gridColors)
        series.forEach { drawSeries(scale.buildPoints(it.values), it.color) }
    }
}

private fun DrawScope.drawGrid(colors: List<Color>) {
    val stroke = 1.dp.toPx()
    (0 until GRID_LINES).forEach { line ->
        val y = size.height * line / (GRID_LINES - 1)
        drawLine(colors[line % colors.size], Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
    }
}

private fun DrawScope.drawSeries(points: List<ChartPoint>, color: Color) {
    val toOffset = { point: ChartPoint -> Offset(point.x * size.width, point.y * size.height) }
    val first = points.firstOrNull() ?: return
    val path = Path().apply {
        toOffset(first).let { moveTo(it.x, it.y) }
        points.drop(1).map(toOffset).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
}

/** A rounded bar filled up to [fraction]. */
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
    }
}

/** A colored dot, a name and a value, as under the charts. */
@Composable
fun Legend(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
