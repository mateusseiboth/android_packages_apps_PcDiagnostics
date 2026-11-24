package org.lineageos.pcdiagnostics

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.lineageos.pcdiagnostics.core.DiagnosticTest
import org.lineageos.pcdiagnostics.core.SuiteRunner
import org.lineageos.pcdiagnostics.core.SuiteState

/** One card's suite: runs its tests off the main thread and stops them on request. */
class SuiteController(private val scope: CoroutineScope, private val buildTests: () -> List<DiagnosticTest>) {
    private val canceled = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val _state = MutableStateFlow(SuiteState(emptyList()))
    val state: StateFlow<SuiteState> = _state.asStateFlow()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        canceled.set(false)
        scope.launch(Dispatchers.Default) {
            try {
                SuiteRunner(buildTests()).run({ canceled.get() }) { _state.value = it }
            } finally {
                running.set(false)
            }
        }
    }

    fun stop() = canceled.set(true)
}
