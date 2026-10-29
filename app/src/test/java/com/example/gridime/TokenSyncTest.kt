package com.example.gridime

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The files outside the app (the device kit, the README images and videos, the audit script) read the app's
 * colours from design/tokens.* (TokenExportTest). This fails if any of them types one of those colours by hand
 * again, because a hand-typed copy is the one that silently stays behind when Design.kt changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenSyncTest {
    private val root = File("..")
    /** Pages kept as a record of an approved look, not used to make anything. */
    private val archive = setOf("in-use.html", "tour-mockups.html", "hero.html")   // hero.html: remade in the launch phase

    @Test fun nothingOutsideTheAppTypesTheAppsColours() {
        fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
        fun rgb(c: Int) = "${(c shr 16) and 255},${(c shr 8) and 255},${c and 255}"
        val watched = listOf(hex(Design.BRAND_ACCENT), rgb(Design.BRAND_ACCENT), hex(Design.INK_DARK), hex(Design.TRACK), hex(Design.SURFACE_CARD)) +
            (Design.FACE_XBOX.values + Design.FACE_PLAYSTATION.values).map { hex(it) }
        val files = listOf(File(root, "tools/video/brand-kit"), File(root, "docs/visuals-src"), File(root, "tools/audit"))
            .flatMap { d -> d.listFiles { f -> f.extension in setOf("js", "py", "html", "css") }.orEmpty().toList() }
            .filter { it.name !in archive && !it.name.endsWith(".merged.js") }
        val problems = files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                watched.firstOrNull { line.contains(it, ignoreCase = true) }?.let { "${f.relativeTo(root)}:${i + 1}: types $it (read it from design/tokens.*)" }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("design/tokens.json is missing: run the tests once (TokenExportTest writes it)", File(root, "design/tokens.json").exists())
    }
}
