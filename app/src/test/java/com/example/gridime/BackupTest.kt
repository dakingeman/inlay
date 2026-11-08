package com.example.gridime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Back up and restore (1.3): settings and learnt words go into one file and come back; other files are refused. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun aRoundTripBringsEverythingBack() {
        KeyboardSettings(tutorialDone = true, toneIndex = 3, sizeLevel = 2, split = true, language2 = Language.GERMAN).save(context)
        SmallFile.write(context, "my_words.json", JSONObject().put("words", JSONObject().put("inlay", 5)).toString())
        val file = Backup.make(context)
        assertTrue("no clipboard in the backup", "clip" !in JSONObject(file).keys().asSequence().joinToString())
        KeyboardSettings().save(context)                                   // everything changed since
        SmallFile.write(context, "my_words.json", "{}")
        assertEquals(Backup.Result.RESTORED, Backup.restore(context, file))
        val s = KeyboardSettings.load(context)
        assertEquals("settings back", listOf(3, 2), listOf(s.toneIndex, s.sizeLevel))
        assertEquals(true, s.split); assertEquals(Language.GERMAN, s.language2)
        assertTrue("words back", "inlay" in (SmallFile.read(context, "my_words.json") ?: ""))
    }

    @Test fun otherFilesAreRefused() {
        assertEquals(Backup.Result.UNREADABLE, Backup.restore(context, "not json"))
        assertEquals(Backup.Result.NOT_A_BACKUP, Backup.restore(context, "{\"hello\": 1}"))
    }

    /** Opening the file picker mustn't count as leaving Settings, or Settings closes and takes the picker with it (7 Nov). */
    @Test fun thePickerKeepsSettingsOpen() {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        a.startActivityForResult(android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT), 41)
        val sent = shadowOf(a).nextStartedActivity
        assertTrue("marked as not the user leaving", sent.flags and android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION != 0)
        assertTrue("Settings still open", !a.isFinishing)
    }
}
