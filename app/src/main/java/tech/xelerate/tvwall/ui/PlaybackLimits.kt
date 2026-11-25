package tech.xelerate.tvwall.ui

import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException

/** Safety limits that keep one odd file from crashing or freezing the wall. */
object PlaybackLimits {
    const val MAX_VIDEO_MS = 30 * 60_000L
    private const val VIDEO_MARGIN_MS = 5_000L

    /** Power-of-two downsample so neither side is decoded at more than ~2x the screen. */
    fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= targetWidth || height / (sample * 2) >= targetHeight) sample *= 2
        return sample
    }

    /** How long a video may run before it's treated as stuck. */
    fun videoTimeoutMs(durationMs: Long?): Long =
        if (durationMs == null || durationMs <= 0) MAX_VIDEO_MS
        else minOf(durationMs + VIDEO_MARGIN_MS, MAX_VIDEO_MS)

    /**
     * Like `withTimeout`, but a timeout is an ordinary [TimeoutException] (so the play loop skips the
     * slide) instead of a CancellationException (which would stop the loop).
     */
    suspend fun <T : Any> withPlaybackTimeout(timeoutMs: Long, what: String, block: suspend () -> T): T =
        withTimeoutOrNull(timeoutMs) { block() } ?: throw TimeoutException("$what took longer than $timeoutMs ms")
}
