package com.example.gridime

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Find a setting (Start in Settings) can reach every row on every tab, and the everyday words land where people
 * mean: "bigger" on Keyboard size, "transparent" on the see-through theme, "numbers" on the number row, "r3" on
 * taking a word. Every word list in SettingsFind still names a real row (so a renamed row can't leave Find behind).
 * And the whole trip works with the controller: Start, type, Start, and the right row on the right tab has the focus.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class SettingsFindTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun open(s: KeyboardSettings): SettingsActivity {
        s.save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(600); return a
    }
    private val base = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false)

    @Test fun everyRowCanBeFoundByItsName() {
        val problems = mutableListOf<String>()
        val clear = Palette.tones.indexOfFirst { it.clear }
        val titles = HashSet<String>()
        for ((tag, s) in listOf("normal" to base, "retro, clear and swapped" to base.copy(retroUnlocked = true, toneIndex = clear, swapFaceButtons = true))) {
            val a = open(s)
            val index = a.findIndex()
            titles += index.map { it.title.substringBefore(" · ") }
            for (e in index) {
                val found = SettingsFind.search(index, e.title, 4)
                if (found.none { it.title == e.title && it.page == e.page && it.row == e.row })
                    problems += "$tag: \"${e.title}\" (${e.where}) isn't found by its own name; found ${found.map { it.title }}"
            }
            a.finish(); idle(200)
        }
        for (name in SettingsFind.WORDS.keys) if (name !in titles) problems += "SettingsFind.WORDS names \"$name\", which no row is called"
        println("Find: ${titles.size} rows, ${problems.size} problems")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun everydayWordsLandWherePeopleMean() {
        val clear = Palette.tones.indexOfFirst { it.clear }
        val a = open(base.copy(toneIndex = clear))
        val index = a.findIndex()
        val cases = listOf("bigger" to "Keyboard size", "transparent" to "Theme", "numbers" to "Number row", "r3" to "Take the first suggested word",
            "clipboard" to "Remember copied text", "haptics" to "Vibration", "nintendo" to "Your buttons", "split" to "Split",
            "accents" to "Hold for accents", "obtainium" to "Get updates automatically", "test" to "Try your buttons",
            // slips and whole questions
            "vibraton" to "Vibration", "keybord size" to "Keyboard size", "how do i make the keys bigger" to "Keyboard size",
            "transparant" to "Theme", "where is the number row" to "Number row")
        val problems = cases.mapNotNull { (q, want) ->
            val top = SettingsFind.search(index, q, 4).map { it.title.substringBefore(" · ") }
            if (want !in top.take(2)) "\"$q\" should find \"$want\" first or second; got $top" else null
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun factsAndTestRowsStayOut() {
        val index = open(base).findIndex().map { it.title }
        for (t in listOf("Version", "Licence and credits")) assertTrue("\"$t\" shouldn't be a Find result", t !in index)
        assertTrue("test build rows shouldn't be Find results", index.none { it.startsWith("Test build") })
        val popular = SettingsFind.search(open(base).findIndex(), "", 6).map { it.title }
        assertEquals("before typing, the popular rows", SettingsFind.POPULAR, popular)
    }

    @Test fun bHidesTheKeyboardThenTheDpadBrowses() {
        val a = open(base)
        @Suppress("UNCHECKED_CAST")
        (SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(a) as List<View>).first().requestFocusFromTouch(); idle(300)
        fun press(key: Int, wait: Long = 150) {
            val t = SystemClock.uptimeMillis()
            a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0)); idle(wait)
        }
        fun field(n: String) = SettingsActivity::class.java.getDeclaredField(n).apply { isAccessible = true }
        press(KeyEvent.KEYCODE_BUTTON_START, 1200)          // Find, with the popular rows
        press(KeyEvent.KEYCODE_BUTTON_B, 1200)              // B: the keyboard goes, Find stays
        assertTrue("B should hide the keyboard, not close Find", field("finding").getBoolean(a))
        assertTrue("the keyboard should be down", !(field("settings").get(a) as KeyboardSettings).showPreview)
        press(KeyEvent.KEYCODE_DPAD_DOWN)                   // second popular row: Theme
        press(KeyEvent.KEYCODE_BUTTON_A, 1600)
        val focused = a.window.decorView.findFocus()
        val title = (focused as? SettingRow)?.rowTitle ?: generateSequence(focused?.parent) { it.parent }.filterIsInstance<SettingRow>().firstOrNull()?.rowTitle
        assertEquals("down, A from the popular rows", SettingsFind.POPULAR[1], title)
    }

    @Test fun yTypesASpaceWhileTyping() {
        val a = open(base)
        @Suppress("UNCHECKED_CAST")
        (SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(a) as List<View>).first().requestFocusFromTouch(); idle(300)
        fun press(key: Int, wait: Long = 150) {
            val t = SystemClock.uptimeMillis()
            a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0)); idle(wait)
        }
        fun field(n: String) = SettingsActivity::class.java.getDeclaredField(n).apply { isAccessible = true }
        press(KeyEvent.KEYCODE_BUTTON_START, 1200)
        press(KeyEvent.KEYCODE_BUTTON_Y, 300)
        assertEquals("Y types a space", " ", field("findQuery").get(a))
        assertTrue("Y keeps the keyboard up", (field("settings").get(a) as KeyboardSettings).showPreview && !field("findBrowsing").getBoolean(a))
    }

    @Test fun startTypeStartGoesToTheRow() {
        val a = open(base)
        @Suppress("UNCHECKED_CAST")
        (SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(a) as List<View>).first().requestFocusFromTouch(); idle(300)
        fun press(key: Int, wait: Long = 120) {
            val t = SystemClock.uptimeMillis()
            a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0)); idle(wait)
        }
        press(KeyEvent.KEYCODE_BUTTON_START, 1200)                      // Find opens, the keyboard comes up
        val kb = SettingsActivity::class.java.getDeclaredField("preview").apply { isAccessible = true }.get(a) as KeyGridView
        // type "size" on the real keyboard: step to each letter, then A
        for (ch in "size") {
            var guard = 0
            while (kb.stepTowards(ch.toString()) == false && guard++ < 40) idle(5)
            press(KeyEvent.KEYCODE_BUTTON_A)
        }
        press(KeyEvent.KEYCODE_BUTTON_START, 1500)                      // go there
        val focused = a.window.decorView.findFocus()
        val title = (focused as? SettingRow)?.rowTitle ?: (generateSequence(focused?.parent) { it.parent }.filterIsInstance<SettingRow>().firstOrNull()?.rowTitle)
        assertEquals("Start, \"size\", Start should land on Keyboard size", "Keyboard size", title)
        val finding = SettingsActivity::class.java.getDeclaredField("finding").apply { isAccessible = true }.getBoolean(a)
        assertTrue("Find should close after going there", !finding)
    }
}
