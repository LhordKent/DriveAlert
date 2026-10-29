package com.lhordkent.drivealert.data.history

import com.lhordkent.drivealert.postauth.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class HistoryRetentionTest {
    private fun record(time: Long) = Stage3SyncRecord("record", LocalDateTime.of(2026, 1, 1, 0, 0),
        SyncRecordKind.STAGE_3_TRANSITION, emptySet(), SharingState.SHARED, occurredAtEpochMillis = time)

    @Test fun presetsAndCustomUseExact24HourDays() {
        listOf(1, 3, 7, 14, 3650).forEach { days ->
            assertEquals(1_000_000_000_000L - days * 86_400_000L, HistoryRetention.cutoff(1_000_000_000_000L, days))
        }
    }
    @Test fun invalidCustomDurationsAreRejected() {
        listOf(0, -1, 3651, Int.MAX_VALUE).forEach { assertTrue(runCatching { HistoryRetention.validate(it) }.isFailure) }
        HistoryRetention.validate(null)
    }
    @Test fun shorterPolicyNeedsPreviewButManualAndLongerDoNot() {
        assertTrue(HistoryRetention.isShorter(null, 1))
        assertTrue(HistoryRetention.isShorter(7, 3))
        assertFalse(HistoryRetention.isShorter(1, 7))
        assertFalse(HistoryRetention.isShorter(1, null))
        assertFalse(HistoryRetention.isShorter(7, 7))
    }
    @Test fun expiryBoundaryUsesOriginalEpochAndRetainsExactBoundary() {
        val visibility = SharedHistoryVisibility(expiredBefore = 100)
        assertFalse(visibility.allows("driver", record(99)))
        assertTrue(visibility.allows("driver", record(100)))
        assertTrue(visibility.allows("driver", record(101)))
    }
    @Test fun manualDismissalsAreScopedToSourceDriver() {
        val visibility = SharedHistoryVisibility(dismissed = setOf("driver-a" to "record"))
        assertFalse(visibility.allows("driver-a", record(100)))
        assertTrue(visibility.allows("driver-b", record(100)))
    }
}
