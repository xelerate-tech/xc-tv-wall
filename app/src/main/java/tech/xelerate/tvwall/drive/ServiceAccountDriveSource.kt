package tech.xelerate.tvwall.drive

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import tech.xelerate.tvwall.core.DriveSource
import tech.xelerate.tvwall.core.RemoteFile
import java.io.File
import java.io.InputStream

/** Read-only Drive access as a service account the folder is shared with. */
class ServiceAccountDriveSource(credentialsJson: InputStream) : DriveSource {
    private val drive: Drive

    init {
        val credentials = GoogleCredentials.fromStream(credentialsJson).createScoped(listOf(DriveScopes.DRIVE_READONLY))
        val adapter = HttpCredentialsAdapter(credentials)
        drive = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance()) { request ->
            adapter.initialize(request)
            request.connectTimeout = 30_000
            request.readTimeout = 120_000
        }.setApplicationName("xc-tv-wall").build()
    }

    override fun listFolder(folderId: String): List<RemoteFile> {
        val result = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        do {
            val page = drive.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setFields("nextPageToken, files(id,name,mimeType,modifiedTime)")
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setPageSize(1000)
                .setPageToken(pageToken)
                .execute()
            page.files.orEmpty().mapTo(result) {
                RemoteFile(it.id, it.name, it.mimeType, it.modifiedTime.toStringRfc3339())
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return result
    }

    override fun download(fileId: String, dest: File) {
        dest.outputStream().use { out ->
            drive.files().get(fileId).setSupportsAllDrives(true).executeMediaAndDownloadTo(out)
        }
    }

    override fun exportPdf(fileId: String, dest: File) {
        dest.outputStream().use { out ->
            drive.files().export(fileId, "application/pdf").executeMediaAndDownloadTo(out)
        }
    }
}
