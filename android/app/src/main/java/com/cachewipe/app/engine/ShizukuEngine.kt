package com.cachewipe.app.engine

import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Fast mode: runs `pm trim-caches` with shell rights through Shizuku,
 * which clears the cache of every app (system apps included) in a few seconds.
 */
object ShizukuEngine {
    const val REQUEST_CODE = 1001
    private const val TAG = "CacheWipe"

    enum class State { NOT_RUNNING, NO_PERMISSION, READY }

    fun state(): State = when {
        !Shizuku.pingBinder() -> State.NOT_RUNNING
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.NO_PERMISSION
        else -> State.READY
    }

    /** Freed bytes (change in free space), or null when the command failed. */
    suspend fun trimCaches(): Long? = withContext(Dispatchers.IO) {
        val before = freeBytes()
        val exit = try {
            // Shizuku keeps newProcess private (it prefers UserService); a one-line shell command does not need that
            val process = newProcess.invoke(null, arrayOf("pm", "trim-caches", "999G"), null, null) as Process
            // Plain waitFor(): the timed overload polls exitValue(), which the remote process rejects while running
            process.waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "trim-caches via Shizuku failed", e)
            -1
        }
        val freed = (freeBytes() - before).coerceAtLeast(0)
        Log.i(TAG, "fast mode trim-caches exit=$exit freed=$freed")
        if (exit == 0) freed else null
    }

    private fun freeBytes(): Long = StatFs(Environment.getDataDirectory().path).availableBytes

    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
    }
}
