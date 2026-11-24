package org.lineageos.pcdiagnostics.core

enum class NetworkKind { WIFI, ETHERNET, OTHER }

enum class WifiSecurity { OPEN, SECURED, UNKNOWN }

data class WifiDetails(
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val security: WifiSecurity,
)

/** The active network as the connectivity page shows it. */
data class NetworkSnapshot(
    val kind: NetworkKind,
    val addresses: List<String>,
    val gateway: String?,
    val dnsServers: List<String>,
    val wifi: WifiDetails?,
)

enum class SignalLevel(val minimumDbm: Int) {
    EXCELLENT(-55),
    GOOD(-66),
    AVERAGE(-77),
    WEAK(Int.MIN_VALUE);

    companion object {
        fun fromRssi(dbm: Int) = entries.first { dbm >= it.minimumDbm }
    }
}

object WifiChannel {
    /** Band edges in MHz and the frequency of channel 0 in each. */
    private class Band(val range: IntRange, val base: Int)

    private val BANDS = listOf(Band(2412..2472, 2407), Band(5150..5895, 5000), Band(5955..7115, 5950))
    private const val CHANNEL_14_MHZ = 2484

    fun fromFrequency(mhz: Int): Int? {
        if (mhz == CHANNEL_14_MHZ) return 14
        val band = BANDS.firstOrNull { mhz in it.range } ?: return null
        return (mhz - band.base) / 5
    }
}

data class HttpResult(val statusCode: Int, val latencyMs: Long)

/** The network calls the connectivity tests make, bound to the active network on the device. */
interface NetworkProbe {
    fun isReachable(host: String): Boolean

    /** How long resolving [host] took, or null when it failed. */
    fun resolve(host: String): Long?

    /** The answer to a GET of [url], or null when no connection could be made. */
    fun fetch(url: String): HttpResult?
}

/** The first rule that holds gives the outcome; none holding means passed. */
private fun judge(vararg rules: Pair<Boolean, () -> TestOutcome>): TestOutcome =
    rules.firstOrNull { (holds, _) -> holds }?.second?.invoke() ?: Passed()

class LocalNetworkTest(private val network: NetworkSnapshot, private val probe: NetworkProbe) : DiagnosticTest {
    override val name = TestName.NETWORK_LOCAL

    override fun run(cancel: CancelSignal): TestOutcome {
        val gateway = network.gateway ?: return Failed(Finding(FindingKey.NETWORK_NO_GATEWAY))
        return judge(!probe.isReachable(gateway) to { Warning(Finding(FindingKey.NETWORK_GATEWAY_UNREACHABLE, listOf(gateway))) })
    }
}

class NameResolutionTest(
    private val network: NetworkSnapshot,
    private val probe: NetworkProbe,
    private val host: String = "lineageos.org",
) : DiagnosticTest {
    override val name = TestName.NETWORK_NAME_RESOLUTION

    override fun run(cancel: CancelSignal): TestOutcome {
        if (network.dnsServers.isEmpty()) return Failed(Finding(FindingKey.NETWORK_NO_DNS))
        val millis = probe.resolve(host) ?: return Failed(Finding(FindingKey.NETWORK_DNS_FAILED))
        return judge((millis > SLOW_DNS_MILLIS) to { Warning(Finding(FindingKey.NETWORK_DNS_SLOW, listOf(millis))) })
    }

    private companion object {
        const val SLOW_DNS_MILLIS = 400
    }
}

/** Fetches the captive portal check page, which answers 204 with no body when the internet is open. */
class InternetConnectivityTest(private val probe: NetworkProbe, private val url: String) : DiagnosticTest {
    override val name = TestName.NETWORK_INTERNET

    override fun run(cancel: CancelSignal): TestOutcome {
        val result = probe.fetch(url) ?: return Failed(Finding(FindingKey.NETWORK_NO_INTERNET))
        return judge(
            (result.statusCode != NO_CONTENT) to { Warning(Finding(FindingKey.NETWORK_CAPTIVE_PORTAL)) },
            (result.latencyMs > SLOW_HTTPS_MILLIS) to { Warning(Finding(FindingKey.NETWORK_HTTPS_SLOW, listOf(result.latencyMs))) },
        )
    }

    private companion object {
        const val NO_CONTENT = 204
        const val SLOW_HTTPS_MILLIS = 1_000
    }
}

class WifiTest(private val network: NetworkSnapshot) : DiagnosticTest {
    override val name = TestName.NETWORK_WIFI

    override fun run(cancel: CancelSignal): TestOutcome {
        val wifi = network.wifi ?: return Failed(Finding(FindingKey.NETWORK_NO_WIFI_DETAILS))
        return judge(
            (SignalLevel.fromRssi(wifi.rssiDbm) == SignalLevel.WEAK) to { Warning(Finding(FindingKey.NETWORK_WEAK_SIGNAL)) },
            (wifi.security == WifiSecurity.OPEN) to { Warning(Finding(FindingKey.NETWORK_OPEN_WIFI)) },
        )
    }
}

object ConnectivitySuite {
    /** Tests for the active network; Wi-Fi adds its own. No network, no tests. */
    fun buildTests(network: NetworkSnapshot?, probe: NetworkProbe, checkUrl: String): List<DiagnosticTest> {
        val active = network ?: return emptyList()
        val common = listOf(LocalNetworkTest(active, probe), NameResolutionTest(active, probe), InternetConnectivityTest(probe, checkUrl))
        return common + listOfNotNull(WifiTest(active).takeIf { active.kind == NetworkKind.WIFI })
    }
}
