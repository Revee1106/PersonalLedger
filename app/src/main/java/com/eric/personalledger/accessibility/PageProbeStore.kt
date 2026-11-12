package com.eric.personalledger.accessibility

import android.os.SystemClock
import com.eric.personalledger.domain.PageProbeSample
import com.eric.personalledger.domain.PageProbeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Process memory only. Neither page text nor summaries are written to disk or logs. */
object PageProbeStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableSession = MutableStateFlow(PageProbeSession())
    private val mutableConnected = MutableStateFlow(false)
    private var expiry: Job? = null
    val session = mutableSession.asStateFlow()
    val connected = mutableConnected.asStateFlow()

    fun start() {
        expiry?.cancel()
        mutableSession.update { it.start(SystemClock.elapsedRealtime()) }
        expiry = scope.launch {
            delay(5 * 60_000L)
            stop()
        }
    }

    fun stop(clear: Boolean = false) {
        expiry?.cancel()
        mutableSession.update { it.stop(clear) }
    }

    fun expireIfNeeded() {
        val current = mutableSession.value
        if (current.running && !current.isActive(SystemClock.elapsedRealtime())) stop()
    }

    fun setConnected(connected: Boolean) {
        mutableConnected.value = connected
        if (!connected) stop()
    }

    fun record(generation: Long, sample: PageProbeSample) {
        mutableSession.update { it.record(generation, SystemClock.elapsedRealtime(), sample) }
    }
}
