package com.example.gridime

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the design system in one place, so a change in Design.kt reaches every screen and nothing drifts:
 *  - Settings, the tour and What's new take every text size from the scale (12 · 14 · 16 · 20 · 24 · 28), nothing under 12
 *  - no colour is typed anywhere but Design.kt, except the themes and highlight presets (Palette) and colour maths
 *  - every line weight is a token (icons, hairlines, focus rings); drawn icons come from Icons.kt
 *  - every animation's length is a token (Design.MOTION_*)
 * Each failure names the file and line, and what to use instead.
 */
class DesignTokensTest {
    private val src = File("src/main/java/com/example/gridime")
    private val screens = listOf("SettingsActivity.kt", "SettingsViews.kt", "SettingsChrome.kt", "TutorialActivity.kt", "WhatsNew.kt")

    @Test fun textSizesComeFromTheScale() {
        val literal = Regex("""textSize\s*=\s*[0-9.]+f|COMPLEX_UNIT_SP,\s*[0-9.]+f|_SP\s*=\s*[0-9.]+f""")
        val problems = screens.flatMap { f ->
            File(src, f).readLines().mapIndexedNotNull { i, line -> if (literal.containsMatchIn(line)) "$f:${i + 1}: ${line.trim()}" else null }
        }
        assertTrue("hand-typed text sizes (use Design.TYPE_*):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private val all get() = src.listFiles { f -> f.name.endsWith(".kt") }!!.sortedBy { it.name }
    private fun scan(files: List<File>, rule: Regex, allowed: (String, String) -> Boolean = { _, _ -> false }) = files.flatMap { f ->
        f.readLines().mapIndexedNotNull { i, line ->
            val code = line.substringBefore("//")
            if (rule.containsMatchIn(code) && !allowed(f.name, code)) "${f.name}:${i + 1}: ${line.trim()}" else null
        }
    }

    @Test fun coloursAreOnlyTypedInTheDesignFile() {
        // a hex colour, a built colour, or a see-through amount typed by hand (withAlpha(c, 0x59))
        val colour = Regex("""parseColor\("#|0x[0-9A-Fa-f]{8}(?!\.toInt\(\) and)|Color\.(argb|rgb)\(|withAlpha\([^,]+,\s*(0x[0-9A-Fa-f]+|[0-9]+)\s*\)""")
        val problems = scan(all.filter { it.name != "Design.kt" }, colour) { file, code ->
            (file == "KeyboardSettings.kt" && ("Tone(" in code || "edge = " in code || "to \"#" in code || "Color.rgb(" in code)) ||  // the presets, and colour maths
                (file == "Backdrop.kt" && "Color.argb(" in code) ||                                   // colour maths (OKLCH)
                "0x00FFFFFF" in code || "0xFF000000" in code                                         // masks, not colours
        }
        assertTrue("colours typed outside Design.kt (add a token there and use it):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun lineWeightsAreTokens() {
        val weight = Regex("""strokeWidth\s*=\s*([0-9.]+f?\s*\*\s*dp|u\([0-9.]+f\)|[0-9.]+f\s*\*)""")
        val problems = scan(all, weight)
        assertTrue("hand-typed line weights (use Design.STROKE_* or Icons.line):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun animationLengthsAreTokens() {
        val length = Regex("""(setDuration|duration\s*=)\s*\(?[0-9]+L?""")
        val problems = scan(all, length)
        assertTrue("hand-typed animation lengths (use Design.MOTION_*):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun theScaleNeverGoesUnderTheMinimum() {
        val sizes = listOf(Design.TYPE_SECTION, Design.TYPE_CAPTION, Design.TYPE_ROW, Design.TYPE_TITLE, Design.TYPE_HEADER, Design.TYPE_DISPLAY, Design.TYPE_CHIP)
        assertTrue("a size under ${Design.TYPE_MIN}: $sizes", sizes.all { it >= Design.TYPE_MIN })
    }
}
