package tech.xelerate.tvwall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private val folder = "folder1"
    private lateinit var drive: FakeDriveSource
    private lateinit var root: File

    private fun file(id: String, name: String, mime: String = "image/png", modified: String = "t1") =
        RemoteFile(id, name, mime, modified)

    private fun engine() = SyncEngine(drive, folder, root)

    private fun mediaFiles() = File(root, "media").listFiles().orEmpty().map { it.name }.toSet()

    @Before fun setUp() {
        drive = FakeDriveSource()
        root = tmp.newFolder("tvwall")
        drive.put(folder, file("b", "2_b.png"))
        drive.put(folder, file("a", "1_a.jpg", "image/jpeg"))
        drive.put(folder, file("s", "3_deck", PlaylistRules.GOOGLE_SLIDES_MIME))
    }

    private fun updated(result: SyncResult): Manifest {
        assertTrue("expected Updated but was $result", result is SyncResult.Updated)
        return (result as SyncResult.Updated).manifest
    }

    @Test fun firstSync_downloadsAllInRuleOrder() {
        val manifest = updated(engine().sync())
        assertEquals(listOf("a", "b", "s"), manifest.items.map { it.fileId })
        manifest.items.forEach { assertTrue(File(it.path).exists()) }
        assertTrue(manifest.items[2].path.endsWith(".pdf"))
        assertEquals(ItemKind.SLIDES, manifest.items[2].kind)
        assertEquals(1, drive.exports)
        assertEquals(2, drive.downloads)
    }

    @Test fun secondSync_withoutChanges_isUnchanged() {
        val e = engine()
        e.sync()
        assertEquals(SyncResult.Unchanged, e.sync())
        assertEquals(2, drive.downloads)
        assertEquals(1, drive.exports)
    }

    @Test fun changedModifiedTime_redownloadsButKeepsOldFileUntilReleased() {
        val e = engine()
        val oldManifest = updated(e.sync())
        val oldPath = oldManifest.items.first { it.fileId == "b" }.path
        drive.put(folder, file("b", "2_b.png", modified = "t2"), "new".toByteArray())
        val newManifest = updated(e.sync())
        assertEquals("new", File(newManifest.items.first { it.fileId == "b" }.path).readText())
        assertTrue("old file must survive while the player may still use it", File(oldPath).exists())
        e.releaseUnused(inUse = newManifest)
        assertFalse(File(oldPath).exists())
    }

    @Test fun removedRemotely_isDroppedAndDeletedOnceReleased() {
        val e = engine()
        val oldPath = updated(e.sync()).items.first { it.fileId == "a" }.path
        drive.remove(folder, "a")
        val manifest = updated(e.sync())
        assertEquals(listOf("b", "s"), manifest.items.map { it.fileId })
        assertTrue(File(oldPath).exists())
        e.releaseUnused(inUse = manifest)
        assertFalse(File(oldPath).exists())
    }

    @Test fun releaseUnused_keepsFilesOfPlayingAndLatestManifests() {
        val e = engine()
        val playing = updated(e.sync())
        drive.put(folder, file("b", "2_b.png", modified = "t2"))
        val latest = updated(e.sync())
        e.releaseUnused(inUse = playing)
        (playing.items + latest.items).forEach { assertTrue(it.path, File(it.path).exists()) }
    }

    @Test fun failingNewFile_isLeftOutAndOthersStillUpdate() {
        val e = engine()
        e.sync()
        drive.put(folder, file("c", "4_c.png"))
        drive.put(folder, file("b", "2_b.png", modified = "t2"), "new".toByteArray())
        drive.failOn += "c"
        val manifest = updated(e.sync())
        assertEquals(listOf("a", "b", "s"), manifest.items.map { it.fileId })
        assertEquals("new", File(manifest.items[1].path).readText())
        assertTrue(File(root, "staging").listFiles().orEmpty().isEmpty())
    }

    @Test fun failingChangedFile_keepsPreviousVersion() {
        val e = engine()
        val oldDeck = updated(e.sync()).items.first { it.fileId == "s" }
        drive.put(folder, file("s", "3_deck", PlaylistRules.GOOGLE_SLIDES_MIME, modified = "t2"))
        drive.put(folder, file("c", "4_c.png"))
        drive.failOn += "s"
        val manifest = updated(e.sync())
        assertEquals(oldDeck, manifest.items.first { it.fileId == "s" })
        assertEquals(listOf("a", "b", "s", "c"), manifest.items.map { it.fileId })
    }

    @Test fun listingFailure_keepsManifest() {
        val e = engine()
        val before = updated(e.sync())
        drive.listFails = true
        assertTrue(e.sync() is SyncResult.Failed)
        assertEquals(before, e.currentManifest())
    }

    @Test fun missingCachedFile_isRedownloaded() {
        val e = engine()
        val item = updated(e.sync()).items.first { it.fileId == "a" }
        File(item.path).delete()
        val manifest = updated(e.sync())
        assertTrue(File(manifest.items.first { it.fileId == "a" }.path).exists())
        assertEquals(3, drive.downloads)
    }

    @Test fun corruptManifest_isTreatedAsEmpty() {
        File(root, "manifest.json").writeText("{garbage")
        val e = engine()
        assertEquals(Manifest.EMPTY, e.currentManifest())
        assertEquals(3, updated(e.sync()).items.size)
    }

    @Test fun manifestPersistsAcrossInstances() {
        val manifest = updated(engine().sync())
        assertEquals(manifest, engine().currentManifest())
    }

    @Test fun durationsAreCarriedIntoManifest() {
        drive.put(folder, file("v", "5_clip.mp4", "video/mp4"))
        drive.put(folder, file("d", "6_x__20s.png"))
        val byId = updated(engine().sync()).items.associateBy { it.fileId }
        assertEquals(10_000L, byId.getValue("a").durationMs)
        assertEquals(null, byId.getValue("v").durationMs)
        assertEquals(20_000L, byId.getValue("d").durationMs)
    }
}
