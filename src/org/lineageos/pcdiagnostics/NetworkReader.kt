package org.lineageos.pcdiagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.provider.Settings
import java.net.HttpURLConnection
import java.net.URL
import org.lineageos.pcdiagnostics.core.HttpResult
import org.lineageos.pcdiagnostics.core.NetworkKind
import org.lineageos.pcdiagnostics.core.NetworkProbe
import org.lineageos.pcdiagnostics.core.NetworkSnapshot
import org.lineageos.pcdiagnostics.core.WifiDetails
import org.lineageos.pcdiagnostics.core.WifiSecurity

/** The active network as a [NetworkSnapshot], and a probe bound to it. */
class NetworkReader(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.getSystemService(WifiManager::class.java)
    private val resolver = context.contentResolver

    fun readSnapshot(): NetworkSnapshot? {
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        val link = connectivity.getLinkProperties(network) ?: return null
        val kind = KINDS.entries.firstOrNull { capabilities.hasTransport(it.key) }?.value ?: NetworkKind.OTHER
        return NetworkSnapshot(
            kind = kind,
            addresses = link.linkAddresses.mapNotNull { it.address.hostAddress },
            gateway = findGateway(link),
            dnsServers = link.dnsServers.mapNotNull { it.hostAddress },
            wifi = readWifi().takeIf { kind == NetworkKind.WIFI },
        )
    }

    fun buildProbe(): NetworkProbe? = connectivity.activeNetwork?.let(::AndroidNetworkProbe)

    /** The page the system itself uses to find captive portals. */
    fun readCheckUrl(): String = Settings.Global.getString(resolver, CHECK_URL_SETTING) ?: DEFAULT_CHECK_URL

    private fun findGateway(link: LinkProperties): String? =
        link.routes.firstOrNull { it.isDefaultRoute && it.hasGateway() }?.gateway?.hostAddress

    @Suppress("DEPRECATION") // The only call that answers right away; NETWORK_SETTINGS keeps SSID and BSSID in it.
    private fun readWifi(): WifiDetails? {
        val info = wifiManager.connectionInfo ?: return null
        return WifiDetails(
            ssid = info.ssid?.removeSurrounding("\"")?.takeUnless { it == WifiManager.UNKNOWN_SSID },
            bssid = info.bssid,
            rssiDbm = info.rssi,
            frequencyMhz = info.frequency,
            security = SECURITY[info.currentSecurityType] ?: WifiSecurity.SECURED,
        )
    }

    private companion object {
        const val CHECK_URL_SETTING = "captive_portal_https_url"
        const val DEFAULT_CHECK_URL = "https://connectivitycheck.gstatic.com/generate_204"

        val KINDS = linkedMapOf(
            NetworkCapabilities.TRANSPORT_ETHERNET to NetworkKind.ETHERNET,
            NetworkCapabilities.TRANSPORT_WIFI to NetworkKind.WIFI,
        )
        val SECURITY = mapOf(WifiInfo.SECURITY_TYPE_OPEN to WifiSecurity.OPEN, WifiInfo.SECURITY_TYPE_UNKNOWN to WifiSecurity.UNKNOWN)
    }
}

/** Network calls bound to one network, so a VPN or another interface does not answer for it. */
class AndroidNetworkProbe(private val network: Network) : NetworkProbe {
    override fun isReachable(host: String): Boolean =
        runCatching { network.getByName(host).isReachable(TIMEOUT_MILLIS) }.getOrDefault(false)

    override fun resolve(host: String): Long? = runCatching { timed { network.getAllByName(host) }.second }.getOrNull()

    override fun fetch(url: String): HttpResult? = runCatching {
        val connection = network.openConnection(URL(url)) as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.useCaches = false
        try {
            val (status, millis) = timed { connection.responseCode }
            HttpResult(status, millis)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** The block's result and how long it took, in milliseconds. */
    private fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val result = block()
        return result to (System.nanoTime() - start) / 1_000_000
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000
    }
}
