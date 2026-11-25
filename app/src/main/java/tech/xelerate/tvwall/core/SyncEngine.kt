package tech.xelerate.tvwall.core

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

sealed interface SyncResult {
    data object Unchanged : SyncResult
    data class Updated(val manifest: Manifest, val skipped: List<String> = emptyList()) : SyncResult
    data class Failed(val error: Throwable) : SyncResult
}

/**
 * Mirrors one Drive folder into [rootDir]. Each sync writes a new manifest atomically; a file that
 * fails to download is left out (or kept at its previous version) without blocking the others.
 * A listing failure leaves everything untouched. Old media is only deleted via [releaseUnused].
 */
class SyncEngine(
    private val source: DriveSource,
    private val folderId: String,
    private val rootDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val manifestFile = File(rootDir, "manifest.json")
    private val mediaDir = File(rootDir, "media")
    private val stagingDir = File(rootDir, "staging")

    @Synchronized
    fun currentManifest(): Manifest =
        try {
            if (manifestFile.exists()) json.decodeFromString<Manifest>(manifestFile.readText()) else Manifest.EMPTY
        } catch (e: Exception) {
            Manifest.EMPTY
        }

    @Synchronized
    fun sync(): SyncResult {
        val old = currentManifest()
        mediaDir.mkdirs()
        stagingDir.mkdirs()
        clearStaging()
        return try {
            val planned = PlaylistRules.plan(source.listFolder(folderId))
            val reusable = old.items.associateBy { it.fileId }
            val staged = mutableListOf<Pair<File, File>>() // staging file -> final media file
            val skipped = mutableListOf<String>()
            val items = planned.mapNotNull { p ->
                val existing = reusable[p.file.id]?.takeIf { File(it.path).exists() }
                if (existing != null && existing.modifiedTime == p.file.modifiedTime) {
                    existing.copy(name = p.file.name, durationMs = p.durationMs)
                } else {
                    val fileName = mediaName(p.file)
                    val stagingFile = File(stagingDir, fileName)
                    try {
                        if (p.file.mimeType == PlaylistRules.GOOGLE_SLIDES_MIME) source.exportPdf(p.file.id, stagingFile)
                        else source.download(p.file.id, stagingFile)
                        val target = File(mediaDir, fileName)
                        staged += stagingFile to target
                        LocalItem(p.file.id, p.file.name, p.kind, p.file.modifiedTime, target.absolutePath, p.durationMs)
                    } catch (e: Exception) {
                        // One bad file (too big to export, download blocked, timeout) must not block the rest:
                        // keep the previous version if there is one, otherwise leave it out and retry next sync.
                        stagingFile.delete()
                        skipped += "${p.file.name}: ${e.message}"
                        existing?.copy(name = p.file.name, durationMs = p.durationMs)
                    }
                }
            }
            val manifest = Manifest(items)
            if (staged.isEmpty() && manifest == old) return SyncResult.Unchanged

            staged.forEach { (from, to) ->
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            writeManifest(manifest)
            SyncResult.Updated(manifest, skipped)
        } catch (e: Exception) {
            clearStaging()
            SyncResult.Failed(e)
        }
    }

    private fun writeManifest(manifest: Manifest) {
        val tmp = File(rootDir, "manifest.json.tmp")
        tmp.writeText(json.encodeToString(Manifest.serializer(), manifest))
        Files.move(tmp.toPath(), manifestFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Deletes media used neither by [inUse] (what the player is looping now) nor by the latest
     * synced manifest. Call it whenever the player switches to a new manifest.
     */
    @Synchronized
    fun releaseUnused(inUse: Manifest) {
        val keep = (inUse.items + currentManifest().items).map { File(it.path).name }.toSet()
        mediaDir.listFiles().orEmpty().filter { it.name !in keep }.forEach { it.delete() }
    }

    private fun clearStaging() {
        stagingDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
    }

    private fun mediaName(file: RemoteFile): String {
        val hash = MessageDigest.getInstance("SHA-1").digest(file.modifiedTime.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(8)
        return "${file.id}_$hash.${extensionFor(file.mimeType)}"
    }

    private fun extensionFor(mime: String): String = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "video/quicktime" -> "mov"
        "video/webm" -> "webm"
        else -> "pdf" // Google Slides export and PDFs
    }
}
