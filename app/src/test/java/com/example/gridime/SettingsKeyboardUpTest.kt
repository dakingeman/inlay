package com.example.gridime

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
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
 * With the keyboard open in Settings (Y), the row you're on is never hidden: stepping down through every row of
 * every tab, the focused row always shows whole between the folded tabs and the top of the keyboard (a row taller
 * than all that room shows its bottom, where its control is). On short screens the hints step aside for this. Checked on a
 * wide handheld, a small vertical handheld and a phone (audit, 30 Oct 2026: rows move as the Settings overhaul goes on).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsKeyboardUpTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun shot(a: android.app.Activity, name: String) {
        val v = a.window.decorView; val dir = java.io.File("build/kbup").apply { mkdirs() }
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(b)); java.io.File(dir, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun check(where: String) {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false).save(context)
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(600)
        fun field(n: String) = SettingsActivity::class.java.getDeclaredField(n).apply { isAccessible = true }
        fun press(key: Int, wait: Long) {
            val t = SystemClock.uptimeMillis()
            s.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0)); s.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0)); idle(wait)
        }
        fun top(v: View) = IntArray(2).also { v.getLocationInWindow(it) }[1]
        val problems = mutableListOf<String>()
        @Suppress("UNCHECKED_CAST")
        (field("focusableRows").get(s) as List<View>).first().requestFocusFromTouch(); idle(300)
        press(KeyEvent.KEYCODE_BUTTON_Y, 1500)                     // the keyboard slides up
        val keyboard = field("preview").get(s) as View
        for (tab in 0 until 6) {
            @Suppress("UNCHECKED_CAST")
            val rows = field("focusableRows").get(s) as List<View>
            for (i in rows.indices) {
                val row = s.window.decorView.findFocus() ?: break
                // the folded tabs end where the line of bars ends (the tab bar slid up, its bottom left showing)
                val tabs = field("tabs").get(s) as View
                val ceiling = top(tabs) + tabs.height
                val floor = top(keyboard)
                val rowTop = top(row); val rowBottom = rowTop + row.height
                val name = (row as? SettingRow)?.let { r -> (0 until r.childCount).map { r.getChildAt(it) }.filterIsInstance<android.widget.LinearLayout>()
                    .firstOrNull()?.getChildAt(0)?.let { (it as? android.widget.TextView)?.text } } ?: "row $i"
                val before = problems.size
                if (rowBottom > floor + 1) problems += "$where, tab $tab, \"$name\": hidden behind the keyboard by ${rowBottom - floor} px"
                // A row taller than the whole room (the setup card) can't show whole: its bottom, where its button or
                // control is, must show; every other row shows whole
                if (rowTop < ceiling - 1 && row.height <= floor - ceiling) problems += "$where, tab $tab, \"$name\": under the tabs by ${ceiling - rowTop} px (row ${row.height} px, room ${floor - ceiling} px)"
                if (problems.size > before) shot(s, "$where-$tab-$i")
                if (i < rows.lastIndex) press(KeyEvent.KEYCODE_DPAD_DOWN, 450)
            }
            press(KeyEvent.KEYCODE_BUTTON_R1, 700)
        }
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun handheld() = check("handheld")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun vertical() = check("vertical")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun phone() = check("phone")
}
