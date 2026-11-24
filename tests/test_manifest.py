"""Settings > About rows (three tiles whose summaries come from the app's provider) and the runtime
permissions of the audio and camera pages."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
AUTHORITY = "org.lineageos.pcdiagnostics.hardware"
PERMISSION = "org.lineageos.pcdiagnostics.permission.READ_HARDWARE_SUMMARY"
# Order in Settings: lower first, after the device name (1 to 3).
ROWS = {
    ".AboutCpuActivity": ("cpu", "@string/about_cpu_title", "100"),
    ".AboutMemoryActivity": ("memory", "@string/about_memory_title", "110"),
    ".AboutStorageActivity": ("storage", "@string/about_storage_title", "120"),
}


def read_manifest():
    return ET.parse(HERE / "AndroidManifest.xml").getroot()


def meta_data(element):
    return {m.get(f"{ANDROID}name"): m.get(f"{ANDROID}value") or m.get(f"{ANDROID}resource")
            for m in element.findall("meta-data")}


def hardware_row_methods():
    source = (HERE / "core/src/org/lineageos/pcdiagnostics/core/HardwareRow.kt").read_text()
    return set(re.findall(r'^\s+[A-Z]+\("(\w+)"\)', source, re.M))


class AboutRowsTest(unittest.TestCase):
    def setUp(self):
        application = read_manifest().find("application")
        self.aliases = {a.get(f"{ANDROID}name"): a for a in application.findall("activity-alias")}
        self.providers = {p.get(f"{ANDROID}authorities"): p for p in application.findall("provider")}

    def test_each_row_is_a_tile_in_about(self):
        for name, (method, title, order) in ROWS.items():
            alias = self.aliases[name]
            self.assertEqual(alias.get(f"{ANDROID}targetActivity"), ".MainActivity", name)
            self.assertEqual(alias.find("intent-filter/action").get(f"{ANDROID}name"),
                             "com.android.settings.action.IA_SETTINGS", name)
            self.assertEqual(meta_data(alias), {
                "com.android.settings.category": "com.android.settings.category.ia.my_device_info",
                "com.android.settings.group_key": "basic_info_category",
                "com.android.settings.order": order,
                "com.android.settings.title": title,
                "com.android.settings.summary_uri": f"content://{AUTHORITY}/{method}",
            }, name)

    def test_the_summary_methods_are_the_providers(self):
        self.assertEqual({method for method, _, _ in ROWS.values()}, hardware_row_methods())

    def test_the_provider_answers_only_platform_signed_callers(self):
        provider = self.providers[AUTHORITY]
        self.assertEqual(provider.get(f"{ANDROID}name"), ".HardwareSummaryProvider")
        self.assertEqual(provider.get(f"{ANDROID}exported"), "true")
        self.assertEqual(provider.get(f"{ANDROID}readPermission"), PERMISSION)
        declared = {p.get(f"{ANDROID}name"): p.get(f"{ANDROID}protectionLevel") for p in read_manifest().findall("permission")}
        self.assertEqual(declared.get(PERMISSION), "signature")


# Runtime permissions and the hardware they need: the app still installs on a PC without it.
RUNTIME_PERMISSIONS = {
    "android.permission.RECORD_AUDIO": "android.hardware.microphone",
    "android.permission.CAMERA": "android.hardware.camera.any",
}


class RuntimePermissionsTest(unittest.TestCase):
    def setUp(self):
        manifest = read_manifest()
        self.permissions = {p.get(f"{ANDROID}name") for p in manifest.findall("uses-permission")}
        self.features = {f.get(f"{ANDROID}name"): f.get(f"{ANDROID}required") for f in manifest.findall("uses-feature")}

    def test_each_page_asks_for_its_permission(self):
        for permission in RUNTIME_PERMISSIONS:
            self.assertIn(permission, self.permissions)

    def test_the_hardware_is_optional(self):
        for permission, feature in RUNTIME_PERMISSIONS.items():
            self.assertEqual(self.features.get(feature), "false", feature)

    def test_none_is_on_the_privileged_allowlist(self):
        allowlist = ET.parse(HERE / "etc/privapp-permissions-pcdiagnostics.xml").getroot()
        listed = {p.get("name") for p in allowlist.iter("permission")}
        self.assertFalse(listed & set(RUNTIME_PERMISSIONS))


if __name__ == "__main__":
    unittest.main()
