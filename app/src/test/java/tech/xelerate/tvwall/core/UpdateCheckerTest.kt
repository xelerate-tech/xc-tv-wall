package tech.xelerate.tvwall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    private val root = "root"
    private val drive = FakeDriveSource()

    private fun apk(id: String, name: String) = RemoteFile(id, name, "application/vnd.android.package-archive", "t")

    private fun withAppFolder(vararg files: RemoteFile) {
        drive.put(root, RemoteFile("appdir", "_app", PlaylistRules.FOLDER_MIME, "t"))
        files.forEach { drive.put("appdir", it) }
    }

    @Test fun noAppFolder_returnsNull() {
        drive.put(root, RemoteFile("x", "1.png", "image/png", "t"))
        assertNull(UpdateChecker(drive, root, currentVersionCode = 1).findUpdate())
    }

    @Test fun picksHighestNewerVersion() {
        withAppFolder(apk("v3", "xc-tv-wall-3.apk"), apk("v12", "xc-tv-wall-12.apk"), apk("o", "other.apk"))
        assertEquals("v12", UpdateChecker(drive, root, currentVersionCode = 5).findUpdate()?.id)
    }

    @Test fun alreadyUpToDate_returnsNull() {
        withAppFolder(apk("v3", "xc-tv-wall-3.apk"), apk("v12", "xc-tv-wall-12.apk"))
        assertNull(UpdateChecker(drive, root, currentVersionCode = 12).findUpdate())
    }

    @Test fun malformedNamesIgnored() {
        withAppFolder(apk("a", "xc-tv-wall-.apk"), apk("b", "xc-tv-wall-9.apk.zip"), apk("c", "xc-tv-wall-v9.apk"))
        assertNull(UpdateChecker(drive, root, currentVersionCode = 1).findUpdate())
    }

    @Test fun listingFailure_returnsNull() {
        withAppFolder(apk("v12", "xc-tv-wall-12.apk"))
        drive.listFails = true
        assertNull(UpdateChecker(drive, root, currentVersionCode = 1).findUpdate())
    }

    @Test fun offeredUpdateIsNotReturnedAgainButUnofferedIs() {
        withAppFolder(apk("v12", "xc-tv-wall-12.apk"))
        val checker = UpdateChecker(drive, root, currentVersionCode = 1)
        val first = checker.findUpdate()!!
        assertEquals("v12", checker.findUpdate()?.id) // not offered yet (e.g. download failed) -> retried
        checker.markOffered(first)
        assertNull(checker.findUpdate())
    }
}
