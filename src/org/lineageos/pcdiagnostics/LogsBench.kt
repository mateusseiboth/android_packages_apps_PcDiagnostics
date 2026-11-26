package org.lineageos.pcdiagnostics

import android.app.Application
import android.net.Uri
import android.util.Log
import java.io.FileNotFoundException
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.lineageos.pcdiagnostics.core.LogBundle
import org.lineageos.pcdiagnostics.core.LogCollectors
import org.lineageos.pcdiagnostics.core.LogProgress

enum class LogSaveStatus { IDLE, SAVING, SAVED, FAILED }

data class LogSaveState(val status: LogSaveStatus = LogSaveStatus.IDLE, val progress: LogProgress? = null)

/** The logs page's state: writes the logs zip into the file the person picked. */
class LogsBench(private val application: Application, private val scope: CoroutineScope) {
    private val reader = LogsReader(application)

    private val _state = MutableStateFlow(LogSaveState())
    val state: StateFlow<LogSaveState> = _state.asStateFlow()

    fun buildFileName() = LogBundle.buildFileName(LocalDateTime.now())

    fun save(uri: Uri) {
        if (_state.value.status == LogSaveStatus.SAVING) return
        _state.value = LogSaveState(LogSaveStatus.SAVING)
        scope.launch(Dispatchers.IO) {
            val saved = runCatching { writeZip(uri) }.onFailure { Log.w(TAG, "Could not save the logs to $uri", it) }.isSuccess
            _state.value = LogSaveState(RESULTS.getValue(saved))
        }
    }

    private fun writeZip(uri: Uri) {
        val destination = application.contentResolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())
        val failed = destination.use { LogBundle.write(LogCollectors.build(reader.buildSources()), it, ::onProgress) }
        Log.i(TAG, "Logs saved; parts that failed: $failed")
    }

    private fun onProgress(progress: LogProgress) {
        _state.value = LogSaveState(LogSaveStatus.SAVING, progress)
    }

    private companion object {
        const val TAG = "PcDiagnosticsLogs"
        val RESULTS = mapOf(true to LogSaveStatus.SAVED, false to LogSaveStatus.FAILED)
    }
}
