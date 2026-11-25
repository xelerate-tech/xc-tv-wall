package tech.xelerate.tvwall.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeoutException

class PlaybackLimitsTest {
    @Test fun panoramaIsDownsampledToFitInside() {
        val s = PlaybackLimits.sampleSize(1080, 20_000, 1920, 1080)
        assertTrue("height ${20_000 / s} must be near the screen", 20_000 / s < 2 * 1080)
    }

    @Test fun normalPhotoKeepsAtLeastScreenResolution() {
        assertEquals(2, PlaybackLimits.sampleSize(4000, 3000, 1920, 1080))
        assertEquals(1, PlaybackLimits.sampleSize(1920, 1080, 1920, 1080))
    }

    @Test fun videoTimeoutIsDurationPlusMarginOrCap() {
        assertEquals(65_000L, PlaybackLimits.videoTimeoutMs(durationMs = 60_000L))
        assertEquals(PlaybackLimits.MAX_VIDEO_MS, PlaybackLimits.videoTimeoutMs(durationMs = null))
        assertEquals(PlaybackLimits.MAX_VIDEO_MS, PlaybackLimits.videoTimeoutMs(durationMs = 10 * 60 * 60_000L))
    }

    @Test fun stuckWorkTimesOutAsOrdinaryErrorNotCancellation() = runTest {
        val error = runCatching { PlaybackLimits.withPlaybackTimeout(1_000L, "video") { awaitCancellation() } }.exceptionOrNull()
        assertTrue("got $error", error is TimeoutException)
        assertTrue(error !is CancellationException)
    }

    @Test fun finishedWorkReturnsItsValue() = runTest {
        assertEquals(42, PlaybackLimits.withPlaybackTimeout(1_000L, "video") { 42 })
    }
}
