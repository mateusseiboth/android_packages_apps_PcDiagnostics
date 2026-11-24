package org.lineageos.pcdiagnostics

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.format.Formatter
import org.lineageos.pcdiagnostics.core.HardwareRow

/**
 * Summaries of the Settings > About rows (AndroidManifest.xml): Settings calls the method named in a
 * row's summary URI and shows the text under [SUMMARY_KEY].
 */
class HardwareSummaryProvider : ContentProvider() {
    private val system by lazy { SystemSource(Environment.getDataDirectory()) }

    private val summaries: Map<HardwareRow, (Context) -> String> = mapOf(
        HardwareRow.CPU to ::summarizeCpu,
        HardwareRow.MEMORY to ::summarizeMemory,
        HardwareRow.STORAGE to ::summarizeStorage,
    )

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        val summarize = HardwareRow.fromMethod(method)?.let(summaries::getValue) ?: return null
        return Bundle().apply { putString(SUMMARY_KEY, summarize(context)) }
    }

    private fun summarizeCpu(context: Context): String = system.readCpuModel().let { cpu ->
        context.getString(R.string.about_cpu_summary, cpu.name, cpu.cores, cpu.threads)
    }

    private fun summarizeMemory(context: Context): String =
        context.getString(R.string.about_memory_summary, system.readMemory().totalGigabytesRoundedUp)

    private fun summarizeStorage(context: Context): String = system.readStorage().let { storage ->
        context.getString(
            R.string.about_storage_summary,
            Formatter.formatShortFileSize(context, storage.usedBytes),
            Formatter.formatShortFileSize(context, storage.totalBytes),
        )
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    private companion object {
        /** TileUtils.META_DATA_PREFERENCE_SUMMARY in SettingsLib. */
        const val SUMMARY_KEY = "com.android.settings.summary"
    }
}
