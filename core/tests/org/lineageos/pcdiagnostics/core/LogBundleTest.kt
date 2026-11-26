package org.lineageos.pcdiagnostics.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.Random
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The zip's files, in order, with their text. */
fun readZip(bytes: ByteArray): List<Pair<String, String>> = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
    generateSequence { zip.nextEntry }.map { entry -> entry.name to zip.readBytes().decodeToString() }.toList()
}

private fun buildTextPart(fileName: String, text: String) = LogPart(fileName) { it.write(text.toByteArray()) }

/** A USB stick pulled out after [limit] bytes. */
private class BrokenDestination(private val limit: Int) : OutputStream() {
    private var written = 0

    override fun write(b: Int) {
        written += 1
        if (written > limit) throw IOException("device removed")
    }
}

class LogBundleTest {
    @Test
    fun `the zip holds one file per part, in order`() {
        val zip = ByteArrayOutputStream()

        val failed = LogBundle.write(listOf(buildTextPart("a.txt", "alpha"), buildTextPart("b.txt", "beta")), zip)

        assertEquals(listOf("a.txt" to "alpha", "b.txt" to "beta"), readZip(zip.toByteArray()))
        assertEquals(emptyList<String>(), failed)
    }

    @Test
    fun `a failing part keeps its error in its own file and the others are written`() {
        val broken = LogPart("b.txt") { out ->
            out.write("partial".toByteArray())
            throw IllegalStateException("no access")
        }
        val zip = ByteArrayOutputStream()

        val failed = LogBundle.write(listOf(buildTextPart("a.txt", "alpha"), broken, buildTextPart("c.txt", "gamma")), zip)

        val files = readZip(zip.toByteArray()).toMap()
        assertEquals(listOf("a.txt", "b.txt", "c.txt"), files.keys.toList())
        assertTrue(files.getValue("b.txt"), files.getValue("b.txt").startsWith("partial"))
        assertTrue(files.getValue("b.txt"), files.getValue("b.txt").contains("Could not collect b.txt"))
        assertTrue(files.getValue("b.txt"), files.getValue("b.txt").contains("no access"))
        assertEquals("gamma", files.getValue("c.txt"))
        assertEquals(listOf("b.txt"), failed)
    }

    @Test
    fun `progress counts every part`() {
        val progress = mutableListOf<LogProgress>()

        LogBundle.write(listOf(buildTextPart("a.txt", "alpha"), buildTextPart("b.txt", "beta")), ByteArrayOutputStream(), progress::add)

        assertEquals(listOf(LogProgress(0, 2, "a.txt"), LogProgress(1, 2, "b.txt"), LogProgress(2, 2, null)), progress)
    }

    @Test
    fun `a destination that fails stops the save`() {
        // Random bytes do not compress, so the zip reaches the destination while the part is written.
        val noise = ByteArray(1 shl 20).also(Random(1)::nextBytes)
        val after = mutableListOf<String>()
        val parts = listOf(LogPart("big.bin") { it.write(noise) }, LogPart("next.txt") { after += "next.txt" })

        val error = runCatching { LogBundle.write(parts, BrokenDestination(limit = 4096)) }.exceptionOrNull()

        assertTrue(error.toString(), error is IOException)
        assertEquals(emptyList<String>(), after)
    }

    @Test
    fun `the file name carries the date and time`() {
        assertEquals("lineage-pc-logs-20261126-0905.zip", LogBundle.buildFileName(LocalDateTime.of(2026, 11, 26, 9, 5)))
    }

    @Test
    fun `a command's output and errors go to its file`() {
        val out = ByteArrayOutputStream()

        ProcessCommandRunner.run(listOf("sh", "-c", "echo out; echo err >&2"), out)

        assertEquals("out\nerr\n", out.toString())
    }

    @Test
    fun `a command that fails throws`() {
        val error = runCatching { ProcessCommandRunner.run(listOf("sh", "-c", "exit 3"), ByteArrayOutputStream()) }.exceptionOrNull()

        assertTrue(error.toString(), error is CommandFailedException)
        assertTrue(error.toString(), error?.message.orEmpty().contains("exited with 3"))
    }
}
