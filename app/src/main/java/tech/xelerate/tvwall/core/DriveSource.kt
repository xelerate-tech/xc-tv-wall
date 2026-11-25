package tech.xelerate.tvwall.core

import java.io.File

/** The few Google Drive operations the app needs. */
interface DriveSource {
    /** Direct, non-trashed children of [folderId], including subfolders. */
    fun listFolder(folderId: String): List<RemoteFile>
    fun download(fileId: String, dest: File)
    /** Google Slides only: export the deck as a PDF. */
    fun exportPdf(fileId: String, dest: File)
}
