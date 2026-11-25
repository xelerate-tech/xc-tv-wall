package tech.xelerate.tvwall.core

import kotlinx.serialization.Serializable

/** A file as listed in the Drive folder. `modifiedTime` is Drive's RFC 3339 string. */
data class RemoteFile(val id: String, val name: String, val mimeType: String, val modifiedTime: String)

@Serializable
enum class ItemKind { IMAGE, SLIDES, VIDEO }

/** A remote file that will be played, with its display duration (null = play video to the end). */
data class PlannedItem(val file: RemoteFile, val kind: ItemKind, val durationMs: Long?)

/** A file that has been downloaded and can be played. `path` is absolute. */
@Serializable
data class LocalItem(
    val fileId: String,
    val name: String,
    val kind: ItemKind,
    val modifiedTime: String,
    val path: String,
    val durationMs: Long?,
)

/** The last fully-synced playlist, persisted as `manifest.json`. */
@Serializable
data class Manifest(val items: List<LocalItem>) {
    companion object { val EMPTY = Manifest(emptyList()) }
}
