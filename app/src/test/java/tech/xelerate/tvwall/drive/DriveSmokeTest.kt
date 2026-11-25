package tech.xelerate.tvwall.drive

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Talks to real Google Drive. Skipped unless TVWALL_SA_JSON (path to the service-account key)
 * and TVWALL_FOLDER_ID are set.
 */
class DriveSmokeTest {
    @Test fun listsSharedFolder() {
        val keyPath = System.getenv("TVWALL_SA_JSON")
        val folderId = System.getenv("TVWALL_FOLDER_ID")
        assumeTrue(!keyPath.isNullOrBlank() && !folderId.isNullOrBlank())
        val files = File(keyPath!!).inputStream().use { ServiceAccountDriveSource(it) }.listFolder(folderId!!)
        println("Drive folder has ${files.size} entries: " + files.joinToString { "${it.name} (${it.mimeType})" })
    }
}
