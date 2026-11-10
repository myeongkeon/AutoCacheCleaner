package com.cachewipe.app.engine

import com.cachewipe.app.data.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineLogicTest {

    @Test
    fun labelMatch_ignoresCaseAndSpaces() {
        assertTrue(LabelMatcher.matches("Clear  Cache", listOf("Clear cache")))
        assertTrue(LabelMatcher.matches("캐시 삭제", listOf("캐시삭제")))
    }

    @Test
    fun labelMatch_rejectsSimilarButDifferentButtons() {
        // "Clear storage" / "데이터 삭제" must never be tapped
        assertFalse(LabelMatcher.matches("Clear storage", listOf("Clear cache")))
        assertFalse(LabelMatcher.matches("데이터 삭제", listOf("캐시 삭제")))
        assertFalse(LabelMatcher.matches(null, listOf("캐시 삭제")))
        assertFalse(LabelMatcher.matches("", listOf("")))
    }

    @Test
    fun summarize_countsAndSumsFreedBytes() {
        val s = summarize(
            listOf(
                ItemResult("a", "A", ItemStatus.CLEARED, 100),
                ItemResult("b", "B", ItemStatus.CLEARED, 50),
                ItemResult("c", "C", ItemStatus.ALREADY_EMPTY, 0),
                ItemResult("d", "D", ItemStatus.FAILED, 0),
            ),
        )
        assertEquals(Summary(cleared = 2, alreadyEmpty = 1, failed = 1, freedBytes = 150), s)
    }

    @Test
    fun verdict_smallSelfTrimIsNotACleanup() {
        // Play Store case: 434176 -> 426000 on its own must not count as cleared
        assertFalse(Verdict.clearedBySizeDrop(434_176, 425_984))
        assertFalse(Verdict.clearedBySizeDrop(1_000, 500))
    }

    @Test
    fun verdict_dropToAlmostNothingCounts() {
        assertTrue(Verdict.clearedBySizeDrop(1_000_000, 0))
        assertTrue(Verdict.clearedBySizeDrop(1_000_000, 100_000)) // exactly 10% left
        assertFalse(Verdict.clearedBySizeDrop(1_000_000, 100_001))
    }

    @Test
    fun verdict_ignoresInvalidInput() {
        assertFalse(Verdict.clearedBySizeDrop(0, 0))
        assertFalse(Verdict.clearedBySizeDrop(1_000, -1))
    }

    @Test
    fun hasCache_ignoresEmptyCacheDirectory() {
        // An empty cache dir reports one 4 KB block; it must not be counted or cleaned
        assertFalse(AppInfo("a", "A", 0).hasCache)
        assertFalse(AppInfo("a", "A", 4096).hasCache)
        assertTrue(AppInfo("a", "A", 4097).hasCache)
    }
}
