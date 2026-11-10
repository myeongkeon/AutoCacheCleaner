package com.cachewipe.app.engine

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ItemStatus { CLEARED, ALREADY_EMPTY, FAILED }

data class ItemResult(
    val packageName: String,
    val label: String,
    val status: ItemStatus,
    val freedBytes: Long,
)

data class JobState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val stopped: Boolean = false,
    /** Why the job ended early, shown on the result screen (null = finished or user stop). */
    val stopReason: String? = null,
    /** Fast mode (Shizuku) clears everything in one command, so there are no per-app results. */
    val fastMode: Boolean = false,
    val fastFreedBytes: Long = 0,
    val total: Int = 0,
    val currentLabel: String = "",
    val results: List<ItemResult> = emptyList(),
)

data class Summary(val cleared: Int, val alreadyEmpty: Int, val failed: Int, val freedBytes: Long)

fun summarize(results: List<ItemResult>) = Summary(
    cleared = results.count { it.status == ItemStatus.CLEARED },
    alreadyEmpty = results.count { it.status == ItemStatus.ALREADY_EMPTY },
    failed = results.count { it.status == ItemStatus.FAILED },
    freedBytes = results.sumOf { it.freedBytes },
)

/** Shared state between the UI and the accessibility service. */
object CleanJob {
    private val _state = MutableStateFlow(JobState())
    val state: StateFlow<JobState> = _state

    @Volatile
    var stopRequested = false

    /** Pop-up window bounds to restore the app to when the job ends (null = was not a pop-up). */
    @Volatile
    var restoreBounds: Rect? = null

    /** Device-test switch (debug builds only): skip the automatic tap to exercise the semi-manual mode. */
    @Volatile
    var debugForceManual = false

    internal fun update(change: (JobState) -> JobState) {
        _state.value = change(_state.value)
    }

    fun reset() {
        stopRequested = false
        _state.value = JobState()
    }
}
