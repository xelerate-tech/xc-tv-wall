package tech.xelerate.tvwall.core

import kotlin.math.abs

/** How long each picture / slide stays on screen, as chosen in the on-TV menu. */
object SlideSpeed {
    val CHOICES_SECONDS = listOf(3, 5, 8, 10, 15, 20, 30, 45, 60)
    const val DEFAULT_SECONDS = 10

    /** Snaps any stored value onto the nearest menu choice. */
    fun normalize(seconds: Int): Int = CHOICES_SECONDS.minBy { abs(it - seconds) }

    /** One step shorter (slides change faster); stays at the shortest. */
    fun faster(seconds: Int): Int = step(seconds, -1)

    /** One step longer (slides change slower); stays at the longest. */
    fun slower(seconds: Int): Int = step(seconds, +1)

    private fun step(seconds: Int, by: Int): Int {
        val i = CHOICES_SECONDS.indexOf(normalize(seconds)) + by
        return CHOICES_SECONDS[i.coerceIn(0, CHOICES_SECONDS.lastIndex)]
    }
}
