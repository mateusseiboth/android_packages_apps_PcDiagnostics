package org.lineageos.pcdiagnostics.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lineageos.pcdiagnostics.DiagnosticsViewModel
import org.lineageos.pcdiagnostics.LogSaveState
import org.lineageos.pcdiagnostics.LogSaveStatus
import org.lineageos.pcdiagnostics.R
import org.lineageos.pcdiagnostics.core.LogBundle

/** The row under the button for each status: progress while saving, then the result; none before. */
private val STATUS_ROWS: Map<LogSaveStatus, (@Composable (LogSaveState) -> Unit)?> = mapOf(
    LogSaveStatus.IDLE to null,
    LogSaveStatus.SAVING to { state -> SavingRow(state) },
    LogSaveStatus.SAVED to { _ -> ResultRow(R.string.logs_saved) },
    LogSaveStatus.FAILED to { _ -> ResultRow(R.string.logs_failed) },
)

/** Saves the logs zip to a file the person picks (a USB drive when there is no network), for a bug report. */
@Composable
fun LogsPage(viewModel: DiagnosticsViewModel) {
    val bench = viewModel.logs
    val state by bench.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(LogBundle.MIME_TYPE)) { uri -> uri?.let(bench::save) }
    val saveRow: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.logs_description), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { picker.launch(bench.buildFileName()) }, enabled = state.status != LogSaveStatus.SAVING) {
                Text(stringResource(R.string.logs_save))
            }
        }
    }
    SurfaceGroup(listOfNotNull(saveRow, buildStatusRow(state)))
}

private fun buildStatusRow(state: LogSaveState): (@Composable ColumnScope.() -> Unit)? {
    val row = STATUS_ROWS.getValue(state.status) ?: return null
    return { row(state) }
}

@Composable
private fun SavingRow(state: LogSaveState) {
    val done = state.progress?.done ?: 0
    val total = state.progress?.total ?: 1
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.logs_saving, done, total), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultRow(text: Int) {
    Text(stringResource(text), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
}
