package org.lineageos.pcdiagnostics.ui

import org.lineageos.pcdiagnostics.R
import org.lineageos.pcdiagnostics.core.BatteryStatus
import org.lineageos.pcdiagnostics.core.FindingKey
import org.lineageos.pcdiagnostics.core.NetworkKind
import org.lineageos.pcdiagnostics.core.RateKind
import org.lineageos.pcdiagnostics.core.SignalLevel
import org.lineageos.pcdiagnostics.core.SuiteStatus
import org.lineageos.pcdiagnostics.core.TestName
import org.lineageos.pcdiagnostics.core.WifiSecurity

/** The text of every name and finding the core reports. */
object Texts {
    val TEST_NAMES = mapOf(
        TestName.CPU_STRESS to R.string.test_cpu_stress,
        TestName.CPU_CACHE to R.string.test_cpu_cache,
        TestName.CPU_FLOATING_POINT to R.string.test_cpu_floating_point,
        TestName.CPU_PRIME_SEARCH to R.string.test_cpu_prime_search,
        TestName.MEMORY to R.string.test_memory,
        TestName.STORAGE to R.string.test_storage,
        TestName.BATTERY_CHARGE to R.string.test_battery_charge,
        TestName.BATTERY_DISCHARGE to R.string.test_battery_discharge,
        TestName.NETWORK_LOCAL to R.string.test_network_local,
        TestName.NETWORK_NAME_RESOLUTION to R.string.test_network_name_resolution,
        TestName.NETWORK_INTERNET to R.string.test_network_internet,
        TestName.NETWORK_WIFI to R.string.test_network_wifi,
    )

    val FINDINGS = mapOf(
        FindingKey.TEST_CRASHED to R.string.finding_test_crashed,
        FindingKey.CPU_STRESS_MISMATCH to R.string.finding_cpu_stress_mismatch,
        FindingKey.CPU_CACHE_MISMATCH to R.string.finding_cpu_cache_mismatch,
        FindingKey.CPU_FLOAT_MISMATCH to R.string.finding_cpu_float_mismatch,
        FindingKey.CPU_PRIME_MISMATCH to R.string.finding_cpu_prime_mismatch,
        FindingKey.MEMORY_MISMATCH to R.string.finding_memory_mismatch,
        FindingKey.STORAGE_NO_SPACE to R.string.finding_storage_no_space,
        FindingKey.STORAGE_LOW_SPACE to R.string.finding_storage_low_space,
        FindingKey.STORAGE_MISMATCH to R.string.finding_storage_mismatch,
        FindingKey.STORAGE_SPEED to R.string.finding_storage_speed,
        FindingKey.BATTERY_UNAVAILABLE to R.string.finding_battery_unavailable,
        FindingKey.BATTERY_FULL to R.string.finding_battery_full,
        FindingKey.BATTERY_STATUS_CHANGED to R.string.finding_battery_status_changed,
        FindingKey.BATTERY_CHARGE_DECREASED to R.string.finding_battery_charge_decreased,
        FindingKey.BATTERY_CHARGE_INCREASED to R.string.finding_battery_charge_increased,
        FindingKey.BATTERY_DISCHARGE_INCREASED to R.string.finding_battery_discharge_increased,
        FindingKey.BATTERY_DISCHARGE_DECREASED to R.string.finding_battery_discharge_decreased,
        FindingKey.NETWORK_NO_GATEWAY to R.string.finding_network_no_gateway,
        FindingKey.NETWORK_GATEWAY_UNREACHABLE to R.string.finding_network_gateway_unreachable,
        FindingKey.NETWORK_NO_DNS to R.string.finding_network_no_dns,
        FindingKey.NETWORK_DNS_FAILED to R.string.finding_network_dns_failed,
        FindingKey.NETWORK_DNS_SLOW to R.string.finding_network_dns_slow,
        FindingKey.NETWORK_NO_INTERNET to R.string.finding_network_no_internet,
        FindingKey.NETWORK_HTTPS_SLOW to R.string.finding_network_https_slow,
        FindingKey.NETWORK_CAPTIVE_PORTAL to R.string.finding_network_captive_portal,
        FindingKey.NETWORK_NO_WIFI_DETAILS to R.string.finding_network_no_wifi_details,
        FindingKey.NETWORK_WEAK_SIGNAL to R.string.finding_network_weak_signal,
        FindingKey.NETWORK_OPEN_WIFI to R.string.finding_network_open_wifi,
    )

    val SUITE_STATUSES = mapOf(
        SuiteStatus.NOT_RUN to R.string.status_not_run,
        SuiteStatus.RUNNING to R.string.status_running,
        SuiteStatus.PASSED to R.string.status_passed,
        SuiteStatus.WARNING to R.string.status_warning,
        SuiteStatus.FAILED to R.string.status_failed,
        SuiteStatus.CANCELED to R.string.status_canceled,
    )

    val BATTERY_STATUSES = mapOf(
        BatteryStatus.CHARGING to R.string.battery_status_charging,
        BatteryStatus.DISCHARGING to R.string.battery_status_discharging,
        BatteryStatus.NOT_CHARGING to R.string.battery_status_not_charging,
        BatteryStatus.FULL to R.string.battery_status_full,
        BatteryStatus.UNKNOWN to R.string.battery_status_unknown,
    )

    /** Time left to empty while on battery, to full while charging. */
    val BATTERY_TIMES = mapOf(
        BatteryStatus.DISCHARGING to R.string.battery_time_left,
        BatteryStatus.CHARGING to R.string.battery_time_until_full,
    )

    val RATE_RUN_LABELS = mapOf(RateKind.CHARGE to R.string.run_battery_charge, RateKind.DISCHARGE to R.string.run_battery_discharge)

    val NETWORK_KINDS = mapOf(
        NetworkKind.WIFI to R.string.network_wifi,
        NetworkKind.ETHERNET to R.string.network_ethernet,
        NetworkKind.OTHER to R.string.network_other,
    )

    val SIGNAL_LEVELS = mapOf(
        SignalLevel.EXCELLENT to R.string.signal_excellent,
        SignalLevel.GOOD to R.string.signal_good,
        SignalLevel.AVERAGE to R.string.signal_average,
        SignalLevel.WEAK to R.string.signal_weak,
    )

    val SECURITY = mapOf(
        WifiSecurity.OPEN to R.string.security_open,
        WifiSecurity.SECURED to R.string.security_secured,
        WifiSecurity.UNKNOWN to R.string.security_unknown,
    )
}
