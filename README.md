# PcDiagnostics

Hardware checks for LineageOS for PCs, in the style of ChromeOS Diagnostics: live CPU, memory, storage,
battery and network readings, and test suites that say what is wrong and what to do. Written from scratch
in Kotlin and Jetpack Compose (Material 3, wallpaper colors).

## What it shows

A navigation drawer (kept open on wide windows, behind a menu button on narrow ones) with:

- **Overview**: the device model and Android version, then one card each for
  - **Battery** (only when the PC has one): level, status (charging, on battery, fully charged), time to
    empty or to full, design capacity, health (full charge against design), cycle count and current.
    Test: charge test when plugged in, discharge test on battery (level read, 30 s wait, level read again).
  - **CPU usage**: model and threads, a 60-second user/system chart, current usage, temperature and speed.
    Tests: stress (every core runs the same computation for 20 s; every result must match), cache
    (cache-sized buffers written and read back in line order), floating point (identities and a bit-exact
    fingerprint per round) and prime search (sieve against the known prime counts).
  - **Memory**: used of total, with a bar. Test: patterns (zeros, ones, alternating bits, walking ones,
    addresses, pseudo-random) over a quarter of the free memory, within the app's heap.
  - **Storage**: used of total on `/data`. Test: writes 256 MB in the app's cache, syncs it, reads it back,
    compares and removes it; reports write and read speed and warns below 10% free.
- **Connectivity**: the active network (Wi-Fi or Ethernet): SSID, BSSID, signal, security, channel, IP
  addresses, gateway and name servers. Tests: local network (gateway answers), name resolution (server
  present, answers, under 400 ms), internet (the captive portal check page answers 204, under 1 s) and,
  on Wi-Fi, the link (signal not weak, network not open). A button opens the network settings.
- **Task Manager**: opens PcTaskManager when it is installed.

Every test reports Passed, Warning or Failed with a sentence; "See report" lists them. "Stop test" cancels
the suite. Settings gets two tiles: **System > Diagnostics** and **Battery > Battery health**.

## Where the numbers come from

`/proc/stat`, `/proc/cpuinfo`, `/proc/meminfo`, `/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq`,
temperature from `/sys/class/thermal` and `/sys/class/hwmon` (package sensors first: `x86_pkg_temp`,
`coretemp`, `k10temp`, `zenpower`), the battery from `/sys/class/power_supply/*/uevent` (charge or energy
units), storage from `StatFs`, and the network from `ConnectivityManager` and `WifiManager`. The captive
portal check page is the system's own (`captive_portal_https_url`, AOSP default otherwise).

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` (normal) and `NETWORK_SETTINGS` (signature,
granted by the platform certificate): with it `WifiManager` returns the SSID and BSSID without location
access. None is a privileged permission, so `etc/privapp-permissions-pcdiagnostics.xml` lists none.
`largeHeap` gives the memory test room.

## Code

- `core/`: plain Kotlin, no Android. `SuiteRunner`/`SuiteState` (tests in order, worst outcome wins,
  cancel), the checks (`StressTest`, `CacheTest`, `FloatingPointAccuracyTest`, `PrimeSearchTest`,
  `MemoryTest`, `StorageTest`, `BatteryRateTest`, `LocalNetworkTest`, `NameResolutionTest`,
  `InternetConnectivityTest`, `WifiTest`), `ConnectivitySuite`, `BatteryInfo`, readers and `CpuMonitor`.
- `src/`: `DiagnosticsViewModel` (readings once a second while visible, one `SuiteController` per card),
  `SystemSource`, `NetworkReader`/`AndroidNetworkProbe`, and the Compose UI in `ui/`.

## Tests

```sh
core/run_tests.sh                        # JUnit tests of core/, with external/kotlinc and JUnit from prebuilts
python3 -m unittest discover -s tests    # UI strings: both languages, same placeholders, no em dash
tools/typecheck.sh                       # compiles core + Compose UI against the prebuilts (needs a previous build's out/)
```

In Soong: `PcDiagnosticsCoreTests` (`java_test_host`, `atest`-able).

## Build integration

- local manifest: `<project path="packages/apps/PcDiagnostics" name="android_packages_apps_PcDiagnostics" remote="pc" revision="lineage-24.0" />`
- `device.mk`: `PRODUCT_PACKAGES += PcDiagnostics` (pulls `privapp-permissions-pcdiagnostics.xml`)

## Not done yet

- Not built or run on a device yet: only the host tests and the Compose typecheck have run.
- Input (keyboard, touchpad, touchscreen), audio (speakers, microphone) and camera pages.
- Settings "About" entries for CPU, RAM and storage information.
- "Learn more" links and a help page.
- SELinux: the device is permissive. Enforcing needs `system_app`/`platform_app` read access to
  `sysfs_thermal`, hwmon and power supply nodes; the rules are not written.
- The storage read speed comes from the page cache right after writing, so it measures memory more than
  the disk; the check is about data coming back intact.
