package com.cachewipe.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cachewipe.app.data.AppInfo
import com.cachewipe.app.data.AppRepository
import com.cachewipe.app.data.Prefs
import com.cachewipe.app.engine.CacheCleanService
import com.cachewipe.app.engine.CleanJob
import com.cachewipe.app.engine.JobState
import com.cachewipe.app.engine.ShizukuEngine
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.cachewipe.app.ads.AdManager
import com.cachewipe.app.ads.AdPolicy
import com.cachewipe.app.ads.PrefsAdStore
import com.cachewipe.app.ads.RewardResult
import com.cachewipe.app.billing.BillingManager
import com.cachewipe.app.engine.AutoClean
import com.cachewipe.app.engine.Diagnostics
import com.cachewipe.app.engine.AutoMode
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.res.stringResource
import com.cachewipe.app.ui.DisclosureScreen
import com.cachewipe.app.ui.HomeScreen
import com.cachewipe.app.ui.RunScreen
import com.cachewipe.app.ui.SelectScreen

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository

    private var refreshTick by mutableIntStateOf(0)
    private var usageOk by mutableStateOf(false)
    private var a11yOk by mutableStateOf(false)
    private var protectionOn by mutableStateOf(false)
    private var apps by mutableStateOf<List<AppInfo>?>(null)
    private var shizukuState by mutableStateOf(ShizukuEngine.State.NOT_RUNNING)
    private var fastMode by mutableStateOf(true)
    private var autoMode by mutableStateOf(AutoMode.OFF)
    private var debugCleanAll = false
    private var debugFast = false
    /** Set when opened from the widget, quick tile or reminder notification. */
    private var pendingCleanAll = false

    private lateinit var adPolicy: AdPolicy
    private lateinit var adManager: AdManager
    private lateinit var billing: BillingManager
    private var billingPro by mutableStateOf(false)
    private var debugPro by mutableStateOf<Boolean?>(null)
    private val isPro get() = BuildConfig.OPEN_BUILD || (debugPro ?: billingPro)
    private var fastDialog by mutableStateOf<FastDialog?>(null)

    // Shizuku can start or grant permission while we are open: refresh the home screen
    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener { refreshTick++ }
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshTick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepository(this)
        fastMode = prefs.fastMode
        autoMode = prefs.autoMode
        billingPro = prefs.isPro // last known, so ads do not flash before the store answers
        if (prefs.debugPro) debugPro = true
        adPolicy = AdPolicy(PrefsAdStore(this))
        if (!BuildConfig.OPEN_BUILD) {
            adManager = AdManager(this).apply { init() }
            billing = BillingManager(this) { pro ->
                runOnUiThread {
                    billingPro = pro
                    prefs.isPro = pro
                }
            }.apply { connect() }
        }
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        readDebugCommand(intent)
        readLaunchAction(intent)
        setContent {
            MaterialTheme {
                Surface { App() }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(shizukuBinderListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        if (!BuildConfig.OPEN_BUILD) billing.close()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDebugCommand(intent)
        readLaunchAction(intent)
    }

    private fun readLaunchAction(intent: Intent?) {
        if (intent?.action == AutoClean.ACTION_CLEAN_ALL) {
            pendingCleanAll = true
            refreshTick++
        }
    }

    /** Device-test hook (debug builds only): `am start -n ... --es debug_clean all` runs "clean all" without tapping. */
    private fun readDebugCommand(intent: Intent?) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) CleanJob.debugForceManual = intent?.getBooleanExtra("debug_manual", false) == true
        if (debuggable && intent?.hasExtra("debug_pro") == true) {
            prefs.debugPro = intent.getBooleanExtra("debug_pro", false)
            debugPro = if (prefs.debugPro) true else null
        }
        if (debuggable && intent?.getStringExtra("debug_auto") == "run") AutoClean.runNow(this)
        if (debuggable && intent?.hasExtra("debug_cleans") == true) {
            // Reset ad rule memory and pretend N cleans were already done
            getSharedPreferences("ads", MODE_PRIVATE).edit().clear().apply()
            PrefsAdStore(this).finishedCleans = intent.getIntExtra("debug_cleans", 0)
        }
        val command = if (debuggable) intent?.getStringExtra("debug_clean") else null
        if (command == "all" || command == "fast") {
            prefs.disclosureAccepted = true
            debugCleanAll = command == "all"
            debugFast = command == "fast"
            refreshTick++
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }

    @Composable
    private fun App() {
        var accepted by remember(refreshTick) { mutableStateOf(prefs.disclosureAccepted) }
        var screen by remember { mutableStateOf(Screen.HOME) }
        var selected by remember { mutableStateOf(prefs.selected) }
        var excluded by remember { mutableStateOf(prefs.excluded) }
        val job by CleanJob.state.collectAsState()

        LaunchedEffect(refreshTick) {
            usageOk = repo.hasUsageAccess()
            a11yOk = CacheCleanService.instance != null
            protectionOn = repo.isAdvancedProtectionOn()
            shizukuState = ShizukuEngine.state()
            if (debugFast) {
                debugFast = false
                requestFastClean()
            }
            if (usageOk && !job.running) apps = repo.loadApps()
            if (debugCleanAll && apps != null) {
                debugCleanAll = false
                CleanJob.reset()
                startClean(apps.orEmpty().filter { it.packageName !in excluded && it.hasCache })
            }
            if (pendingCleanAll && !job.running && (apps != null || shizukuState == ShizukuEngine.State.READY)) {
                pendingCleanAll = false
                if (!isPro) {
                    toast(getString(R.string.toast_pro_only))
                } else {
                    CleanJob.reset()
                    cleanAll(excluded)
                }
            }
        }

        when {
            !accepted -> DisclosureScreen(onAccept = {
                prefs.disclosureAccepted = true
                accepted = true
            })

            job.running || job.finished -> {
                // Rule A: load only after the engine has finished, so [완료] never waits
                LaunchedEffect(job.finished) {
                    if (job.finished && adPolicy.shouldShowInterstitial(isPro)) adManager.preloadInterstitial()
                }
                RunScreen(
                    state = job,
                    onRetry = { failed -> startClean(failed) },
                    onDone = {
                        val backHome: () -> Unit = {
                            CleanJob.reset()
                            screen = Screen.HOME
                            refreshTick++
                        }
                        if (adPolicy.shouldShowInterstitial(isPro) && adManager.showInterstitialIfReady(backHome)) {
                            adPolicy.onInterstitialShown()
                        } else {
                            backHome()
                        }
                    },
                )
            }

            screen == Screen.SELECT -> {
                BackHandler { screen = Screen.HOME }
                SelectScreen(
                    apps = apps.orEmpty(),
                    selected = selected,
                    excluded = excluded,
                    onSelectedChange = { selected = it; prefs.selected = it },
                    onExcludedChange = { excluded = it; prefs.excluded = it },
                    onClean = { startClean(apps.orEmpty().filter { it.packageName in selected }) },
                    onBack = { screen = Screen.HOME },
                )
            }

            else -> HomeScreen(
                apps = apps,
                excluded = excluded,
                usageOk = usageOk,
                a11yOk = a11yOk,
                protectionOn = protectionOn,
                shizukuState = shizukuState,
                fastMode = fastMode,
                onFastModeChange = { fastMode = it; prefs.fastMode = it },
                onRequestShizuku = { Shizuku.requestPermission(ShizukuEngine.REQUEST_CODE) },
                onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                isPro = isPro,
                onBuyPro = ::buyPro,
                onShareDiagnostics = ::shareDiagnostics,
                autoMode = autoMode,
                onAutoModeChange = ::changeAutoMode,
                onCleanAll = { cleanAll(excluded) },
                onCleanSelected = { screen = Screen.SELECT },
            )
        }

        when (fastDialog) {
            FastDialog.INTRO -> AlertDialog(
                onDismissRequest = { fastDialog = null },
                title = { Text(stringResource(R.string.fast_dialog_title)) },
                text = { Text(stringResource(R.string.fast_dialog_text)) },
                confirmButton = {
                    TextButton(onClick = { fastDialog = null; watchAdThenFastClean() }) {
                        Text(stringResource(R.string.fast_dialog_watch))
                    }
                },
                dismissButton = { TextButton(onClick = { fastDialog = null }) { Text(stringResource(R.string.cancel)) } },
            )
            FastDialog.USED_UP -> AlertDialog(
                onDismissRequest = { fastDialog = null },
                title = { Text(stringResource(R.string.used_up_title)) },
                text = { Text(stringResource(R.string.used_up_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        fastDialog = null
                        startClean(normalTargets(excluded))
                    }) { Text(stringResource(R.string.used_up_normal)) }
                },
                dismissButton = { TextButton(onClick = { fastDialog = null; buyPro() }) { Text(stringResource(R.string.buy_pro)) } },
            )
            null -> Unit
        }
    }

    private fun normalTargets(excluded: Set<String>) =
        apps.orEmpty().filter { it.packageName !in excluded && it.hasCache }

    /** "Clear all": fast mode when it is on and ready, otherwise the accessibility engine. */
    private fun cleanAll(excluded: Set<String>) {
        if (fastMode && shizukuState == ShizukuEngine.State.READY) requestFastClean()
        else startClean(normalTargets(excluded))
    }

    private fun changeAutoMode(mode: AutoMode) {
        autoMode = mode
        prefs.autoMode = mode
        AutoClean.schedule(this, mode)
        // The result/reminder is a notification, which needs permission on Android 13+
        if (mode != AutoMode.OFF && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    private fun startClean(targets: List<AppInfo>) {
        val service = CacheCleanService.instance
        when {
            service == null -> toast(getString(R.string.toast_enable_a11y))
            targets.isEmpty() -> toast(getString(R.string.toast_nothing))
            else -> {
                service.start(targets)
                // A pop-up window could cover Settings' buttons: hide it, and reopen it at the same spot when done
                val window = windowManager.currentWindowMetrics.bounds
                val screen = windowManager.maximumWindowMetrics.bounds
                val isPopup = isInMultiWindowMode && window.width() < screen.width() && window.height() < screen.height() * 0.95
                if (isPopup) {
                    CleanJob.restoreBounds = window
                    moveTaskToBack(true)
                }
            }
        }
    }

    /** Rule B: Pro runs right away; free users get one ad-supported run per day. */
    private fun requestFastClean() {
        fastDialog = when {
            isPro -> null.also { startFastClean() }
            adPolicy.canUseFastFree() -> FastDialog.INTRO
            else -> FastDialog.USED_UP
        }
    }

    private fun watchAdThenFastClean() {
        toast(getString(R.string.toast_ad_loading))
        adManager.showRewarded { result ->
            when (result) {
                RewardResult.WATCHED -> {
                    adPolicy.onFastStarted(rewardWatched = true)
                    startFastClean()
                }
                // No ad to show is not the user's fault: run for free
                RewardResult.NO_AD -> {
                    adPolicy.onFastStarted(rewardWatched = false)
                    startFastClean()
                }
                RewardResult.CLOSED_EARLY -> toast(getString(R.string.toast_ad_closed))
            }
        }
    }

    private fun buyPro() = billing.buyPro(this) { toast(it) }

    private fun startFastClean() {
        CleanJob.reset()
        CleanJob.update { JobState(running = true, fastMode = true) }
        lifecycleScope.launch {
            val freed = ShizukuEngine.trimCaches()
            if (freed == null) adPolicy.onFastFailed() else adPolicy.onCleanFinished()
            CleanJob.update {
                it.copy(
                    running = false,
                    finished = true,
                    fastFreedBytes = freed ?: 0,
                    stopped = freed == null,
                    stopReason = if (freed == null) getString(R.string.fast_failed) else null,
                )
            }
        }
    }

    /** Open-source support: device info + this app's own recent log lines, shared as text. */
    private fun shareDiagnostics() {
        lifecycleScope.launch {
            val report = Diagnostics.build(this@MainActivity)
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
            startActivity(Intent.createChooser(send, getString(R.string.diag_button)))
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private enum class Screen { HOME, SELECT }

    private enum class FastDialog { INTRO, USED_UP }
}
