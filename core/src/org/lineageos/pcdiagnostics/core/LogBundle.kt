package org.lineageos.pcdiagnostics.core

import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One file of the logs zip and what writes its content. */
class LogPart(val fileName: String, val write: (OutputStream) -> Unit)

/** [done] of [total] files written; [fileName] is the one being written, null once all are. */
data class LogProgress(val done: Int, val total: Int, val fileName: String?)

/** The destination itself failed (a USB stick pulled out, a full disk): the save stops. */
class DestinationFailedException(cause: IOException) : IOException(cause)

/** The logs zip: every part best effort, a part that fails keeps its error in its own file. */
object LogBundle {
    const val MIME_TYPE = "application/zip"

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    fun buildFileName(time: LocalDateTime) = "lineage-pc-logs-${STAMP.format(time)}.zip"

    /** Writes [parts] as a zip into [destination] (left open) and returns the files whose part failed. */
    fun write(parts: List<LogPart>, destination: OutputStream, onProgress: (LogProgress) -> Unit = {}): List<String> {
        val zip = ZipOutputStream(destination)
        val failed = parts.mapIndexedNotNull { index, part ->
            onProgress(LogProgress(index, parts.size, part.fileName))
            writeEntry(zip, part)
        }
        zip.finish()
        onProgress(LogProgress(parts.size, parts.size, null))
        return failed
    }

    /** The part's file; returns its name when the part failed. */
    private fun writeEntry(zip: ZipOutputStream, part: LogPart): String? {
        zip.putNextEntry(ZipEntry(part.fileName))
        val entry = EntryStream(zip)
        val error = runCatching { part.write(entry) }.exceptionOrNull()
        error?.let(::rethrowDestinationFailure)
        error?.let { entry.write("\nCould not collect ${part.fileName}:\n${it.stackTraceToString()}".toByteArray()) }
        zip.closeEntry()
        return error?.let { part.fileName }
    }
}

/** A part's failure is written down; the destination's failure ends the save. */
internal fun rethrowDestinationFailure(error: Throwable) {
    (error as? DestinationFailedException)?.let { throw it }
}

/** The zip entry as the part sees it: its failures are the destination's, and closing it is the bundle's job. */
private class EntryStream(private val zip: ZipOutputStream) : OutputStream() {
    override fun write(b: Int) = guard { zip.write(b) }

    override fun write(b: ByteArray, off: Int, len: Int) = guard { zip.write(b, off, len) }

    override fun close() = Unit

    private fun guard(write: () -> Unit) = try {
        write()
    } catch (e: IOException) {
        throw DestinationFailedException(e)
    }
}

class CommandFailedException(message: String) : IOException(message)

/** Runs a command with its output written to [out]; throws [CommandFailedException] when it fails. */
fun interface CommandRunner {
    fun run(command: List<String>, out: OutputStream)
}

/** A real process; its error output goes with its output, so the file shows why it failed. */
object ProcessCommandRunner : CommandRunner {
    override fun run(command: List<String>, out: OutputStream) {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        try {
            process.outputStream.close()
            process.inputStream.use { it.copyTo(out) }
            val exitCode = process.waitFor()
            if (exitCode != 0) throw CommandFailedException("${command.joinToString(" ")} exited with $exitCode")
        } finally {
            process.destroy()
        }
    }
}
