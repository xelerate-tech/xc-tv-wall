package tech.xelerate.tvwall.core

import java.math.BigInteger

/** The rules the team follows when filling the Drive folder. */
object PlaylistRules {
    const val DEFAULT_DURATION_MS = 10_000L
    const val FOLDER_MIME = "application/vnd.google-apps.folder"
    const val GOOGLE_SLIDES_MIME = "application/vnd.google-apps.presentation"

    private val kinds = mapOf(
        "image/jpeg" to ItemKind.IMAGE,
        "image/png" to ItemKind.IMAGE,
        "image/webp" to ItemKind.IMAGE,
        GOOGLE_SLIDES_MIME to ItemKind.SLIDES,
        "application/pdf" to ItemKind.SLIDES,
        "video/mp4" to ItemKind.VIDEO,
        "video/quicktime" to ItemKind.VIDEO,
        "video/webm" to ItemKind.VIDEO,
    )

    // `__20s` right before the extension, or at the end for Google Slides (which have no extension).
    private val durationSuffix = Regex("""__(\d+)s(\.[^.]+)?$""", RegexOption.IGNORE_CASE)

    fun kindOf(mimeType: String): ItemKind? = kinds[mimeType]

    fun durationOverrideMs(name: String): Long? {
        val seconds = durationSuffix.find(name)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return if (seconds > 0) seconds * 1000 else null
    }

    fun plan(files: List<RemoteFile>): List<PlannedItem> =
        files.asSequence()
            .filterNot { it.name.startsWith("_") }
            .mapNotNull { file ->
                val kind = kindOf(file.mimeType) ?: return@mapNotNull null
                val duration = if (kind == ItemKind.VIDEO) null
                else durationOverrideMs(file.name) ?: DEFAULT_DURATION_MS
                PlannedItem(file, kind, duration)
            }
            .sortedWith { a, b -> naturalCompare(a.file.name, b.file.name).takeIf { it != 0 } ?: a.file.id.compareTo(b.file.id) }
            .toList()

    private val chunk = Regex("""\d+|\D+""")

    /** Compares names so that "2_x" sorts before "10_x", ignoring case for text. */
    internal fun naturalCompare(a: String, b: String): Int {
        val ca = chunk.findAll(a).map { it.value }.toList()
        val cb = chunk.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]; val y = cb[i]
            val cmp = if (x[0] in '0'..'9' && y[0] in '0'..'9') BigInteger(x).compareTo(BigInteger(y))
            else x.compareTo(y, ignoreCase = true)
            if (cmp != 0) return cmp
        }
        return ca.size.compareTo(cb.size)
    }
}
