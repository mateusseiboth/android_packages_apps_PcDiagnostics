package org.lineageos.pcdiagnostics.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.lineageos.pcdiagnostics.R

/** A runtime permission: granted or not, refused once already, and how to ask for it. */
class PermissionState(val isGranted: Boolean, val wasDenied: Boolean, val request: () -> Unit)

private fun Context.isGranted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Checked again whenever the app comes back, so a permission turned on in Settings shows at once. */
@Composable
fun rememberPermission(permission: String): PermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.isGranted(permission)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        denied = !result
    }
    LifecycleResumeEffect(permission) {
        granted = context.isGranted(permission)
        onPauseOrDispose {}
    }
    return PermissionState(granted, denied) { launcher.launch(permission) }
}

/** What the permission row says and its button: ask first; once refused, open the app's settings. */
private class PermissionStep(val button: Int, val act: (PermissionState, Context, (Intent) -> Unit) -> Unit)

private val PERMISSION_STEPS = mapOf(
    false to PermissionStep(R.string.permission_allow) { state, _, _ -> state.request() },
    true to PermissionStep(R.string.permission_open_settings) { _, context, onOpen ->
        onOpen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    },
)

@Composable
fun PermissionRow(state: PermissionState, askText: Int, deniedText: Int, onOpen: (Intent) -> Unit) {
    val context = LocalContext.current
    val step = PERMISSION_STEPS.getValue(state.wasDenied)
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(deniedText.takeIf { state.wasDenied } ?: askText),
            Modifier.weight(1f).padding(end = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        FilledTonalButton(onClick = { step.act(state, context, onOpen) }) { Text(stringResource(step.button)) }
    }
}
