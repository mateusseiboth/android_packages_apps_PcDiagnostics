package org.lineageos.pcdiagnostics.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lineageos.pcdiagnostics.R
import org.lineageos.pcdiagnostics.core.Failed
import org.lineageos.pcdiagnostics.core.Finding
import org.lineageos.pcdiagnostics.core.Passed
import org.lineageos.pcdiagnostics.core.SuiteState
import org.lineageos.pcdiagnostics.core.SuiteStatus
import org.lineageos.pcdiagnostics.core.TestEntry
import org.lineageos.pcdiagnostics.core.TestOutcome
import org.lineageos.pcdiagnostics.core.TestStatus
import org.lineageos.pcdiagnostics.core.Warning

/** How a status looks on its chip: its icon and its colors. */
private class ChipStyle(val icon: ImageVector, val colors: (StatePalette) -> StateColors)

private val CHIP_STYLES = mapOf(
    SuiteStatus.NOT_RUN to ChipStyle(Icons.Filled.Remove, StatePalette::inactive),
    SuiteStatus.RUNNING to ChipStyle(Icons.Filled.MoreHoriz, StatePalette::progress),
    SuiteStatus.PASSED to ChipStyle(Icons.Filled.Check, StatePalette::success),
    SuiteStatus.WARNING to ChipStyle(Icons.Filled.PriorityHigh, StatePalette::warning),
    SuiteStatus.FAILED to ChipStyle(Icons.Filled.Close, StatePalette::failure),
    SuiteStatus.CANCELED to ChipStyle(Icons.Filled.Remove, StatePalette::inactive),
)

/** The suite button's label for each status; null is the card's own "Run ..." label. */
private val BUTTON_LABELS: Map<SuiteStatus, Int?> = mapOf(
    SuiteStatus.NOT_RUN to null,
    SuiteStatus.RUNNING to R.string.stop_test,
    SuiteStatus.PASSED to R.string.run_again,
    SuiteStatus.WARNING to R.string.run_again,
    SuiteStatus.FAILED to R.string.run_again,
    SuiteStatus.CANCELED to R.string.run_again,
)

private val OUTCOME_STATUSES = mapOf(Passed::class to SuiteStatus.PASSED, Warning::class to SuiteStatus.WARNING, Failed::class to SuiteStatus.FAILED)

private fun statusOf(outcome: TestOutcome) = OUTCOME_STATUSES.getValue(outcome::class)

/** A single test shows like a suite of one: waiting counts as running. */
private fun statusOf(status: TestStatus): SuiteStatus = when (status) {
    TestStatus.NotRun -> SuiteStatus.NOT_RUN
    TestStatus.Waiting, TestStatus.Running -> SuiteStatus.RUNNING
    TestStatus.Canceled -> SuiteStatus.CANCELED
    is TestStatus.Done -> statusOf(status.outcome)
}

/**
 * The bottom row of a card: the suite's status, "See report" once a test finished, and the button that
 * runs the suite, stops it or runs it again.
 */
@Composable
fun TestSuiteRow(state: SuiteState, runLabel: String, onRun: () -> Unit, onStop: () -> Unit) {
    var showReport by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SuiteSummary(state, Modifier.weight(1f))
            state.finished().takeIf { it.isNotEmpty() }?.let {
                TextButton(onClick = { showReport = !showReport }) {
                    Text(stringResource(R.string.hide_report.takeIf { showReport } ?: R.string.see_report))
                }
                Spacer(Modifier.width(10.dp))
            }
            SuiteButton(state, runLabel, onRun, onStop)
        }
        AnimatedVisibility(showReport) { Report(state.finished()) }
    }
}

@Composable
private fun SuiteSummary(state: SuiteState, modifier: Modifier) {
    val running = state.tests.firstOrNull { it.status == TestStatus.Running }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusChip(state.overall)
        running?.let {
            Text(
                stringResource(R.string.suite_running, stringResource(Texts.TEST_NAMES.getValue(it.name))),
                Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuiteButton(state: SuiteState, runLabel: String, onRun: () -> Unit, onStop: () -> Unit) {
    val label = BUTTON_LABELS.getValue(state.overall)?.let { stringResource(it) } ?: runLabel
    Button(
        onClick = onStop.takeIf { state.isRunning } ?: onRun,
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) { Text(label) }
}

@Composable
private fun Report(tests: List<TestEntry>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            tests.forEach { ReportLine(it) }
        }
    }
}

@Composable
private fun ReportLine(test: TestEntry) {
    val finding = (test.status as? TestStatus.Done)?.outcome?.finding
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Texts.TEST_NAMES.getValue(test.name)), style = MaterialTheme.typography.titleSmall)
            finding?.let { FindingText(it) }
        }
        StatusChip(statusOf(test.status))
    }
}

@Composable
fun FindingText(finding: Finding) {
    Text(
        stringResource(Texts.FINDINGS.getValue(finding.key), *finding.args.toTypedArray()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A small chip: a colored dot with the status icon, then the status name. */
@Composable
fun StatusChip(status: SuiteStatus) {
    val style = CHIP_STYLES.getValue(status)
    val colors = style.colors(LocalStatePalette.current)
    Surface(color = MaterialTheme.colorScheme.surfaceBright, shape = RoundedCornerShape(6.dp)) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(colors.onContainer), contentAlignment = Alignment.Center) {
                Icon(style.icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.surfaceBright)
            }
            Text(
                stringResource(Texts.SUITE_STATUSES.getValue(status)),
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
