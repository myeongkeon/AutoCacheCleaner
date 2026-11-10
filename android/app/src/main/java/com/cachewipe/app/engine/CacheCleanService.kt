package com.cachewipe.app.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.cachewipe.app.MainActivity
import com.cachewipe.app.R
import com.cachewipe.app.ads.AdPolicy
import com.cachewipe.app.ads.PrefsAdStore
import com.cachewipe.app.data.AppInfo
import com.cachewipe.app.data.AppRepository
import com.cachewipe.app.data.EMPTY_CACHE_BYTES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Clears app caches by tapping Settings > App info > Storage > Clear cache.
 * Only the Settings app is watched (see accessibility_service_config.xml); nothing is stored or sent.
 */
class CacheCleanService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var overlay: View? = null
    private var overlayText: TextView? = null

    /** Bumped on every Settings window change, so we never act on the previous app's screen. */
    @Volatile
    private var windowChanges = 0

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) windowChanges++
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        CleanJob.stopRequested = true
        // Service killed mid-job (turned off, low memory): end the job so the UI does not hang
        if (CleanJob.state.value.running) {
            CleanJob.update { it.copy(running = false, finished = true, stopped = true, currentLabel = "") }
        }
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    fun start(apps: List<AppInfo>) {
        if (job?.isActive == true || apps.isEmpty()) return
        CleanJob.stopRequested = false
        CleanJob.update { JobState(running = true, total = apps.size) }
        showOverlay()
        job = scope.launch {
            val labels = SettingsLabels(this@CacheCleanService)
            val repo = AppRepository(this@CacheCleanService)
            Log.i(TAG, "start ${apps.size} apps, storage=${labels.storage} clearCache=${labels.clearCache}")
            for ((index, app) in apps.withIndex()) {
                if (shouldStop()) break
                overlayText?.text = getString(R.string.overlay_progress, index + 1, apps.size, app.label)
                val result = process(app, labels, repo) ?: break
                CleanJob.update { it.copy(results = it.results + result) }
            }
            // One automatic retry for failures (a call or notification may have covered Settings)
            val failed = CleanJob.state.value.results.filter { it.status == ItemStatus.FAILED }
            for ((index, item) in failed.withIndex()) {
                if (shouldStop()) break
                overlayText?.text = getString(R.string.overlay_retry, index + 1, failed.size, item.label)
                val result = process(AppInfo(item.packageName, item.label, 0L), labels, repo) ?: break
                CleanJob.update { s ->
                    s.copy(results = s.results.map { if (it.packageName == item.packageName) result else it })
                }
            }
            finishJob()
        }
    }

    private fun shouldStop(): Boolean {
        if (CleanJob.stopRequested) return true
        if (!isScreenLocked()) return false
        Log.i(TAG, "screen off or locked, stopping")
        CleanJob.update { it.copy(stopReason = getString(R.string.stop_screen_locked)) }
        return true
    }

    /** Cleans one app and measures what was freed; null when the user stopped mid-app. */
    private suspend fun process(app: AppInfo, labels: SettingsLabels, repo: AppRepository): ItemResult? {
        CleanJob.update { it.copy(currentLabel = app.label) }
        val startedAt = SystemClock.elapsedRealtime()
        val before = repo.cacheBytes(app.packageName)
        val status = if (before <= EMPTY_CACHE_BYTES) ItemStatus.ALREADY_EMPTY else cleanOne(app.packageName, labels, before, repo)
        if (CleanJob.stopRequested && status == ItemStatus.FAILED) return null
        val freed = if (status == ItemStatus.CLEARED) {
            delay(SETTLE_MS)
            (before - repo.cacheBytes(app.packageName)).coerceAtLeast(0)
        } else 0L
        Log.i(TAG, "${app.packageName} $status before=$before freed=$freed ms=${SystemClock.elapsedRealtime() - startedAt}")
        return ItemResult(app.packageName, app.label, status, freed)
    }

    /**
     * Semi-manual fallback: when automation cannot find or press the button, ask the user to tap it
     * and count the app as cleared once the button turns off or the cache falls to almost nothing
     * (a small drop alone is the app trimming itself, not the user's tap).
     */
    private suspend fun askUserToTap(
        packageName: String,
        labels: SettingsLabels,
        before: Long,
        repo: AppRepository,
        reason: String,
    ): ItemStatus {
        val shown = rootInActiveWindow?.let { findOnScreenButton(it, labels) }?.let { Rect().also(it::getBoundsInScreen) }
        Log.d(TAG, "$packageName asking user to tap: $reason, button=$shown")
        if (shown == null) {
            dumpTree("$packageName[active]", rootInActiveWindow)
            // Diagnostic: is the button in another window?
            windows.forEachIndexed { i, w ->
                Log.d(TAG, "$packageName window#$i type=${w.type} layer=${w.layer} title=${w.title} active=${w.isActive} root=${w.root?.packageName}")
                if (w.root?.packageName == SettingsLabels.SETTINGS_PKG) dumpTree("$packageName[window#$i]", w.root)
            }
        }
        val previous = overlayText?.text
        overlayText?.text = getString(R.string.overlay_manual)
        val done = waitUntil(MANUAL_TIMEOUT_MS) {
            rootInActiveWindow?.let { findOnScreenButton(it, labels) }?.isEnabled == false ||
                Verdict.clearedBySizeDrop(before, repo.cacheBytes(packageName))
        }
        overlayText?.text = previous
        return if (done) ItemStatus.CLEARED else fail(packageName, "$reason, user did not tap")
    }

    private suspend fun cleanOne(
        packageName: String,
        labels: SettingsLabels,
        before: Long,
        repo: AppRepository,
    ): ItemStatus {
        val seen = windowChanges
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        if (!waitUntil(STEP_TIMEOUT_MS) { windowChanges != seen }) return fail(packageName, "app info not opened")

        val storageItem = waitForNode(scroll = true) { findClickableByText(it, labels.storage) }
            ?: return askUserToTap(packageName, labels, before, repo, "storage item not found")
        val beforeStorage = windowChanges
        if (!storageItem.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return fail(packageName, "storage click rejected")
        if (!waitUntil(STEP_TIMEOUT_MS) { windowChanges != beforeStorage }) return fail(packageName, "storage page not opened")

        // The storage page slides in from the side: wait until the button is fully on screen,
        // then give Settings time to compute the cache size and set the button state
        waitForNode(scroll = false) { findOnScreenButton(it, labels) }
            ?: return askUserToTap(packageName, labels, before, repo, "clear cache button not found")
        // Wait at least SIZE_LOAD_MIN_MS, then only while the button is still disabled (worst case = SIZE_LOAD_MS)
        delay(SIZE_LOAD_MIN_MS)
        var button = rootInActiveWindow?.let { findOnScreenButton(it, labels) }
        var extraWait = 0L
        while (button != null && !button.isEnabled && extraWait < SIZE_LOAD_MS - SIZE_LOAD_MIN_MS) {
            delay(POLL_MS)
            extraWait += POLL_MS
            button = rootInActiveWindow?.let { findOnScreenButton(it, labels) }
        }
        if (button == null) return fail(packageName, "clear cache button lost")
        if (!button.isEnabled) return ItemStatus.ALREADY_EMPTY
        if (CleanJob.debugForceManual) return askUserToTap(packageName, labels, before, repo, "forced by debug switch")
        // One UI ignores ACTION_CLICK on the bottom bar buttons, so tap like a finger
        val bounds = Rect().also(button::getBoundsInScreen)
        // Settings drops touches on sensitive buttons while any overlay covers its window
        overlay?.visibility = View.GONE
        delay(OVERLAY_HIDE_MS)
        val tapped = tap(button)
        overlay?.visibility = View.VISIBLE
        Log.d(TAG, "$packageName tap=$tapped at $bounds id=${button.viewIdResourceName}")
        if (!tapped) return ItemStatus.FAILED
        // Count as cleared only when Settings disables the button (cache is 0)
        val disabled = waitUntil(VERIFY_TIMEOUT_MS, settleMs = 0L) {
            rootInActiveWindow?.let { findOnScreenButton(it, labels) }?.isEnabled == false
        }
        return if (disabled) ItemStatus.CLEARED else askUserToTap(packageName, labels, before, repo, "button still enabled after tap")
    }

    /**
     * Diagnostics for screens the engine does not recognise (read with `adb logcat -s CacheWipe`).
     * Only runs on the fallback path; logs Settings UI structure only, never user content beyond labels.
     */
    private fun dumpTree(packageName: String, root: AccessibilityNodeInfo?) {
        if (root == null) {
            Log.d(TAG, "$packageName tree: no active window")
            return
        }
        Log.d(TAG, "$packageName tree: window pkg=${root.packageName}")
        var count = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (count++ >= DUMP_MAX_NODES) return
            val b = Rect().also(node::getBoundsInScreen)
            Log.d(
                TAG,
                "  ".repeat(depth) + "${node.className?.toString()?.substringAfterLast('.')} " +
                    "text=${node.text} desc=${node.contentDescription} id=${node.viewIdResourceName} " +
                    "click=${node.isClickable} en=${node.isEnabled} vis=${node.isVisibleToUser} $b",
            )
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
    }

    private fun isScreenLocked(): Boolean =
        !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun findOnScreenButton(root: AccessibilityNodeInfo, labels: SettingsLabels): AccessibilityNodeInfo? {
        val node = findClickableByText(root, labels.clearCache) ?: return null
        val bounds = Rect().also(node::getBoundsInScreen)
        val screenWidth = resources.displayMetrics.widthPixels
        return node.takeIf { bounds.width() > 0 && bounds.left >= 0 && bounds.right <= screenWidth }
    }

    private fun fail(packageName: String, reason: String): ItemStatus {
        Log.d(TAG, "$packageName failed: $reason")
        return ItemStatus.FAILED
    }

    private suspend fun tap(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect().also(node::getBoundsInScreen)
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS))
            .build()
        return suspendCancellableCoroutine { cont ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(g: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            if (!dispatchGesture(gesture, callback, null) && cont.isActive) cont.resume(false)
        }
    }

    /** Polls the active Settings window until [find] returns a node, scrolling the list when asked. */
    private suspend fun waitForNode(
        scroll: Boolean,
        find: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?,
    ): AccessibilityNodeInfo? {
        var waited = 0L
        var polls = 0
        while (waited < STEP_TIMEOUT_MS && !CleanJob.stopRequested) {
            val root = rootInActiveWindow
            if (root != null && root.packageName == SettingsLabels.SETTINGS_PKG) {
                find(root)?.let { return it }
                if (scroll && ++polls % SCROLL_EVERY_POLLS == 0) {
                    findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                }
            }
            delay(POLL_MS)
            waited += POLL_MS
        }
        return null
    }

    private suspend fun waitUntil(timeoutMs: Long, settleMs: Long = DRAW_SETTLE_MS, condition: () -> Boolean): Boolean {
        var waited = 0L
        while (waited < timeoutMs && !CleanJob.stopRequested) {
            if (condition()) {
                if (settleMs > 0) delay(settleMs) // let the new screen finish drawing
                return true
            }
            delay(POLL_MS)
            waited += POLL_MS
        }
        return false
    }

    /** Finds a node showing one of [labels] and returns its clickable self or ancestor. */
    private fun findClickableByText(root: AccessibilityNodeInfo, labels: Set<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (LabelMatcher.matches(node.text, labels) || LabelMatcher.matches(node.contentDescription, labels)) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                if (target != null) return target
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun finishJob() {
        Log.i(TAG, "done stopped=${CleanJob.stopRequested} ${summarize(CleanJob.state.value.results)}")
        if (CleanJob.state.value.results.isNotEmpty()) AdPolicy(PrefsAdStore(this)).onCleanFinished()
        removeOverlay()
        CleanJob.update {
            it.copy(running = false, finished = true, stopped = CleanJob.stopRequested || it.stopReason != null, currentLabel = "")
        }
        val options = CleanJob.restoreBounds?.let { ActivityOptions.makeBasic().setLaunchBounds(it).toBundle() }
        CleanJob.restoreBounds = null
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            options,
        )
    }

    /** Progress bar with a Stop button, drawn over Settings while the job runs. */
    private fun showOverlay() {
        if (overlay != null) return
        val label = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val stop = Button(this).apply {
            text = getString(R.string.stop)
            setOnClickListener {
                CleanJob.stopRequested = true
                this.text = getString(R.string.stopping)
            }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xEE202124.toInt())
            setPadding(40, 140, 40, 24)
            addView(label)
            addView(stop)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // KEEP_SCREEN_ON: the screen must not time out mid-job (Settings will not open on a locked screen)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        getSystemService(WindowManager::class.java).addView(bar, params)
        overlay = bar
        overlayText = label
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null
        overlayText = null
    }

    companion object {
        /** Set while the user has the service turned on in Accessibility settings. */
        @Volatile
        var instance: CacheCleanService? = null
            private set

        private const val TAG = "CacheWipe"
        private const val POLL_MS = 100L
        private const val DRAW_SETTLE_MS = 250L
        private const val SCROLL_EVERY_POLLS = 8 // ~800 ms, same cadence as before the poll interval was shortened
        private const val DUMP_MAX_NODES = 150
        private const val STEP_TIMEOUT_MS = 6_000L
        private const val SETTLE_MS = 300L
        private const val VERIFY_TIMEOUT_MS = 2_500L
        private const val SIZE_LOAD_MS = 500L
        private const val SIZE_LOAD_MIN_MS = 250L
        private const val OVERLAY_HIDE_MS = 150L
        private const val MANUAL_TIMEOUT_MS = 10_000L
        private const val TAP_MS = 50L
    }
}
