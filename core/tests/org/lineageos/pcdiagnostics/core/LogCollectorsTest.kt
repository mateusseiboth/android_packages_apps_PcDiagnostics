package org.lineageos.pcdiagnostics.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fake /sys (or /proc) in a temporary folder. */
private class FakeTree {
    val root: File = Files.createTempDirectory("sysfs").toFile()

    fun writeFile(path: String, text: String) = File(root, path).apply { parentFile.mkdirs() }.writeText(text)

    /** A device's `driver` link, pointing at the driver's folder as the kernel does. */
    fun linkDriver(path: String, driver: String) {
        val link = File(root, path).apply { parentFile.mkdirs() }.toPath()
        Files.createSymbolicLink(link, File(root, "bus/drivers/$driver").toPath())
    }

    fun makeFolder(path: String) = File(root, path).mkdirs()
}

/** Prints the command's name, except for the commands in [failing], which fail. */
private class RecordingRunner(private val failing: Set<List<String>> = emptySet()) : CommandRunner {
    val commands = mutableListOf<List<String>>()

    override fun run(command: List<String>, out: OutputStream) {
        commands += command
        if (command in failing) throw CommandFailedException("${command.joinToString(" ")} exited with 1")
        out.write("output of ${command.first()}\n".toByteArray())
    }
}

private fun readPart(part: LogPart): String = ByteArrayOutputStream().also(part.write).toString()

private fun buildSources(
    sysfs: FakeTree = FakeTree(),
    proc: FakeTree = FakeTree(),
    runner: CommandRunner = RecordingRunner(),
    readRadios: () -> List<Pair<String, String>> = { emptyList() },
    readBuild: () -> List<Pair<String, String>> = { emptyList() },
) = LogSources(sysfs.root, proc.root, runner, readRadios, readBuild)

private fun writeZip(sources: LogSources): Map<String, String> {
    val zip = ByteArrayOutputStream()
    LogBundle.write(LogCollectors.build(sources), zip)
    return readZip(zip.toByteArray()).toMap()
}

class LogCollectorsTest {
    @Test
    fun `pci devices with ids, class and the bound driver`() {
        val sysfs = FakeTree()
        sysfs.writeFile("bus/pci/devices/0000:00:02.0/vendor", "0x8086\n")
        sysfs.writeFile("bus/pci/devices/0000:00:02.0/device", "0x9a49\n")
        sysfs.writeFile("bus/pci/devices/0000:00:02.0/class", "0x030000\n")
        sysfs.linkDriver("bus/pci/devices/0000:00:02.0/driver", "i915")
        sysfs.writeFile("bus/pci/devices/0000:00:14.3/vendor", "0x8086\n")
        sysfs.writeFile("bus/pci/devices/0000:00:14.3/device", "0x51f0\n")
        sysfs.writeFile("bus/pci/devices/0000:00:14.3/subsystem_vendor", "0x8086\n")
        sysfs.writeFile("bus/pci/devices/0000:00:14.3/subsystem_device", "0x0094\n")
        sysfs.writeFile("bus/pci/devices/0000:00:14.3/class", "0x028000\n")

        val text = SysfsTable.PCI.describe(File(sysfs.root, "bus/pci/devices"))

        assertEquals(
            "0000:00:02.0 vendor=0x8086 device=0x9a49 class=0x030000 driver=i915\n" +
                "0000:00:14.3 vendor=0x8086 device=0x51f0 subsystem_vendor=0x8086 subsystem_device=0x0094 class=0x028000 driver=none\n",
            text,
        )
    }

    @Test
    fun `usb devices and their interfaces`() {
        val sysfs = FakeTree()
        sysfs.writeFile("bus/usb/devices/1-1/idVendor", "046d\n")
        sysfs.writeFile("bus/usb/devices/1-1/idProduct", "c52b\n")
        sysfs.writeFile("bus/usb/devices/1-1/bDeviceClass", "00\n")
        sysfs.writeFile("bus/usb/devices/1-1/manufacturer", "Logitech\n")
        sysfs.writeFile("bus/usb/devices/1-1/product", "USB Receiver\n")
        sysfs.linkDriver("bus/usb/devices/1-1/driver", "usb")
        sysfs.writeFile("bus/usb/devices/1-1:1.0/bInterfaceClass", "03\n")
        sysfs.linkDriver("bus/usb/devices/1-1:1.0/driver", "usbhid")

        val text = SysfsTable.USB.describe(File(sysfs.root, "bus/usb/devices"))

        assertEquals(
            "1-1 idVendor=046d idProduct=c52b bDeviceClass=00 manufacturer=Logitech product=\"USB Receiver\" driver=usb\n" +
                "1-1:1.0 bInterfaceClass=03 driver=usbhid\n",
            text,
        )
    }

    @Test
    fun `an empty bus says so`() {
        val sysfs = FakeTree().apply { makeFolder("bus/usb/devices") }

        assertEquals("(none)\n", SysfsTable.USB.describe(File(sysfs.root, "bus/usb/devices")))
    }

    @Test
    fun `network report lists interfaces, rfkill and what Android reports`() {
        val sysfs = FakeTree()
        sysfs.writeFile("class/net/lo/operstate", "unknown\n")
        sysfs.writeFile("class/net/lo/address", "00:00:00:00:00:00\n")
        sysfs.writeFile("class/net/wlan0/operstate", "down\n")
        sysfs.writeFile("class/net/wlan0/address", "a0:b1:c2:d3:e4:f5\n")
        sysfs.linkDriver("class/net/wlan0/device/driver", "iwlwifi")
        sysfs.writeFile("class/rfkill/rfkill0/type", "wlan\n")
        sysfs.writeFile("class/rfkill/rfkill0/name", "phy0\n")
        sysfs.writeFile("class/rfkill/rfkill0/state", "0\n")
        sysfs.writeFile("class/rfkill/rfkill0/soft", "1\n")
        sysfs.writeFile("class/rfkill/rfkill0/hard", "0\n")
        val sources = buildSources(sysfs, readRadios = { listOf("wifi enabled" to "false", "bluetooth state" to "off") })

        val text = writeZip(sources).getValue("network.txt")

        assertEquals(
            "[interfaces]\n" +
                "lo operstate=unknown address=00:00:00:00:00:00 driver=none\n" +
                "wlan0 operstate=down address=a0:b1:c2:d3:e4:f5 driver=iwlwifi\n" +
                "\n[rfkill]\n" +
                "rfkill0 type=wlan name=phy0 state=0 soft=1 hard=0\n" +
                "\n[android]\n" +
                "wifi enabled: false\n" +
                "bluetooth state: off\n",
            text,
        )
    }

    @Test
    fun `a network section that cannot be read says why and the others stay`() {
        val sysfs = FakeTree()
        sysfs.writeFile("class/net/eth0/operstate", "up\n")
        val sources = buildSources(sysfs, readRadios = { throw SecurityException("no bluetooth permission") })

        val text = writeZip(sources).getValue("network.txt")

        assertTrue(text, text.startsWith("[interfaces]\neth0 operstate=up driver=none\n"))
        assertTrue(text, text.contains("[rfkill]\nCould not read: "))
        assertTrue(text, text.contains("[android]\nCould not read: java.lang.SecurityException: no bluetooth permission"))
    }

    @Test
    fun `the zip has every collector, in order`() {
        val names = LogCollectors.build(buildSources()).map { it.fileName }

        assertEquals(
            listOf(
                "logcat.txt", "kernel.txt", "getprop.txt", "modules.txt", "pci.txt", "usb.txt", "network.txt",
                "dumpsys-wifi.txt", "dumpsys-bluetooth.txt", "build.txt",
            ),
            names,
        )
    }

    @Test
    fun `the collectors run logcat, getprop and dumpsys`() {
        val runner = RecordingRunner()

        val files = writeZip(buildSources(runner = runner))

        assertEquals(
            listOf(
                listOf("logcat", "-d", "-b", "all", "-v", "threadtime"),
                listOf("logcat", "-d", "-b", "kernel"),
                listOf("getprop"),
                listOf("dumpsys", "wifi"),
                listOf("dumpsys", "bluetooth_manager"),
            ),
            runner.commands,
        )
        assertEquals("output of logcat\n", files.getValue("logcat.txt"))
        assertEquals("output of dumpsys\n", files.getValue("dumpsys-wifi.txt"))
    }

    @Test
    fun `the kernel log falls back to dmesg`() {
        val runner = RecordingRunner(failing = setOf(listOf("logcat", "-d", "-b", "kernel")))

        val text = writeZip(buildSources(runner = runner)).getValue("kernel.txt")

        assertEquals("# logcat -d -b kernel failed: logcat -d -b kernel exited with 1\noutput of dmesg\n", text)
    }

    @Test
    fun `a command that prints nothing is a failure`() {
        val silent = CommandRunner { _, _ -> }

        val text = writeZip(buildSources(runner = silent)).getValue("getprop.txt")

        assertTrue(text, text.startsWith("# getprop printed nothing\n"))
        assertTrue(text, text.contains("Could not collect getprop.txt"))
    }

    @Test
    fun `a missing folder is an error in its own file`() {
        val files = writeZip(buildSources())

        assertTrue(files.getValue("pci.txt"), files.getValue("pci.txt").startsWith("\nCould not collect pci.txt"))
        assertTrue(files.getValue("modules.txt"), files.getValue("modules.txt").startsWith("\nCould not collect modules.txt"))
        assertEquals("output of logcat\n", files.getValue("logcat.txt"))
    }

    @Test
    fun `modules and build come from proc and the app`() {
        val proc = FakeTree()
        proc.writeFile("modules", "iwlwifi 438272 0 - Live 0x0000000000000000\n")
        proc.writeFile("version", "Linux version 6.12.0 (builder@lineage)\n")
        val sources = buildSources(proc = proc, readBuild = { listOf("fingerprint" to "lineage/pc_x86_64/pc:17", "kernel" to "6.12.0") })

        val parts = LogCollectors.build(sources).associateBy { it.fileName }

        assertEquals("iwlwifi 438272 0 - Live 0x0000000000000000\n", readPart(parts.getValue("modules.txt")))
        assertEquals(
            "fingerprint: lineage/pc_x86_64/pc:17\nkernel: 6.12.0\n/proc/version: Linux version 6.12.0 (builder@lineage)\n",
            readPart(parts.getValue("build.txt")),
        )
    }
}
