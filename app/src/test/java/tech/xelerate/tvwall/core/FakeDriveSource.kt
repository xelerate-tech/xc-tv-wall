package tech.xelerate.tvwall.core

import java.io.File
import java.io.IOException

class FakeDriveSource : DriveSource {
    /** Children per folder id. */
    val folders = mutableMapOf<String, MutableList<RemoteFile>>()
    val contents = mutableMapOf<String, ByteArray>()
    val failOn = mutableSetOf<String>()
    var listFails = false
    var downloads = 0
    var exports = 0

    fun put(folderId: String, file: RemoteFile, bytes: ByteArray = file.id.toByteArray()) {
        folders.getOrPut(folderId) { mutableListOf() }.apply { removeAll { it.id == file.id }; add(file) }
        contents[file.id] = bytes
    }

    fun remove(folderId: String, id: String) { folders[folderId]?.removeAll { it.id == id } }

    override fun listFolder(folderId: String): List<RemoteFile> {
        if (listFails) throw IOException("network down")
        return folders[folderId].orEmpty().toList()
    }

    override fun download(fileId: String, dest: File) {
        if (fileId in failOn) throw IOException("download failed: $fileId")
        downloads++
        dest.writeBytes(contents.getValue(fileId))
    }

    override fun exportPdf(fileId: String, dest: File) {
        if (fileId in failOn) throw IOException("export failed: $fileId")
        exports++
        dest.writeBytes("%PDF ".toByteArray() + contents.getValue(fileId))
    }
}
