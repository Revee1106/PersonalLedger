package com.eric.personalledger.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.eric.personalledger.domain.PageProbeSample
import com.eric.personalledger.domain.PaymentPageHints
import com.eric.personalledger.domain.PaymentPageProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Passive, short-lived sampling. Never clicks, scrolls, captures screenshots, or writes a ledger. */
class PaymentPageProbeService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<String?>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            PageProbeStore.session.map { it.generation to it.running }.distinctUntilChanged().collect { (_, running) ->
                if (running) requests.trySend(null)
            }
        }
        scope.launch {
            for (sourcePackage in requests) {
                val generation = PageProbeStore.session.value.generation
                delay(250)
                if (PageProbeStore.session.value.isActive(SystemClock.elapsedRealtime())) {
                    sample(sourcePackage, generation)
                }
                delay(500)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        PageProbeStore.setConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!PageProbeStore.session.value.isActive(SystemClock.elapsedRealtime())) return
        val sourcePackage = event?.packageName?.toString() ?: return
        if (sourcePackage in PaymentPageProbe.supportedApps) requests.trySend(sourcePackage)
    }

    @Suppress("DEPRECATION") // recycle() is needed on the supported Android 8–12 devices.
    private fun sample(sourcePackage: String?, generation: Long) {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        var rootPackage = sourcePackage
        try {
            val root = rootInActiveWindow
            if (root == null) {
                sourcePackage?.let { unavailable(it, generation) }
                return
            }
            val packageName = root.packageName?.toString()
            if (packageName == null || packageName !in PaymentPageProbe.supportedApps) {
                root.recycle()
                return
            }
            rootPackage = packageName
            queue.add(root)
            val texts = mutableListOf<String>()
            var nodes = 0
            var hasPassword = false
            var skippedChildren = false
            val deadline = SystemClock.elapsedRealtime() + 700L
            while (queue.isNotEmpty() && nodes < 240 && SystemClock.elapsedRealtime() < deadline) {
                val node = queue.removeFirst()
                try {
                    if (node.packageName?.toString() != packageName || !node.isVisibleToUser) continue
                    nodes++
                    if (node.isPassword) {
                        hasPassword = true
                        break
                    }
                    node.text?.toString()?.takeIf(String::isNotBlank)?.let { texts.add(it.take(256)) }
                    node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let { texts.add(it.take(256)) }
                    val remaining = (240 - nodes - queue.size).coerceAtLeast(0)
                    if (node.childCount > remaining) skippedChildren = true
                    for (index in 0 until minOf(node.childCount, remaining)) {
                        node.getChild(index)?.let(queue::addLast)
                    }
                } finally {
                    node.recycle()
                }
            }
            val limited = skippedChildren || queue.isNotEmpty() || nodes >= 240 || texts.size > 240
            val hints = PaymentPageProbe.analyze(packageName, texts, hasPassword)
            PageProbeStore.record(generation, PageProbeSample(
                packageName = packageName,
                capturedAtMillis = System.currentTimeMillis(),
                readableNodeCount = nodes,
                hints = hints,
                limited = limited,
            ))
        } catch (_: IllegalStateException) {
            rootPackage?.let { unavailable(it, generation) }
        } catch (_: SecurityException) {
            rootPackage?.let { unavailable(it, generation) }
        } finally {
            while (queue.isNotEmpty()) queue.removeFirst().recycle()
        }
    }

    private fun unavailable(packageName: String, generation: Long) {
        PageProbeStore.record(generation, PageProbeSample(
            packageName, System.currentTimeMillis(), 0, PaymentPageHints(), unavailable = true,
        ))
    }

    override fun onInterrupt() {
        PageProbeStore.stop()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        PageProbeStore.setConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        PageProbeStore.setConnected(false)
        requests.close()
        scope.cancel()
        super.onDestroy()
    }
}
