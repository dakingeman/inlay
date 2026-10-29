package com.example.gridime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Design.kt is the one place a colour is set. Everything outside the app reads it from here: the device
 * kit (tools/video/brand-kit), the README images and videos (docs/visuals-src) and the audit scripts.
 * This test writes Design.kt's values to design/tokens.json, tokens.js and tokens.css on every run, so a
 * change in Design.kt reaches them by itself. Then rebuild the device pictures (build.sh) and the images.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenExportTest {
    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    private fun hexA(c: Int) = if ((c ushr 24) == 0xFF) hex(c) else "#%08X".format(((c and 0xFFFFFF) shl 8) or (c ushr 24))   // CSS order: RRGGBBAA

    private fun tokens(): JSONObject = JSONObject().apply {
        put("brandAccent", hex(Design.BRAND_ACCENT))
        put("inkDark", hex(Design.INK_DARK))
        put("inkLight", hex(Design.INK_LIGHT))
        put("surfaceBg", hex(Design.SURFACE_BG))
        put("surfaceCard", hex(Design.SURFACE_CARD))
        put("surfaceRaised", hex(Design.SURFACE_RAISED))
        put("edgeCard", hexA(Design.EDGE_CARD))
        put("edgeDivider", hexA(Design.EDGE_DIVIDER))
        put("textPrimary", hex(Design.TEXT_PRIMARY))
        put("textSecondary", hex(Design.TEXT_SECONDARY))
        put("track", hex(Design.TRACK))
        put("backdrop", org.json.JSONArray(listOf(hex(Design.BACKDROP_TOP), hex(Design.BACKDROP_MID), hex(Design.BACKDROP_BOTTOM))))
        put("badgePlate", hexA(Design.BADGE_PLATE))
        put("badgeEdge", hexA(Design.BADGE_EDGE))
        put("face", JSONObject().apply {
            put("xbox", JSONObject(Design.FACE_XBOX.mapValues { hex(it.value) }))
            put("ps", JSONObject(Design.FACE_PLAYSTATION.mapValues { hex(it.value) }))
        })
        put("accents", JSONObject(Palette.accents.associate { it.first to hex(it.second) }))
    }

    @Test fun writeTokensForEverythingOutsideTheApp() {
        val t = tokens()
        val dir = File("../design").apply { mkdirs() }
        val note = "Written by TokenExportTest from app/src/main/java/com/example/gridime/Design.kt. Don't edit: change Design.kt and run the tests."
        File(dir, "tokens.json").writeText(t.toString(2) + "\n")
        File(dir, "tokens.js").writeText("// $note\nvar TOKENS = ${t.toString(2)};\nif (typeof module !== 'undefined') module.exports = TOKENS;\n")
        val css = buildString {
            append("/* $note */\n:root{\n")
            listOf("brandAccent" to "teal", "inkDark" to "ink-dark", "inkLight" to "paper", "surfaceBg" to "black", "surfaceCard" to "card",
                "surfaceRaised" to "raised", "edgeCard" to "edge", "edgeDivider" to "divider", "textPrimary" to "text1",
                "textSecondary" to "text2", "track" to "track", "badgePlate" to "badge-plate", "badgeEdge" to "badge-edge")
                .forEach { (k, v) -> append("  --$v:${t.getString(k)};\n") }
            append("  --ink:${hex(Design.BACKDROP_BOTTOM)};\n")
            append("}\n")
        }
        File(dir, "tokens.css").writeText(css)
        // The brand colour is the "Inlay" preset: one value, two names
        assertEquals(Design.BRAND_ACCENT, Palette.accents.first { it.first == "Inlay" }.second)
    }
}
