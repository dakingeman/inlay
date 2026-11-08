package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Voice typing (1.3): a microphone in the top row only when there's a voice app, and the row still adds up. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class VoiceKeyTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun theRowAddsUpWithAndWithoutTheMicrophone() {
        for (all in listOf(false, true)) for (split in listOf(false, true)) for (words in listOf(emptyList(), listOf("one", "two", "three"))) {
            val off = Layouts.toolRow(all, words, split, voice = false)
            val on = Layouts.toolRow(all, words, split, voice = true)
            val where = "all=$all split=$split words=${words.size}"
            assertEquals("$where: no microphone without a voice app", 0, off.count { it.tool == Tool.VOICE })
            assertEquals("$where: one microphone with one", 1, on.count { it.tool == Tool.VOICE })
            assertEquals("$where: the same width", off.sumOf { it.weight.toDouble() }, on.sumOf { it.weight.toDouble() }, 0.001)
            assertTrue("$where: Paste keeps room", on.filter { it.tool == Tool.PASTE }.all { it.weight >= 1.4f })
        }
    }

    @Test fun itDraws() {
        val v = KeyGridView(context)
        v.applySettings(KeyboardSettings(tutorialDone = true, voiceKey = true))
        val w = (827 * context.resources.displayMetrics.density).toInt()
        v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, w, v.measuredHeight)
        val b = Bitmap.createBitmap(w, v.measuredHeight, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
        if (File("build/visuals-size").exists()) File("build/voice-key.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** With no voice app yet, the microphone opens Settings right at Voice typing, which says how to get one. */
    @Test fun noVoiceAppLeadsToTheSetUpRow() {
        assertTrue("the microphone is on by default", KeyboardSettings().voiceKey)
        assertEquals("no voice app here", null, VoiceInput.ready(context))
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION).save(context)
        val c = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val a = c.get()
        // a controller is in use, not touch (as on the device): then the keyboard's microphone opens Settings at the row
        @Suppress("UNCHECKED_CAST")
        (SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(a) as List<android.view.View>).first().requestFocusFromTouch()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
        c.newIntent(android.content.Intent(context, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_GO_TO, "Voice typing"))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2000))
        assertTrue("in the search index: ${a.findIndex().filter { "oice" in it.title }.map { it.title }}", a.findIndex().any { it.title == "Voice typing" })
        var v: android.view.View? = a.window.decorView.findFocus()
        while (v != null && v !is SettingRow) v = v.parent as? android.view.View
        assertEquals("opened on Voice typing (focus: ${a.currentFocus})", "Voice typing", (v as? SettingRow)?.rowTitle)
    }

    /** The same when Settings wasn't open yet: it starts on Typing with Voice typing lit. */
    @Test fun aFreshSettingsOpensAtTheRow() {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java,
            android.content.Intent(context, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_GO_TO, "Voice typing")).setup().get()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2500))
        var v: android.view.View? = a.window.decorView.findFocus()
        while (v != null && v !is SettingRow) v = v.parent as? android.view.View
        assertEquals("opened on Voice typing", "Voice typing", (v as? SettingRow)?.rowTitle)
        val page = SettingsActivity::class.java.getDeclaredField("currentPage").apply { isAccessible = true }.getInt(a)
        assertEquals("on the Typing tab", a.findIndex().first { it.title == "Voice typing" }.page, page)
    }

    /**
     * HARD RULE (Emre, 7 Nov): the keyboard shown in Settings is always what the real keyboard will look like. Settings'
     * preview and a keyboard built from the same settings have the same top row, microphone included or not.
     */
    @Test fun settingsPreviewMatchesTheRealKeyboard() {
        for (voice in listOf(true, false)) {
            val s = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, voiceKey = voice)
            s.save(context)
            val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            val preview = SettingsActivity::class.java.getDeclaredField("preview").apply { isAccessible = true }.get(a) as KeyGridView
            val real = KeyGridView(context).apply { applySettings(KeyboardSettings.load(context).forControls(Controls.of(context, KeyboardSettings.load(context)))) }
            assertEquals("voice=$voice: Settings shows the real top row", real.toolsForCheck(), preview.toolsForCheck())
            assertEquals("voice=$voice: microphone", voice, preview.toolsForCheck().contains(Tool.VOICE))
            a.finish()
        }
    }
}
