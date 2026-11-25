package tech.xelerate.tvwall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistQueueTest {
    private val pages = mutableMapOf("/deck.pdf" to 3, "/empty.pdf" to 0)
    private val activated = mutableListOf<Manifest>()
    private var defaultMs = 10_000L
    private val queue = PlaylistQueue(
        pageCount = { path -> if (path == "/broken.pdf") throw IllegalStateException("corrupt") else pages.getValue(path) },
        defaultDurationMs = { defaultMs },
        onActivated = { activated += it },
    )

    // The stored duration is what version 1 wrote (the default baked in); the queue must not trust it.
    private fun item(name: String, kind: ItemKind, path: String, duration: Long? = 10_000L) =
        LocalItem(name, name, kind, "t", path, duration)

    private val a = Manifest(listOf(
        item("img__7s.png", ItemKind.IMAGE, "/img.png", 7_000L),
        item("deck", ItemKind.SLIDES, "/deck.pdf"),
        item("vid", ItemKind.VIDEO, "/vid.mp4", null),
    ))
    private val b = Manifest(listOf(item("other", ItemKind.IMAGE, "/other.png")))

    private fun take(n: Int) = List(n) { queue.next() }

    @Test fun expandsSlidesAndWraps() {
        queue.setManifest(a)
        assertEquals(listOf(
            Slide.Image("/img.png", 7_000L),
            Slide.PdfPage("/deck.pdf", 0, 10_000L),
            Slide.PdfPage("/deck.pdf", 1, 10_000L),
            Slide.PdfPage("/deck.pdf", 2, 10_000L),
            Slide.Video("/vid.mp4"),
            Slide.Image("/img.png", 7_000L),
        ), take(6))
    }

    @Test fun newManifestAppliesOnlyAtLoopBoundary() {
        queue.setManifest(a)
        take(2)
        queue.setManifest(b)
        assertEquals(listOf(
            Slide.PdfPage("/deck.pdf", 1, 10_000L),
            Slide.PdfPage("/deck.pdf", 2, 10_000L),
            Slide.Video("/vid.mp4"),
            Slide.Image("/other.png", 10_000L),
            Slide.Image("/other.png", 10_000L),
        ), take(5))
    }

    @Test fun freshQueueUsesManifestImmediately() {
        assertNull(queue.next())
        queue.setManifest(b)
        assertEquals(Slide.Image("/other.png", 10_000L), queue.next())
    }

    @Test fun emptiedFolderReturnsNullThenRecovers() {
        queue.setManifest(b)
        queue.next()
        queue.setManifest(Manifest.EMPTY)
        assertNull(queue.next())
        assertNull(queue.next())
        queue.setManifest(a)
        assertEquals(Slide.Image("/img.png", 7_000L), queue.next())
    }

    @Test fun zeroPageOrBrokenPdfContributesNothing() {
        queue.setManifest(Manifest(listOf(
            item("e", ItemKind.SLIDES, "/empty.pdf"),
            item("x", ItemKind.SLIDES, "/broken.pdf"),
            item("ok", ItemKind.IMAGE, "/ok.png"),
        )))
        assertEquals(listOf(Slide.Image("/ok.png", 10_000L), Slide.Image("/ok.png", 10_000L)), take(2))
    }

    @Test fun missingDurationFallsBackToDefault() {
        queue.setManifest(Manifest(listOf(item("i", ItemKind.IMAGE, "/i.png", null))))
        assertEquals(Slide.Image("/i.png", PlaylistRules.DEFAULT_DURATION_MS), queue.next())
    }

    @Test fun reportsManifestWhenItBecomesActive() {
        queue.setManifest(a)
        queue.next()
        assertEquals(listOf(a), activated)
        queue.setManifest(b)
        take(4)
        assertEquals(listOf(a), activated)
        queue.next()
        assertEquals(listOf(a, b), activated)
    }

    @Test fun slidesWithoutOverrideUseTheCurrentSpeedSetting() {
        queue.setManifest(a)
        queue.next()
        defaultMs = 5_000L
        assertEquals(Slide.PdfPage("/deck.pdf", 0, 5_000L), queue.next())
        defaultMs = 30_000L
        assertEquals(Slide.PdfPage("/deck.pdf", 1, 30_000L), queue.next())
    }

    @Test fun fileNameOverrideBeatsSpeedSetting() {
        defaultMs = 5_000L
        queue.setManifest(a)
        assertEquals(Slide.Image("/img.png", 7_000L), queue.next())
    }

    @Test fun storedDefaultFromOldManifestIsIgnored() {
        defaultMs = 20_000L
        queue.setManifest(Manifest(listOf(item("old.png", ItemKind.IMAGE, "/old.png", 10_000L))))
        assertEquals(Slide.Image("/old.png", 20_000L), queue.next())
    }

    @Test fun resendingThePlayingManifestChangesNothing() {
        queue.setManifest(a)
        take(2)
        queue.setManifest(a.copy())
        assertEquals(listOf(
            Slide.PdfPage("/deck.pdf", 1, 10_000L),
            Slide.PdfPage("/deck.pdf", 2, 10_000L),
            Slide.Video("/vid.mp4"),
            Slide.Image("/img.png", 7_000L),
        ), take(4))
        assertEquals(listOf(a), activated)
    }

    @Test fun resendingTheActiveManifestCancelsAPendingOne() {
        queue.setManifest(a)
        queue.next()
        queue.setManifest(b)
        queue.setManifest(a)
        take(4)
        assertEquals(Slide.Image("/img.png", 7_000L), queue.next())
        assertEquals(listOf(a), activated)
    }
}
