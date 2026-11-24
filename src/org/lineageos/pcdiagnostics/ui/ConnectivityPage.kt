package org.lineageos.pcdiagnostics.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lineageos.pcdiagnostics.DiagnosticsViewModel
import org.lineageos.pcdiagnostics.R
import org.lineageos.pcdiagnostics.SuiteKind
import org.lineageos.pcdiagnostics.core.NetworkSnapshot
import org.lineageos.pcdiagnostics.core.SignalLevel
import org.lineageos.pcdiagnostics.core.WifiChannel
import org.lineageos.pcdiagnostics.core.WifiDetails

/** The active network's details and its tests, or how to connect when there is none. */
@Composable
fun ConnectivityPage(viewModel: DiagnosticsViewModel, onOpen: (Intent) -> Unit) {
    val readings by viewModel.readings.collectAsStateWithLifecycle()
    val openSettings = { onOpen(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
    readings.network?.let { NetworkCard(it, viewModel, openSettings) } ?: NoNetwork(openSettings)
}

@Composable
private fun NetworkCard(network: NetworkSnapshot, viewModel: DiagnosticsViewModel, openSettings: () -> Unit) {
    val details = buildWifiDetails(network.wifi) + buildAddressDetails(network)
    InformationGroup(
        title = stringResource(Texts.NETWORK_KINDS.getValue(network.kind)),
        subtitle = stringResource(R.string.network_connected),
        rows = listOf(
            { Column(Modifier.padding(vertical = 8.dp)) { details.forEach { (label, value) -> DetailLine(label, value) } } },
            { SuiteRowFor(viewModel.suites.getValue(SuiteKind.CONNECTIVITY), stringResource(R.string.run_connectivity)) },
            { TextButton(onClick = openSettings, modifier = Modifier.padding(8.dp)) { Text(stringResource(R.string.open_network_settings)) } },
        ),
    )
}

@Composable
private fun buildWifiDetails(wifi: WifiDetails?): List<Pair<String, String>> {
    val link = wifi ?: return emptyList()
    val unavailable = stringResource(R.string.value_unavailable)
    val signal = stringResource(Texts.SIGNAL_LEVELS.getValue(SignalLevel.fromRssi(link.rssiDbm)))
    return listOf(
        stringResource(R.string.label_ssid) to (link.ssid ?: unavailable),
        stringResource(R.string.label_bssid) to (link.bssid ?: unavailable),
        stringResource(R.string.label_signal) to "$signal (${link.rssiDbm} dBm)",
        stringResource(R.string.label_security) to stringResource(Texts.SECURITY.getValue(link.security)),
        stringResource(R.string.label_channel) to (WifiChannel.fromFrequency(link.frequencyMhz)?.toString() ?: unavailable),
    )
}

@Composable
private fun buildAddressDetails(network: NetworkSnapshot): List<Pair<String, String>> {
    val unavailable = stringResource(R.string.value_unavailable)
    val joined = { values: List<String> -> values.joinToString(", ").ifEmpty { unavailable } }
    return listOf(
        stringResource(R.string.label_ip) to joined(network.addresses),
        stringResource(R.string.label_gateway) to (network.gateway ?: unavailable),
        stringResource(R.string.label_dns) to joined(network.dnsServers),
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NoNetwork(openSettings: () -> Unit) {
    SurfaceGroup(
        listOf(
            {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.no_network), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = openSettings) { Text(stringResource(R.string.open_network_settings)) }
                }
            },
        ),
    )
}
