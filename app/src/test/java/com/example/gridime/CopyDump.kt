package com.example.gridime

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/** Every Settings page's words (section, title, hint), for reviewing the copy. Touch build/copy-dump. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class CopyDump {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    @Test fun dump() {
        assumeTrue(File("build/copy-dump").exists())
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false, language2 = Language.GERMAN).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(800)
        val pages = SettingsActivity::class.java.getDeclaredMethod("getPages").apply { isAccessible = true }.invoke(a) as List<*>
        val content = SettingsActivity::class.java.getDeclaredField("content").apply { isAccessible = true }.get(a) as ViewGroup
        val out = StringBuilder()
        for (p in pages.indices) {
            a.tabsForCheck(p); idle(400)
            val title = pages[p]!!.javaClass.getDeclaredField("title").apply { isAccessible = true }.get(pages[p])
            out.append("\n## $title\n")
            for (i in 0 until content.childCount) {
                val v = content.getChildAt(i)
                if (v is SectionHeader) out.append("\n### ${(v as android.widget.TextView).text}\n")
                else if (v is ViewGroup) walk(v) { r -> out.append("- ${r.rowTitle} | ${r.rowHint}\n") }
            }
        }
        File("build/copy-dump/settings.md").writeText(out.toString())
    }
    private fun walk(v: View, f: (SettingRow) -> Unit) {
        if (v is SettingRow) { f(v); return }
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f)
    }
}
