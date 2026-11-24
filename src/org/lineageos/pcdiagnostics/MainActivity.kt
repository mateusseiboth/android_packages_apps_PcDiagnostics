package org.lineageos.pcdiagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import org.lineageos.pcdiagnostics.ui.DiagnosticsApp
import org.lineageos.pcdiagnostics.ui.DiagnosticsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[DiagnosticsViewModel::class.java]
        setContent {
            DiagnosticsTheme {
                DiagnosticsApp(viewModel, onOpen = ::startActivity)
            }
        }
    }
}
