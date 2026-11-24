package org.lineageos.pcdiagnostics

import android.app.Application
import android.content.Intent
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.lineageos.pcdiagnostics.core.ArrayCells
import org.lineageos.pcdiagnostics.core.BatteryInfo
import org.lineageos.pcdiagnostics.core.BatteryRateTest
import org.lineageos.pcdiagnostics.core.BatterySuite
import org.lineageos.pcdiagnostics.core.CacheTest
import org.lineageos.pcdiagnostics.core.ConnectivitySuite
import org.lineageos.pcdiagnostics.core.CpuModel
import org.lineageos.pcdiagnostics.core.CpuMonitor
import org.lineageos.pcdiagnostics.core.CpuUsage
import org.lineageos.pcdiagnostics.core.DiagnosticTest
import org.lineageos.pcdiagnostics.core.FloatingPointAccuracyTest
import org.lineageos.pcdiagnostics.core.MemoryCells
import org.lineageos.pcdiagnostics.core.MemorySummary
import org.lineageos.pcdiagnostics.core.MemoryTest
import org.lineageos.pcdiagnostics.core.NetworkSnapshot
import org.lineageos.pcdiagnostics.core.PrimeSearchTest
import org.lineageos.pcdiagnostics.core.StorageSpace
import org.lineageos.pcdiagnostics.core.StorageTest
import org.lineageos.pcdiagnostics.core.StressTest

enum class SuiteKind { CPU, MEMORY, STORAGE, BATTERY, CONNECTIVITY }

enum class Page { OVERVIEW, CONNECTIVITY }

/** What the cards show, read once a second while the screen is visible. */
data class LiveReadings(
    val cpuModel: CpuModel = CpuModel("", 1),
    val cpuUsage: CpuUsage = CpuUsage.ZERO,
    val cpuHistory: List<CpuUsage> = emptyList(),
    val cpuSpeedKhz: Long? = null,
    val cpuMilliCelsius: Int? = null,
    val memory: MemorySummary = MemorySummary(0, 0),
    val storage: StorageSpace = StorageSpace(0, 0),
    val battery: BatteryInfo? = null,
    val network: NetworkSnapshot? = null,
)

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    private val system = SystemSource(Environment.getDataDirectory())
    private val networkReader = NetworkReader(application)
    private val cpuMonitor = CpuMonitor()

    val readings: StateFlow<LiveReadings> = flow {
        val model = system.readCpuModel()
        while (true) {
            emit(readOnce(model))
            delay(REFRESH_MILLIS)
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LiveReadings())

    private val _page = MutableStateFlow(Page.OVERVIEW)
    val page: StateFlow<Page> = _page.asStateFlow()

    val suites: Map<SuiteKind, SuiteController> = mapOf(
        SuiteKind.CPU to buildController { listOf(StressTest(), CacheTest(), FloatingPointAccuracyTest(), PrimeSearchTest()) },
        SuiteKind.MEMORY to buildController { listOf(MemoryTest(chunks = ::allocateMemoryChunks)) },
        SuiteKind.STORAGE to buildController { listOf(StorageTest(application.cacheDir, readSpace = system::readStorage)) },
        SuiteKind.BATTERY to buildController(::buildBatteryTests),
        SuiteKind.CONNECTIVITY to buildController(::buildConnectivityTests),
    )

    /** PcTaskManager's launcher intent, or null when it is not installed. */
    val taskManagerIntent: Intent? = application.packageManager.getLaunchIntentForPackage(TASK_MANAGER_PACKAGE)

    fun onPageSelected(page: Page) {
        _page.value = page
    }

    private fun buildController(buildTests: () -> List<DiagnosticTest>) = SuiteController(viewModelScope, buildTests)

    private fun readOnce(model: CpuModel): LiveReadings = LiveReadings(
        cpuModel = model,
        cpuUsage = system.readCpuTimes()?.let(cpuMonitor::onSample) ?: CpuUsage.ZERO,
        cpuHistory = cpuMonitor.history,
        cpuSpeedKhz = system.readCpuSpeedKhz(),
        cpuMilliCelsius = system.readCpuMilliCelsius(),
        memory = system.readMemory(),
        storage = system.readStorage(),
        battery = system.readBattery(),
        network = networkReader.readSnapshot(),
    )

    private fun buildBatteryTests(): List<DiagnosticTest> {
        val battery = system.readBattery() ?: return emptyList()
        return listOf(BatteryRateTest(BatterySuite.chooseKind(battery), system::readBattery))
    }

    private fun buildConnectivityTests(): List<DiagnosticTest> {
        val probe = networkReader.buildProbe() ?: return emptyList()
        return ConnectivitySuite.buildTests(networkReader.readSnapshot(), probe, networkReader.readCheckUrl())
    }

    private fun allocateMemoryChunks(): List<MemoryCells> {
        val bytes = MemoryTest.chooseBytes(system.readMemory().availableBytes, Runtime.getRuntime().maxMemory())
        return List((bytes / CHUNK_BYTES).toInt().coerceAtLeast(1)) { ArrayCells(CHUNK_BYTES / Long.SIZE_BYTES) }
    }

    private companion object {
        const val REFRESH_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val CHUNK_BYTES = 8 shl 20
        const val TASK_MANAGER_PACKAGE = "org.lineageos.pctaskmanager"
    }
}
