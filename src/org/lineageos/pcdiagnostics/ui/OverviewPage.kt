package org.lineageos.pcdiagnostics.ui

import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import org.lineageos.pcdiagnostics.DiagnosticsViewModel
import org.lineageos.pcdiagnostics.LiveReadings
import org.lineageos.pcdiagnostics.R
import org.lineageos.pcdiagnostics.SuiteController
import org.lineageos.pcdiagnostics.SuiteKind
import org.lineageos.pcdiagnostics.core.BatteryInfo
import org.lineageos.pcdiagnostics.core.BatterySuite

private const val HISTORY_CAPACITY = 60

/** Battery (when there is one), CPU, memory and storage, each with its live numbers and its tests. */
@Composable
fun OverviewPage(viewModel: DiagnosticsViewModel) {
    val readings by viewModel.readings.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.device_summary, Build.MODEL, Build.VERSION.RELEASE),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        readings.battery?.let { BatteryCard(it, viewModel.suites.getValue(SuiteKind.BATTERY)) }
        CpuCard(readings, viewModel.suites.getValue(SuiteKind.CPU))
        AmountCard(
            title = stringResource(R.string.memory_title),
            usedBytes = readings.memory.usedBytes,
            totalBytes = readings.memory.totalBytes,
            usedFraction = readings.memory.usedFraction,
            suite = viewModel.suites.getValue(SuiteKind.MEMORY),
            runLabel = stringResource(R.string.run_memory),
        )
        AmountCard(
            title = stringResource(R.string.storage_title),
            usedBytes = readings.storage.usedBytes,
            totalBytes = readings.storage.totalBytes,
            usedFraction = readings.storage.usedFraction,
            suite = viewModel.suites.getValue(SuiteKind.STORAGE),
            runLabel = stringResource(R.string.run_storage),
        )
    }
}

@Composable
fun SuiteRowFor(suite: SuiteController, runLabel: String) {
    val state by suite.state.collectAsStateWithLifecycle()
    TestSuiteRow(state, runLabel, suite::start, suite::stop)
}

@Composable
private fun unavailable() = stringResource(R.string.value_unavailable)

@Composable
private fun BatteryCard(battery: BatteryInfo, suite: SuiteController) {
    val runLabel = stringResource(Texts.RATE_RUN_LABELS.getValue(BatterySuite.chooseKind(battery)))
    InformationGroup(
        title = stringResource(R.string.battery_title),
        subtitle = battery.designCapacityMah?.let { stringResource(R.string.battery_capacity, it) },
        rows = listOf(
            { BatteryLevel(battery) },
            {
                Row(Modifier.fillMaxWidth()) {
                    InformationCell(stringResource(R.string.battery_health), battery.healthPercent?.let { stringResource(R.string.percent_value, it) } ?: unavailable(), Modifier.weight(1f))
                    InformationCell(stringResource(R.string.battery_cycles), battery.cycleCount?.toString() ?: unavailable(), Modifier.weight(1f))
                    InformationCell(stringResource(R.string.battery_current), battery.currentMa?.let { stringResource(R.string.battery_current_value, it) } ?: unavailable(), Modifier.weight(1f))
                }
            },
            { SuiteRowFor(suite, runLabel) },
        ),
    )
}

@Composable
private fun BatteryLevel(battery: BatteryInfo) {
    val minutes = battery.minutesLeft
    val timeText = Texts.BATTERY_TIMES[battery.status]?.takeIf { minutes != null }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.percent_value, battery.levelPercent.roundToInt()),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(stringResource(Texts.BATTERY_STATUSES.getValue(battery.status)), style = MaterialTheme.typography.bodyMedium)
        timeText?.let { text ->
            val total = minutes ?: 0
            Text(stringResource(text, total / 60, total % 60), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CpuCard(readings: LiveReadings, suite: SuiteController) {
    val colors = MaterialTheme.colorScheme
    val usage = readings.cpuUsage
    InformationGroup(
        title = stringResource(R.string.cpu_title),
        subtitle = stringResource(R.string.cpu_model_threads, readings.cpuModel.name, readings.cpuModel.threads),
        rows = listOf(
            {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LineChart(
                        listOf(
                            ChartSeries(readings.cpuHistory.map { it.userPercent }, colors.primary),
                            ChartSeries(readings.cpuHistory.map { it.systemPercent }, colors.tertiary),
                        ),
                        capacity = HISTORY_CAPACITY,
                        modifier = Modifier.padding(vertical = 15.dp),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Legend(stringResource(R.string.cpu_user), stringResource(R.string.percent_value, usage.userPercent.roundToInt()), colors.primary, Modifier.weight(1f))
                        Legend(stringResource(R.string.cpu_system), stringResource(R.string.percent_value, usage.systemPercent.roundToInt()), colors.tertiary, Modifier.weight(1f))
                    }
                }
            },
            {
                Row(Modifier.fillMaxWidth()) {
                    InformationCell(stringResource(R.string.cpu_current_usage), stringResource(R.string.percent_value, usage.totalPercent.roundToInt()), Modifier.weight(1f))
                    InformationCell(
                        stringResource(R.string.cpu_temperature),
                        readings.cpuMilliCelsius?.let { stringResource(R.string.cpu_temperature_value, it / 1000.0) } ?: unavailable(),
                        Modifier.weight(1f),
                    )
                    InformationCell(
                        stringResource(R.string.cpu_speed),
                        readings.cpuSpeedKhz?.let { stringResource(R.string.cpu_speed_value, it / 1_000_000.0) } ?: unavailable(),
                        Modifier.weight(1f),
                    )
                }
            },
            { SuiteRowFor(suite, stringResource(R.string.run_cpu)) },
        ),
    )
}

/** Memory or storage: how much is used of the total, with a bar, and the card's tests. */
@Composable
private fun AmountCard(title: String, usedBytes: Long, totalBytes: Long, usedFraction: Float, suite: SuiteController, runLabel: String) {
    val context = LocalContext.current
    InformationGroup(
        title = title,
        rows = listOf(
            {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.amount_used, Formatter.formatFileSize(context, usedBytes)), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.amount_total, Formatter.formatFileSize(context, totalBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    UsageBar(usedFraction, Modifier.padding(top = 12.dp))
                }
            },
            { SuiteRowFor(suite, runLabel) },
        ),
    )
}
