package com.cachewipe.app.engine

/**
 * Decides whether an app counts as cleared while waiting for the user to tap the button.
 * A running app can shrink its own cache a little at any time, so a small drop is not proof.
 */
object Verdict {
    /** A real "Clear cache" leaves almost nothing behind: the cache must fall to this share of its old size. */
    const val MAX_REMAINING_PERCENT = 10

    fun clearedBySizeDrop(before: Long, after: Long): Boolean =
        before > 0 && after >= 0 && after * 100 <= before * MAX_REMAINING_PERCENT
}
