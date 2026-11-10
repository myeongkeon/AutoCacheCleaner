package com.cachewipe.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cachewipe.app.R
import com.cachewipe.app.BuildConfig
import com.cachewipe.app.data.AppInfo
import com.cachewipe.app.engine.AutoMode
import com.cachewipe.app.engine.ItemStatus
import com.cachewipe.app.engine.JobState
import com.cachewipe.app.engine.ShizukuEngine
import com.cachewipe.app.engine.summarize

@Composable
private fun size(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun DisclosureScreen(onAccept: () -> Unit) {
    Page {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.disclosure_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.disclosure_does_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.disclosure_does_body))
            Text(stringResource(R.string.disclosure_doesnt_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.disclosure_doesnt_body))
            Text(stringResource(R.string.disclosure_stop_hint))
        }
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.disclosure_accept))
        }
    }
}

@Composable
fun HomeScreen(
    apps: List<AppInfo>?,
    excluded: Set<String>,
    usageOk: Boolean,
    a11yOk: Boolean,
    protectionOn: Boolean,
    shizukuState: ShizukuEngine.State,
    fastMode: Boolean,
    onFastModeChange: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    isPro: Boolean,
    onBuyPro: () -> Unit,
    onShareDiagnostics: () -> Unit,
    autoMode: AutoMode,
    onAutoModeChange: (AutoMode) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onCleanAll: () -> Unit,
    onCleanSelected: () -> Unit,
) {
    Page {
        Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        when {
            !usageOk -> Text(stringResource(R.string.home_need_usage))
            apps == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text(stringResource(R.string.home_loading))
            }
            else -> {
                val targets = apps.filter { it.packageName !in excluded }
                Text(stringResource(R.string.home_total, size(targets.sumOf { it.cacheBytes })), fontSize = 20.sp)
                Text(stringResource(R.string.home_apps_with_cache, targets.count { it.hasCache }, excluded.size))
            }
        }
        if (protectionOn) {
            Card(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.protection_on), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        if (!usageOk || !a11yOk) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.perm_title), fontWeight = FontWeight.Bold)
                    if (!usageOk) OutlinedButton(onClick = onOpenUsageAccess) { Text(stringResource(R.string.perm_usage)) }
                    if (!a11yOk) OutlinedButton(onClick = onOpenAccessibility) { Text(stringResource(R.string.perm_a11y)) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (BuildConfig.OPEN_BUILD) {
            // Open build: everything is unlocked, nothing to sell
        } else if (isPro) {
            Text(stringResource(R.string.pro_active), color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onBuyPro) { Text(stringResource(R.string.pro_upsell)) }
        }
        AutoCleanRow(isPro, autoMode, onAutoModeChange, onBuyPro)
        FastModeRow(shizukuState, fastMode, onFastModeChange, onRequestShizuku)
        val ready = usageOk && a11yOk && apps != null
        val fastReady = fastMode && shizukuState == ShizukuEngine.State.READY
        Button(onClick = onCleanAll, enabled = ready || fastReady, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(if (fastReady) R.string.clean_all_fast else R.string.clean_all), fontSize = 18.sp)
        }
        OutlinedButton(onClick = onCleanSelected, enabled = ready, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.clean_selected), fontSize = 18.sp)
        }
        TextButton(onClick = onShareDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.diag_button))
        }
    }
}

/** Scheduled clean (Pro): Off / Daily / Weekly. Free users are sent to the Pro purchase. */
@Composable
private fun AutoCleanRow(isPro: Boolean, mode: AutoMode, onChange: (AutoMode) -> Unit, onBuyPro: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.auto_title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            listOf(
                AutoMode.OFF to R.string.auto_off,
                AutoMode.DAILY to R.string.auto_daily,
                AutoMode.WEEKLY to R.string.auto_weekly,
            ).forEach { (value, label) ->
                FilterChip(
                    selected = mode == value,
                    onClick = { if (isPro) onChange(value) else onBuyPro() },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Text(stringResource(R.string.auto_desc), color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
    }
}

/** Fast mode (Shizuku) status: shown only when the Shizuku app is running. */
@Composable
private fun FastModeRow(
    state: ShizukuEngine.State,
    fastMode: Boolean,
    onFastModeChange: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
) {
    when (state) {
        ShizukuEngine.State.NOT_RUNNING -> Unit
        ShizukuEngine.State.NO_PERMISSION -> OutlinedButton(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.fast_permission))
        }
        ShizukuEngine.State.READY -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.fast_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.fast_desc), color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
            }
            Switch(checked = fastMode, onCheckedChange = onFastModeChange)
        }
    }
}

@Composable
fun SelectScreen(
    apps: List<AppInfo>,
    selected: Set<String>,
    excluded: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    onExcludedChange: (Set<String>) -> Unit,
    onClean: () -> Unit,
    onBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val checked = if (tab == 0) selected else excluded
    val onChange = if (tab == 0) onSelectedChange else onExcludedChange
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                stringResource(if (tab == 0) R.string.select_title_pick else R.string.select_title_exclude),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_selected, selected.size)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_excluded, excluded.size)) })
        }
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { app ->
                val isChecked = app.packageName in checked
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onChange(if (isChecked) checked - app.packageName else checked + app.packageName) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isChecked, onCheckedChange = null)
                    Text(app.label, Modifier.weight(1f).padding(start = 8.dp))
                    Text(size(app.cacheBytes), color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        if (tab == 0) {
            val bytes = apps.filter { it.packageName in selected }.sumOf { it.cacheBytes }
            Button(onClick = onClean, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.select_clean, selected.size, size(bytes)))
            }
        }
    }
}

@Composable
fun RunScreen(state: JobState, onRetry: (List<AppInfo>) -> Unit, onDone: () -> Unit) {
    val summary = summarize(state.results)
    val title = stringResource(if (state.stopped) R.string.run_stopped else R.string.run_done)
    Page {
        if (state.fastMode) {
            Text(
                if (state.running) stringResource(R.string.run_fast_running) else title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            if (state.running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Page
            }
            state.stopReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!state.stopped) {
                Text(stringResource(R.string.run_freed, size(state.fastFreedBytes)), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.run_fast_summary))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.done)) }
            return@Page
        }
        if (state.running) {
            Text(stringResource(R.string.run_running), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.results.size / state.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.run_progress, state.results.size, state.total, state.currentLabel))
            return@Page
        }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        state.stopReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(stringResource(R.string.run_freed, size(summary.freedBytes)), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.run_summary, summary.cleared, summary.alreadyEmpty, summary.failed))
        val failed = state.results.filter { it.status == ItemStatus.FAILED }
        if (failed.isNotEmpty()) {
            Text(stringResource(R.string.run_failed_title), fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f)) {
                items(failed, key = { it.packageName }) { Text("• ${it.label}") }
            }
            OutlinedButton(
                onClick = { onRetry(failed.map { AppInfo(it.packageName, it.label, 0L) }) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.run_retry, failed.size)) }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.done)) }
    }
}
