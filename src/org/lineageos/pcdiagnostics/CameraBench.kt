package org.lineageos.pcdiagnostics

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import org.lineageos.pcdiagnostics.core.CameraDescription
import org.lineageos.pcdiagnostics.core.CameraTest

/** The camera page's state: the cameras, the chosen one, the preview's frames and the suite. */
class CameraBench(private val application: Application, scope: CoroutineScope) {
    private val reader = CameraReader(application)

    val cameras: StateFlow<List<CameraDescription>> = reader.watchCameras()
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    val frames = CameraFrames()

    /** The test judges the frames of the preview on the page. */
    val suite = SuiteController(scope) { listOf(CameraTest(reader.readCameras(), ::hasCameraPermission, frames)) }

    fun onSelected(camera: CameraDescription) {
        _selected.value = camera.id
    }

    fun hasCameraPermission() = application.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
