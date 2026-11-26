package org.lineageos.pcdiagnostics

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.io.File
import org.lineageos.pcdiagnostics.core.LogSources
import org.lineageos.pcdiagnostics.core.ProcessCommandRunner

/** What the logs read from Android: the radios as WifiManager and BluetoothAdapter see them, and the build. */
class LogsReader(context: Context) {
    private val wifiManager = context.getSystemService(WifiManager::class.java)
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)

    fun buildSources() = LogSources(File("/sys"), File("/proc"), ProcessCommandRunner, ::readRadios, ::readBuild)

    /** Each line on its own: a radio that cannot be read says why and the others stay. */
    private fun readRadios(): List<Pair<String, String>> = listOf(
        "wifi enabled" to describe { requireWifi().isWifiEnabled.toString() },
        "wifi state" to describe { requireWifi().wifiState.let { WIFI_STATES[it] ?: it.toString() } },
        "bluetooth enabled" to describe { requireBluetooth().isEnabled.toString() },
        "bluetooth state" to describe { requireBluetooth().state.let { BLUETOOTH_STATES[it] ?: it.toString() } },
    )

    private fun requireWifi(): WifiManager = checkNotNull(wifiManager) { "no WifiManager" }

    private fun requireBluetooth(): BluetoothAdapter = checkNotNull(bluetoothManager?.adapter) { "no Bluetooth adapter" }

    private fun readBuild(): List<Pair<String, String>> = listOf(
        "fingerprint" to Build.FINGERPRINT,
        "display" to Build.DISPLAY,
        "type" to Build.TYPE,
        "android" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT.toString(),
        "incremental" to Build.VERSION.INCREMENTAL,
        "security patch" to Build.VERSION.SECURITY_PATCH,
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "kernel" to System.getProperty("os.version").orEmpty(),
    )

    private fun describe(read: () -> String): String = runCatching(read).getOrElse { "error: $it" }

    private companion object {
        val WIFI_STATES = mapOf(
            WifiManager.WIFI_STATE_DISABLED to "disabled",
            WifiManager.WIFI_STATE_DISABLING to "disabling",
            WifiManager.WIFI_STATE_ENABLED to "enabled",
            WifiManager.WIFI_STATE_ENABLING to "enabling",
            WifiManager.WIFI_STATE_UNKNOWN to "unknown",
        )
        val BLUETOOTH_STATES = mapOf(
            BluetoothAdapter.STATE_OFF to "off",
            BluetoothAdapter.STATE_TURNING_ON to "turning on",
            BluetoothAdapter.STATE_ON to "on",
            BluetoothAdapter.STATE_TURNING_OFF to "turning off",
        )
    }
}
