package org.lineageos.pcdiagnostics.core

import java.io.File
import java.io.FileNotFoundException
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.file.Files

/**
 * One line per entry of a sysfs folder: its name, the [attributes] it has (`name=value`, quoted when the value
 * has spaces) and, when [driverLink] is set, the driver bound to it (the name the link points to, or `none`).
 */
class SysfsTable(private val attributes: List<String>, private val driverLink: String? = null) {
    fun describe(folder: File): String {
        val entries = folder.listFiles()?.sortedBy { it.name } ?: throw FileNotFoundException("${folder.path} cannot be listed")
        return entries.map(::describeEntry).ifEmpty { listOf(NONE) }.joinToString("") { "$it\n" }
    }

    private fun describeEntry(entry: File): String {
        val values = attributes.mapNotNull { name -> readValue(File(entry, name))?.let { "$name=${quote(it)}" } }
        val driver = driverLink?.let { "driver=${readDriver(File(entry, it))}" }
        return (listOf(entry.name) + values + listOfNotNull(driver)).joinToString(" ")
    }

    private fun readValue(file: File): String? = runCatching { file.readText().trim() }.getOrNull()

    private fun readDriver(link: File): String = runCatching { Files.readSymbolicLink(link.toPath()).fileName.toString() }.getOrDefault(NONE_DRIVER)

    private fun quote(value: String) = value.takeUnless { WHITESPACE.containsMatchIn(it) } ?: "\"$value\""

    companion object {
        private const val NONE = "(none)"
        private const val NONE_DRIVER = "none"

        val PCI = SysfsTable(listOf("vendor", "device", "subsystem_vendor", "subsystem_device", "class"), driverLink = "driver")

        /** Devices (ids, class, names) and their interfaces (class), each with its driver. */
        val USB = SysfsTable(listOf("idVendor", "idProduct", "bDeviceClass", "bInterfaceClass", "manufacturer", "product"), driverLink = "driver")

        val NET = SysfsTable(listOf("operstate", "address"), driverLink = "device/driver")

        val RFKILL = SysfsTable(listOf("type", "name", "state", "soft", "hard"))
    }
}

/**
 * Where the logs come from: the sysfs and proc roots (`/sys` and `/proc` on the device), the commands, and what
 * the app reads from Android (radios as WifiManager and BluetoothAdapter report them, build fields).
 */
class LogSources(
    val sysfs: File,
    val proc: File,
    val runner: CommandRunner,
    val readRadios: () -> List<Pair<String, String>>,
    val readBuild: () -> List<Pair<String, String>>,
)

/** The files of the logs zip, in order. */
object LogCollectors {
    private val LOGCAT = listOf("logcat", "-d", "-b", "all", "-v", "threadtime")
    private val KERNEL_LOGCAT = listOf("logcat", "-d", "-b", "kernel")
    private val DMESG = listOf("dmesg")

    fun build(sources: LogSources): List<LogPart> = listOf(
        buildCommandPart("logcat.txt", sources.runner, LOGCAT),
        buildCommandPart("kernel.txt", sources.runner, KERNEL_LOGCAT, DMESG),
        buildCommandPart("getprop.txt", sources.runner, listOf("getprop")),
        buildTextPart("modules.txt") { File(sources.proc, "modules").readText() },
        buildTextPart("pci.txt") { SysfsTable.PCI.describe(File(sources.sysfs, "bus/pci/devices")) },
        buildTextPart("usb.txt") { SysfsTable.USB.describe(File(sources.sysfs, "bus/usb/devices")) },
        buildTextPart("network.txt") { buildNetworkReport(sources) },
        buildCommandPart("dumpsys-wifi.txt", sources.runner, listOf("dumpsys", "wifi")),
        buildCommandPart("dumpsys-bluetooth.txt", sources.runner, listOf("dumpsys", "bluetooth_manager")),
        buildTextPart("build.txt") { formatFields(sources.readBuild() + ("/proc/version" to readProcVersion(sources))) },
    )

    private fun readProcVersion(sources: LogSources) = readOrExplain { File(sources.proc, "version").readText() }.trim()

    private fun buildNetworkReport(sources: LogSources) = formatSections(
        listOf(
            "interfaces" to { SysfsTable.NET.describe(File(sources.sysfs, "class/net")) },
            "rfkill" to { SysfsTable.RFKILL.describe(File(sources.sysfs, "class/rfkill")) },
            "android" to { formatFields(sources.readRadios()) },
        ),
    )

    private fun buildTextPart(fileName: String, read: () -> String) = LogPart(fileName) { it.write(read().toByteArray()) }

    /** Runs the [commands] in turn until one prints something; the ones that did not are noted in the file. */
    private fun buildCommandPart(fileName: String, runner: CommandRunner, vararg commands: List<String>) = LogPart(fileName) { out ->
        val worked = commands.any { command -> runCommand(runner, command, out) }
        if (!worked) throw CommandFailedException("no command printed anything")
    }

    private fun runCommand(runner: CommandRunner, command: List<String>, out: OutputStream): Boolean {
        val counted = CountingStream(out)
        val error = runCatching { runner.run(command, counted) }.exceptionOrNull()
        error?.let(::rethrowDestinationFailure)
        val note = error?.let { "failed: ${it.message}" } ?: "printed nothing".takeIf { counted.count == 0L }
        note?.let { out.write("# ${command.joinToString(" ")} $it\n".toByteArray()) }
        return note == null
    }
}

/** "key: value" lines. */
internal fun formatFields(fields: List<Pair<String, String>>) = fields.joinToString("") { (key, value) -> "$key: $value\n" }

/** "[title]" sections, a blank line apart; a section that cannot be read says why and the others stay. */
internal fun formatSections(sections: List<Pair<String, () -> String>>) =
    sections.joinToString("\n") { (title, read) -> "[$title]\n${readOrExplain(read)}" }

private fun readOrExplain(read: () -> String): String = runCatching(read).getOrElse { "Could not read: $it\n" }

private class CountingStream(out: OutputStream) : FilterOutputStream(out) {
    var count = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        count += 1
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }
}
