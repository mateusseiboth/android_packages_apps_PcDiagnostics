package org.lineageos.pcdiagnostics.core

/**
 * The rows the app adds to Settings > About. Settings reads each summary by calling the method named in
 * the row's summary URI (AndroidManifest.xml).
 */
enum class HardwareRow(val method: String) {
    CPU("cpu"),
    MEMORY("memory"),
    STORAGE("storage"),
    ;

    companion object {
        fun fromMethod(method: String?): HardwareRow? = entries.firstOrNull { it.method == method }
    }
}
