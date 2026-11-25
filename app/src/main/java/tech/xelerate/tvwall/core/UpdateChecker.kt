package tech.xelerate.tvwall.core

/** Looks for a newer `xc-tv-wall-<versionCode>.apk` in the folder's `_app/` subfolder. */
class UpdateChecker(
    private val source: DriveSource,
    private val folderId: String,
    private val currentVersionCode: Int,
) {
    private val apkName = Regex("""^xc-tv-wall-(\d+)\.apk$""")
    private val offered = mutableSetOf<String>()

    /** Call once the installer was shown, so the same APK isn't offered again this session. */
    fun markOffered(file: RemoteFile) {
        offered += file.id + file.modifiedTime
    }

    /** The newest APK above the installed version, or null (also on any Drive error). */
    fun findUpdate(): RemoteFile? = try {
        source.listFolder(folderId)
            .firstOrNull { it.name == APP_FOLDER && it.mimeType == PlaylistRules.FOLDER_MIME }
            ?.let { source.listFolder(it.id) }
            .orEmpty()
            .mapNotNull { file -> apkName.find(file.name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to file } }
            .filter { (version, file) -> version > currentVersionCode && (file.id + file.modifiedTime) !in offered }
            .maxByOrNull { (version, _) -> version }
            ?.second
    } catch (e: Exception) {
        null
    }

    companion object { const val APP_FOLDER = "_app" }
}
