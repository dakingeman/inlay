package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.ScrollView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Settings shows where you are on a page (1.3, Emre, 8 Nov): a slim bar in the right margin, its lit part as long as
 * the share of the page on screen, at the top on top and at the bottom at the end. It never sits on a card, on any
 * screen; on pages that fit, there's none. Writes pictures to build/page-position when that folder exists.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PagePositionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun field(a: Any, n: String) = SettingsActivity::class.java.getDeclaredField(n).apply { isAccessible = true }.get(a)

    private fun run(name: String) {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(800)
        val look = a.findIndex().first { it.title == "Highlight motion" }.page
        a.tabsForCheck(look); idle(600)
        val scroll = field(a, "scroll") as ScrollView
        val content = field(a, "content") as View
        val dir = File("build/page-position").takeIf { it.exists() }
        val page = scroll.getChildAt(0)
        val travel = page.height - (scroll.height - scroll.paddingTop)
        assertTrue("$name: Look is longer than the screen", travel > 0)
        val cardsRight = content.right + (content.parent as View).left
        var lastTop = -1f
        for ((i, f) in listOf(0f, 0.5f, 1f).withIndex()) {
            scroll.scrollTo(0, (travel * f).toInt()); idle(200)
            val b = Bitmap.createBitmap(a.window.decorView.width, a.window.decorView.height, Bitmap.Config.ARGB_8888)
            a.window.decorView.draw(Canvas(b))
            dir?.let { d -> File(d, "$name-$i.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            val bar = a.positionForCheck
            assertNotNull("$name: the bar shows", bar)
            assertTrue("$name: in the margin, clear of the cards (${bar!!.left} vs ${cardsRight})", bar.left >= cardsRight + 1)
            assertTrue("$name: on the screen", bar.right <= scroll.width)
            assertTrue("$name: it moves down the page", bar.top > lastTop); lastTop = bar.top
        }
        // a page that fits has none
        val short = (0 until a.findIndex().map { it.page }.distinct().size).firstOrNull { p ->
            a.tabsForCheck(p); idle(400); scroll.getChildAt(0).height <= scroll.height - scroll.paddingTop }
        if (short != null) { scroll.invalidate(); a.window.decorView.draw(Canvas(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))); 
            val b = Bitmap.createBitmap(a.window.decorView.width, a.window.decorView.height, Bitmap.Config.ARGB_8888); a.window.decorView.draw(Canvas(b))
            assertNull("$name: a page that fits shows no bar", a.positionForCheck) }
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = run("odin")
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun upright() = run("upright")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun small43() = run("small43")
    @Test @Config(sdk = [34], qualifiers = "w620dp-h540dp-land-xhdpi") fun square() = run("square")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tv() = run("tv")
}
