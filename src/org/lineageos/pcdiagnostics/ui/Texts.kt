package org.lineageos.pcdiagnostics.ui

import org.lineageos.pcdiagnostics.R
import org.lineageos.pcdiagnostics.core.AudioDeviceKind
import org.lineageos.pcdiagnostics.core.BatteryStatus
import org.lineageos.pcdiagnostics.core.CameraFacing
import org.lineageos.pcdiagnostics.core.FindingKey
import org.lineageos.pcdiagnostics.core.InputKind
import org.lineageos.pcdiagnostics.core.KeyboardLayout
import org.lineageos.pcdiagnostics.core.NetworkKind
import org.lineageos.pcdiagnostics.core.PointerButton
import org.lineageos.pcdiagnostics.core.RateKind
import org.lineageos.pcdiagnostics.core.SignalLevel
import org.lineageos.pcdiagnostics.core.SpeakerChannel
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
        TestName.INPUT_DEVICES to R.string.test_input_devices,
        TestName.INPUT_KEYBOARD_KEYS to R.string.test_input_keyboard_keys,
        TestName.AUDIO_SPEAKERS to R.string.test_audio_speakers,
        TestName.AUDIO_MICROPHONE to R.string.test_audio_microphone,
        TestName.CAMERA to R.string.test_camera,
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
        FindingKey.INPUT_NO_KEYBOARD to R.string.finding_input_no_keyboard,
        FindingKey.INPUT_NO_POINTER to R.string.finding_input_no_pointer,
        FindingKey.INPUT_DEVICES_FOUND to R.string.finding_input_devices_found,
        FindingKey.INPUT_KEYS_NOT_TESTED to R.string.finding_input_keys_not_tested,
        FindingKey.INPUT_KEYS_MISSED to R.string.finding_input_keys_missed,
        FindingKey.INPUT_KEYS_ALL_PRESSED to R.string.finding_input_keys_all_pressed,
        FindingKey.AUDIO_NO_OUTPUT to R.string.finding_audio_no_output,
        FindingKey.AUDIO_MUTED to R.string.finding_audio_muted,
        FindingKey.AUDIO_PLAYBACK_FAILED to R.string.finding_audio_playback_failed,
        FindingKey.AUDIO_CHANNELS_PLAYED to R.string.finding_audio_channels_played,
        FindingKey.AUDIO_NO_PERMISSION to R.string.finding_audio_no_permission,
        FindingKey.AUDIO_NO_INPUT to R.string.finding_audio_no_input,
        FindingKey.AUDIO_RECORD_FAILED to R.string.finding_audio_record_failed,
        FindingKey.AUDIO_SILENT to R.string.finding_audio_silent,
        FindingKey.AUDIO_RECORDED_LEVEL to R.string.finding_audio_recorded_level,
        FindingKey.CAMERA_NONE to R.string.finding_camera_none,
        FindingKey.CAMERA_NO_PERMISSION to R.string.finding_camera_no_permission,
        FindingKey.CAMERA_NO_FRAMES to R.string.finding_camera_no_frames,
        FindingKey.CAMERA_DARK to R.string.finding_camera_dark,
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

    val INPUT_KINDS = mapOf(
        InputKind.KEYBOARD to R.string.input_keyboard,
        InputKind.TOUCHPAD to R.string.input_touchpad,
        InputKind.TOUCHSCREEN to R.string.input_touchscreen,
        InputKind.MOUSE to R.string.input_mouse,
        InputKind.OTHER to R.string.input_other,
    )

    /** Built-in or external, by whether the device is external. */
    val CONNECTIONS = mapOf(false to R.string.input_built_in, true to R.string.input_external)

    val KEYBOARD_LAYOUTS = mapOf(
        KeyboardLayout.US_ANSI to R.string.layout_us_ansi,
        KeyboardLayout.ISO to R.string.layout_iso,
        KeyboardLayout.ABNT2 to R.string.layout_abnt2,
    )

    val POINTER_BUTTONS = mapOf(
        PointerButton.PRIMARY to R.string.button_primary,
        PointerButton.SECONDARY to R.string.button_secondary,
        PointerButton.TERTIARY to R.string.button_tertiary,
    )

    val AUDIO_KINDS = mapOf(
        AudioDeviceKind.BUILT_IN to R.string.audio_kind_built_in,
        AudioDeviceKind.HEADPHONES to R.string.audio_kind_headphones,
        AudioDeviceKind.USB to R.string.audio_kind_usb,
        AudioDeviceKind.HDMI to R.string.audio_kind_hdmi,
        AudioDeviceKind.BLUETOOTH to R.string.audio_kind_bluetooth,
        AudioDeviceKind.LINE to R.string.audio_kind_line,
        AudioDeviceKind.OTHER to R.string.audio_kind_other,
    )

    val SPEAKER_CHANNELS = mapOf(
        SpeakerChannel.FRONT_LEFT to R.string.channel_front_left,
        SpeakerChannel.FRONT_RIGHT to R.string.channel_front_right,
        SpeakerChannel.FRONT_CENTER to R.string.channel_front_center,
        SpeakerChannel.LOW_FREQUENCY to R.string.channel_low_frequency,
        SpeakerChannel.BACK_LEFT to R.string.channel_back_left,
        SpeakerChannel.BACK_RIGHT to R.string.channel_back_right,
        SpeakerChannel.FRONT_LEFT_OF_CENTER to R.string.channel_front_left_of_center,
        SpeakerChannel.FRONT_RIGHT_OF_CENTER to R.string.channel_front_right_of_center,
        SpeakerChannel.BACK_CENTER to R.string.channel_back_center,
        SpeakerChannel.SIDE_LEFT to R.string.channel_side_left,
        SpeakerChannel.SIDE_RIGHT to R.string.channel_side_right,
    )

    val CAMERA_FACINGS = mapOf(
        CameraFacing.FRONT to R.string.camera_front,
        CameraFacing.BACK to R.string.camera_back,
        CameraFacing.EXTERNAL to R.string.camera_external,
    )
}
