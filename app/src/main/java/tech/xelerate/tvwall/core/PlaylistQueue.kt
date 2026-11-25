package tech.xelerate.tvwall.core

sealed interface Slide {
    data class Image(val path: String, val durationMs: Long) : Slide
    data class PdfPage(val path: String, val pageIndex: Int, val durationMs: Long) : Slide
    data class Video(val path: String) : Slide
}

/**
 * Loops over the slides of the active manifest. A manifest passed to [setManifest] replaces the
 * active one only when the loop wraps around, so the current pass is never cut short.
 * [onActivated] is told when a manifest starts playing, so files of older ones can be released.
 * Pictures and slides stay up for [defaultDurationMs], read at every [next] so a new speed chosen
 * in the menu applies from the next slide; a `__20s` suffix in the file name still wins.
 * [pageCount] and [onActivated] may do I/O, so call [next] off the main thread.
 */
class PlaylistQueue(
    private val pageCount: (path: String) -> Int,
    private val defaultDurationMs: () -> Long = { PlaylistRules.DEFAULT_DURATION_MS },
    private val onActivated: (Manifest) -> Unit = {},
) {
    /** A slide waiting for its duration: the file's own override, or the current speed setting. */
    private class Entry(val overrideMs: Long?, val build: (durationMs: Long) -> Slide)

    private var active: List<Entry> = emptyList()
    private var activeManifest: Manifest? = null
    private var index = 0
    private var pending: Manifest? = null

    /**
     * Queues [manifest] for the next loop. Passing the one already playing is a no-op (and drops
     * anything queued), so callers can safely re-send the current manifest after every sync.
     */
    @Synchronized
    fun setManifest(manifest: Manifest) {
        pending = if (manifest == activeManifest) null else manifest
    }

    @Synchronized
    fun next(): Slide? {
        if (index >= active.size) {
            pending?.let {
                active = expand(it)
                activeManifest = it
                pending = null
                onActivated(it)
            }
            index = 0
        }
        if (active.isEmpty()) return null
        val entry = active[index++]
        return entry.build(entry.overrideMs ?: defaultDurationMs())
    }

    // The override comes from the file name, not LocalItem.durationMs: manifests written by
    // version 1 stored the 10 s default there, which would otherwise mask the menu setting.
    private fun expand(manifest: Manifest): List<Entry> = manifest.items.flatMap { item ->
        val override = PlaylistRules.durationOverrideMs(item.name)
        when (item.kind) {
            ItemKind.IMAGE -> listOf(Entry(override) { Slide.Image(item.path, it) })
            ItemKind.VIDEO -> listOf(Entry(override) { Slide.Video(item.path) })
            ItemKind.SLIDES -> {
                val count = runCatching { pageCount(item.path) }.getOrDefault(0)
                List(count) { page -> Entry(override) { Slide.PdfPage(item.path, page, it) } }
            }
        }
    }
}
