package org.lineageos.pcdiagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// A group is a stack of rows 2dp apart: the group's outer corners are round, the inner ones nearly square.
private val OUTER = 20.dp
private val INNER = 4.dp
private val GROUP_SPACING = 2.dp

fun buildGroupShape(index: Int, count: Int): RoundedCornerShape {
    val top = OUTER.takeIf { index == 0 } ?: INNER
    val bottom = OUTER.takeIf { index == count - 1 } ?: INNER
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
fun SurfaceGroup(rows: List<@Composable ColumnScope.() -> Unit>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GROUP_SPACING)) {
        rows.forEachIndexed { index, row ->
            Column(
                Modifier.fillMaxWidth().clip(buildGroupShape(index, rows.size)).background(MaterialTheme.colorScheme.surfaceBright),
                content = row,
            )
        }
    }
}

/** A card of the overview: its title (and an optional subtitle) above a group of rows. */
@Composable
fun InformationGroup(title: String, rows: List<@Composable ColumnScope.() -> Unit>, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        SurfaceGroup(rows)
    }
}

/** A labelled value: the label on top, the value below. */
@Composable
fun InformationCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
