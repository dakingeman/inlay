package com.example.gridime

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Updating to 1.3 (Emre, 8 Nov): people who had 1.2 may never hear of the button setup, so the Your buttons row
 * carries the spark and says what's new until it's visited. Only for people updating with a controller: a new user
 * (who just did the tour) and a TV remote get nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class UpdaterButtonsSparkTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Suppress("UNCHECKED_CAST")
    private fun rows(s: SettingsActivity) = SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(s) as List<SettingRow>
    private fun has(v: android.view.View, t: String): Boolean = (v is android.widget.TextView && v.text.toString().startsWith(t)) ||
        (v is android.view.ViewGroup && (0 until v.childCount).any { has(v.getChildAt(it), t) })

    private fun open(seen: String, pic: PictureChoice = PictureChoice.HANDHELD): Pair<SettingsActivity, SettingRow?> {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = seen, picture = pic).save(context)
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(800)
        return s to rows(s).firstOrNull { has(it, ButtonRoles.ROW) }
    }

    @Test fun updaterSeesTheSpark() {
        val (s, row) = open("1.2.0")
        assertTrue("the Your buttons row is on Device", row != null)
        assertTrue("it carries the spark", row!!.newMark)
        assertTrue("and says what's new", has(row, "New: show Inlay your four face buttons"))
        assertTrue("remembered", KeyboardSettings.load(context).buttonsRowNew)
        // visited, then left: the spark goes for good
        row.requestFocusFromTouch(); idle(300); rows(s).first { it !== row }.requestFocusFromTouch(); idle(300)
        assertEquals(false, row.newMark)
        assertEquals(false, KeyboardSettings.load(context).buttonsRowNew)
    }

    @Test fun newUserGetsNothing() {
        val (_, row) = open(WhatsNew.VERSION)
        assertEquals(false, row?.newMark ?: false)
        assertEquals(false, KeyboardSettings.load(context).buttonsRowNew)
    }

    @Test fun remoteGetsNothing() {
        val (_, row) = open("1.2.0", PictureChoice.REMOTE)
        assertEquals(false, row?.newMark ?: false)
        assertEquals(false, KeyboardSettings.load(context).buttonsRowNew)
    }

    @Test fun versions() {
        assertTrue(WhatsNew.isBefore("1.2.0", 1, 3)); assertTrue(WhatsNew.isBefore("", 1, 3)); assertTrue(WhatsNew.isBefore("1.1.1", 1, 3))
        assertEquals(false, WhatsNew.isBefore("1.3.0", 1, 3)); assertEquals(false, WhatsNew.isBefore("1.10.0", 1, 3)); assertEquals(false, WhatsNew.isBefore("2.0", 1, 3))
    }
}
