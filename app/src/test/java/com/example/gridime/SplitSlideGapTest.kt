package com.example.gridime

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * What's new, Split (Emre, 8 Nov): the slide's gap between the halves is the real keyboard's gap, as a share of its
 * width, on screens where the tour draws bigger (TourScale: the Odin's 835 × 469 dp draws 10 % bigger) as well as
 * where it doesn't. The real keyboard is built here as the keyboard service builds it: the device's own scale, the
 * display's full width.
 */
@RunWith(RobolectricTestRunner::class)
class SplitSlideGapTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun check() {
        val s = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, split = true)
        s.save(context)
        val real = KeyGridView(context).apply {
            applySettings(s.forControls(Controls.of(context, s)))
            val w = context.resources.displayMetrics.widthPixels
            measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            layout(0, 0, w, measuredHeight)
        }
        val (l, r) = real.splitPanelsForCheck()
        val realShare = (r.left - l.right) / real.width
        val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        idle(1500)
        val now = SystemClock.uptimeMillis()
        t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
        idle(1500)
        val slide = t.splitGapShareForCheck()
        assertNotNull("the split page shows the split", slide)
        assertEquals("the slide's gap is the real gap (real ${"%.3f".format(realShare)})", realShare, slide!!, 0.01f)
    }

    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun odinAsItReallyIs() = check()
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun unscaledScreen() = check()
}
