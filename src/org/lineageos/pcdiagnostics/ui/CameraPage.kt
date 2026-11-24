package org.lineageos.pcdiagnostics.ui

import android.Manifest
import android.content.Intent
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import org.lineageos.pcdiagnostics.CameraFrames
import org.lineageos.pcdiagnostics.DiagnosticsViewModel
import org.lineageos.pcdiagnostics.R
import org.lineageos.pcdiagnostics.SuiteKind
import org.lineageos.pcdiagnostics.core.CameraDescription
import org.lineageos.pcdiagnostics.core.Cameras

private typealias CameraRow = @Composable ColumnScope.() -> Unit

/** The cameras to choose from, the chosen one's live preview, and the camera test. */
@Composable
fun CameraPage(viewModel: DiagnosticsViewModel, onOpen: (Intent) -> Unit) {
    val bench = viewModel.camera
    val cameras by bench.cameras.collectAsStateWithLifecycle()
    val selected by bench.selected.collectAsStateWithLifecycle()
    val permission = rememberPermission(Manifest.permission.CAMERA)
    val chosen = Cameras.findSelected(cameras, selected)
    val rows: List<CameraRow> = cameras.map { camera -> { CameraChoice(camera, camera == chosen) { bench.onSelected(camera) } } }
    val none: CameraRow = { MessageRow(stringResource(R.string.camera_none)) }
    val access: CameraRow = { PermissionRow(permission, R.string.camera_permission_needed, R.string.camera_permission_denied, onOpen) }
    val live: List<CameraRow> = listOfNotNull(chosen).map { camera -> { PreviewRow(camera, bench.frames) } }
    val preview = live.takeIf { permission.isGranted } ?: live.map { access }
    val suite: CameraRow = { SuiteRowFor(viewModel.suites.getValue(SuiteKind.CAMERA), stringResource(R.string.run_camera)) }
    InformationGroup(title = stringResource(R.string.camera_title), rows = rows.ifEmpty { listOf(none) } + preview + listOf(suite))
}

@Composable
private fun CameraChoice(camera: CameraDescription, selected: Boolean, onSelect: () -> Unit) {
    val size = camera.largest?.let { stringResource(R.string.camera_size, it.width, it.height, it.megapixels) } ?: stringResource(R.string.value_unavailable)
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        InformationCell(stringResource(Texts.CAMERA_FACINGS.getValue(camera.facing)), size)
    }
}

/** The preview runs while the page shows; its frames feed the camera test. */
@Composable
private fun PreviewRow(camera: CameraDescription, frames: CameraFrames) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var error by remember(camera.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(camera.id) {
        val provider = runCatching { ProcessCameraProvider.awaitInstance(context) }.getOrNull()
        if (provider == null) {
            error = R.string.camera_open_failed
            return@LaunchedEffect
        }
        try {
            runCatching { bindCamera(provider, owner, camera, frames) { request = it } }.onFailure { error = R.string.camera_open_failed }
            awaitCancellation()
        } finally {
            provider.unbindAll()
            request = null
        }
    }
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.widthIn(max = PREVIEW_MAX_WIDTH).fillMaxWidth().aspectRatio(PREVIEW_ASPECT).clip(RoundedCornerShape(16.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            request?.let { CameraXViewfinder(it, Modifier.fillMaxSize()) }
            error?.let { Text(stringResource(it), Modifier.padding(24.dp), color = Color.White, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

private val PREVIEW_MAX_WIDTH = 720.dp
private const val PREVIEW_ASPECT = 16f / 9f

/** Binds a preview and an analysis of [camera] (by its Camera2 id) to [owner]. */
private fun bindCamera(provider: ProcessCameraProvider, owner: LifecycleOwner, camera: CameraDescription, frames: CameraFrames, onRequest: (SurfaceRequest) -> Unit) {
    val preview = Preview.Builder().build().apply { setSurfaceProvider { onRequest(it) } }
    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        .apply { setAnalyzer(Dispatchers.Default.asExecutor()) { frames.onImage(it) } }
    val selector = CameraSelector.Builder().addCameraFilter { infos -> infos.filter { camera.id in it.cameraIdentifier?.cameraIds.orEmpty() } }.build()
    provider.unbindAll()
    provider.bindToLifecycle(owner, selector, preview, analysis)
}
