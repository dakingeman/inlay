package com.example.gridime

import android.content.Intent
import android.os.Looper
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * What's new must make sense for every kind of player, as a set (Emre, 8 Nov: a TV remote's What's new was one page
 * called "And a few more"). For each device and with the secret found or not:
 *  - there is at least one page, and no title appears twice;
 *  - the first page never reads as a follow-on ("And…", "Also…", "Plus…", "More…", "One more…", "A few more…");
 *  - one page shows no "1 of 1" and no lone dot;
 *  - a remote's pages name no controller-only button or part, and use no controller-only close-up;
 *  - a controller's pages never use the remote's close-up.
 */
@RunWith(RobolectricTestRunner::class)
class WhatsNewSenseTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun check(name: String, pic: PictureChoice, style: ButtonStyle = ButtonStyle.XBOX, found: Boolean = false, sticks: Boolean = false) {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style, picture = pic,
            verticalSticks = sticks, retroUnlocked = found).save(context)
        val t = Robolectric.buildActivity(TutorialActivity::class.java,
            Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        idle(800)
        val pages = t.pageWordsCheck()
        val bad = mutableListOf<String>()
        val remote = pic == PictureChoice.REMOTE
        if (pages.isEmpty()) bad += "no pages at all"
        pages.groupBy { it.first }.filter { it.value.size > 1 }.keys.forEach { bad += "\"$it\" appears twice" }
        pages.firstOrNull()?.let { (title, _, _) ->
            if (FOLLOW_ON.containsMatchIn(title)) bad += "the first page reads as a follow-on: \"$title\"" + if (pages.size == 1) " (and it's the only page)" else ""
        }
        if (pages.size == 1) {
            if (t.pageCountShownForCheck) bad += "one page shows a page count"
            if (t.dotsShownForCheck) bad += "one page shows a dot"
        }
        pages.forEach { (title, body, scene) ->
            if (remote) {
                CONTROLLER_WORDS.find("$title $body")?.let { bad += "remote page \"$title\" names \"${it.value}\"" }
                if (scene != null && scene in CONTROLLER_SCENES) bad += "remote page \"$title\" uses the controller close-up $scene"
            } else if (scene == WhatsNewShowcase.Scene.MORE_REMOTE.name) bad += "controller page \"$title\" uses the remote's close-up"
        }
        t.finish()
        assertTrue("$name: " + bad.joinToString("; "), bad.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tvRemote() = check("tv remote", PictureChoice.REMOTE)
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tvRemoteFound() = check("tv remote, secret found", PictureChoice.REMOTE, found = true)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = check("odin", PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odinFound() = check("odin, secret found", PictureChoice.HANDHELD, found = true)
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun vertical() = check("vertical", PictureChoice.VERTICAL, ButtonStyle.SWITCH)
    @Test @Config(sdk = [34], qualifiers = "w620dp-h540dp-land-xhdpi") fun verticalSticks() = check("vertical with sticks", PictureChoice.VERTICAL, sticks = true)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun controller() = check("controller", PictureChoice.CONTROLLER, ButtonStyle.PLAYSTATION)
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun upright() = check("upright phone", PictureChoice.CONTROLLER)
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun small43() = check("small 4:3", PictureChoice.HANDHELD)

    companion object {
        private val FOLLOW_ON = Regex("^(and|also|plus|more|one more|a few more)\\b", RegexOption.IGNORE_CASE)
        /** Buttons and parts a TV remote doesn't have (OK, Back, Home and the ring are a remote's). */
        private val CONTROLLER_WORDS = Regex("\\b(LT|RT|LB|RB|L1|R1|L2|R2|L3|R3|ZL|ZR|Start|Select button|stick|sticks|D-pad|face buttons?|trigger|bumper)\\b")
        private val CONTROLLER_SCENES = setOf("TOUR", "SPLIT", "BUTTONS", "SYMBOLS", "SECRET", "MORE")
    }
}
