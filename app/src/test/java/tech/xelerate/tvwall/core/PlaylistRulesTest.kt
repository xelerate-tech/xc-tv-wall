package tech.xelerate.tvwall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistRulesTest {
    private fun f(name: String, mime: String = "image/png", id: String = name) =
        RemoteFile(id = id, name = name, mimeType = mime, modifiedTime = "2026-11-23T10:00:00Z")

    @Test fun kindOf_mapsSupportedMimeTypes() {
        assertEquals(ItemKind.IMAGE, PlaylistRules.kindOf("image/jpeg"))
        assertEquals(ItemKind.IMAGE, PlaylistRules.kindOf("image/png"))
        assertEquals(ItemKind.IMAGE, PlaylistRules.kindOf("image/webp"))
        assertEquals(ItemKind.SLIDES, PlaylistRules.kindOf("application/vnd.google-apps.presentation"))
        assertEquals(ItemKind.SLIDES, PlaylistRules.kindOf("application/pdf"))
        assertEquals(ItemKind.VIDEO, PlaylistRules.kindOf("video/mp4"))
        assertEquals(ItemKind.VIDEO, PlaylistRules.kindOf("video/quicktime"))
        assertEquals(ItemKind.VIDEO, PlaylistRules.kindOf("video/webm"))
        assertNull(PlaylistRules.kindOf("application/msword"))
    }

    @Test fun durationOverride_parsesSuffixBeforeExtension() {
        assertEquals(20_000L, PlaylistRules.durationOverrideMs("03_welcome__20s.png"))
        assertEquals(5_000L, PlaylistRules.durationOverrideMs("x__5S.jpg"))
        assertNull(PlaylistRules.durationOverrideMs("x__0s.png"))
        assertNull(PlaylistRules.durationOverrideMs("no_override.png"))
        assertNull(PlaylistRules.durationOverrideMs("a__20s_b.png"))
    }

    @Test fun durationOverride_worksForGoogleSlidesWithoutExtension() {
        assertEquals(15_000L, PlaylistRules.durationOverrideMs("02_team deck__15s"))
    }

    @Test fun plan_dropsUnderscoreFoldersAndUnsupported() {
        val planned = PlaylistRules.plan(listOf(
            f("_draft.png"), f("sub", PlaylistRules.FOLDER_MIME), f("notes.docx", "application/msword"), f("ok.png")))
        assertEquals(listOf("ok.png"), planned.map { it.file.name })
    }

    @Test fun plan_sortsNaturallyAndCaseInsensitively() {
        val planned = PlaylistRules.plan(listOf("10_c.png", "2_b.png", "1_a.png", "B.png", "a.png").map { f(it) })
        assertEquals(listOf("1_a.png", "2_b.png", "10_c.png", "a.png", "B.png"), planned.map { it.file.name })
    }

    @Test fun plan_breaksNameTiesById() {
        val planned = PlaylistRules.plan(listOf(f("same.png", id = "z"), f("same.png", id = "a")))
        assertEquals(listOf("a", "z"), planned.map { it.file.id })
    }

    @Test fun plan_assignsDurations() {
        val planned = PlaylistRules.plan(listOf(
            f("1.png"), f("2__20s.png"), f("3.mp4", "video/mp4"), f("4 deck", PlaylistRules.GOOGLE_SLIDES_MIME)))
        assertEquals(listOf(10_000L, 20_000L, null, 10_000L), planned.map { it.durationMs })
    }

    @Test fun plan_doesNotThrowOnNonAsciiDigits() {
        val planned = PlaylistRules.plan(listOf(f("\uFF11_x.png"), f("1_a.png")))
        assertEquals(2, planned.size)
    }
}
