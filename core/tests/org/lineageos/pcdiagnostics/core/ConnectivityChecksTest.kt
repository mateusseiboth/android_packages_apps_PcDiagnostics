package org.lineageos.pcdiagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectivityChecksTest {
    private val notCanceled = CancelSignal { false }

    private class FakeProbe(
        val reachable: Boolean = true,
        val dnsMillis: Long? = 20,
        val http: HttpResult? = HttpResult(204, 100),
    ) : NetworkProbe {
        override fun isReachable(host: String) = reachable
        override fun resolve(host: String) = dnsMillis
        override fun fetch(url: String) = http
    }

    private val wifi = WifiDetails(ssid = "Home", bssid = "aa:bb", rssiDbm = -50, frequencyMhz = 5180, security = WifiSecurity.SECURED)
    private val network = NetworkSnapshot(NetworkKind.WIFI, listOf("192.168.0.10"), gateway = "192.168.0.1", dnsServers = listOf("192.168.0.1"), wifi = wifi)

    @Test
    fun `signal levels from rssi`() {
        assertEquals(SignalLevel.EXCELLENT, SignalLevel.fromRssi(-50))
        assertEquals(SignalLevel.GOOD, SignalLevel.fromRssi(-60))
        assertEquals(SignalLevel.AVERAGE, SignalLevel.fromRssi(-70))
        assertEquals(SignalLevel.WEAK, SignalLevel.fromRssi(-85))
    }

    @Test
    fun `channels of the 2,4, 5 and 6 GHz bands`() {
        assertEquals(1, WifiChannel.fromFrequency(2412))
        assertEquals(14, WifiChannel.fromFrequency(2484))
        assertEquals(36, WifiChannel.fromFrequency(5180))
        assertEquals(1, WifiChannel.fromFrequency(5955))
        assertNull(WifiChannel.fromFrequency(900))
    }

    @Test
    fun `local network needs a gateway that answers`() {
        assertEquals(Passed(), LocalNetworkTest(network, FakeProbe()).run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.NETWORK_NO_GATEWAY)), LocalNetworkTest(network.copy(gateway = null), FakeProbe()).run(notCanceled))
        assertEquals(
            Warning(Finding(FindingKey.NETWORK_GATEWAY_UNREACHABLE, listOf("192.168.0.1"))),
            LocalNetworkTest(network, FakeProbe(reachable = false)).run(notCanceled),
        )
    }

    @Test
    fun `name resolution needs a server that answers quickly`() {
        assertEquals(Passed(), NameResolutionTest(network, FakeProbe()).run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.NETWORK_NO_DNS)), NameResolutionTest(network.copy(dnsServers = emptyList()), FakeProbe()).run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.NETWORK_DNS_FAILED)), NameResolutionTest(network, FakeProbe(dnsMillis = null)).run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.NETWORK_DNS_SLOW, listOf(900L))), NameResolutionTest(network, FakeProbe(dnsMillis = 900)).run(notCanceled))
    }

    @Test
    fun `internet needs the empty answer of the check page`() {
        assertEquals(Passed(), InternetConnectivityTest(FakeProbe(), "https://example.org/generate_204").run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.NETWORK_NO_INTERNET)), InternetConnectivityTest(FakeProbe(http = null), "u").run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.NETWORK_CAPTIVE_PORTAL)), InternetConnectivityTest(FakeProbe(http = HttpResult(302, 50)), "u").run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.NETWORK_HTTPS_SLOW, listOf(2500L))), InternetConnectivityTest(FakeProbe(http = HttpResult(204, 2500)), "u").run(notCanceled))
    }

    @Test
    fun `wifi test warns about a weak signal or an open network`() {
        assertEquals(Passed(), WifiTest(network).run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.NETWORK_NO_WIFI_DETAILS)), WifiTest(network.copy(wifi = null)).run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.NETWORK_WEAK_SIGNAL)), WifiTest(network.copy(wifi = wifi.copy(rssiDbm = -90))).run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.NETWORK_OPEN_WIFI)), WifiTest(network.copy(wifi = wifi.copy(security = WifiSecurity.OPEN))).run(notCanceled))
    }

    @Test
    fun `the suite adds the wifi test only on wifi`() {
        val probe = FakeProbe()

        assertEquals(
            listOf(TestName.NETWORK_LOCAL, TestName.NETWORK_NAME_RESOLUTION, TestName.NETWORK_INTERNET, TestName.NETWORK_WIFI),
            ConnectivitySuite.buildTests(network, probe, "u").map { it.name },
        )
        assertEquals(3, ConnectivitySuite.buildTests(network.copy(kind = NetworkKind.ETHERNET, wifi = null), probe, "u").size)
        assertEquals(emptyList<DiagnosticTest>(), ConnectivitySuite.buildTests(null, probe, "u"))
    }
}
