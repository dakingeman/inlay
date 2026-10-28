package com.example.gridime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Keeps the sound packs, the files and the code in step. Fails with a plain sentence when
 * a pack is missing a sound, a file belongs to no pack, or the code plays the wrong pack's file.
 * The sounds themselves are measured by tools/soundcheck.py (loudness, speakers, sharpness).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoundPacksTest {

    private val cues = listOf("move", "type", "space", "delete", "on", "off", "lock", "select_all", "confirm", "back")
    private val takes = listOf("type1", "type2", "type3")
    private val raw: Map<String, Int> = R.raw::class.java.fields.associate { it.name to it.getInt(null) }

    @Test fun everyPackHasEverySound() {
        val missing = SoundPack.values().flatMap { p -> (cues + takes).map { "${p.file}_$it" } }.filter { it !in raw }
        if (missing.isNotEmpty()) fail("These sounds are missing from res/raw: ${missing.joinToString()}")
    }

    @Test fun everySoundFileBelongsToAPack() {
        val prefixes = SoundPack.values().map { it.file + "_" }
        val strays = raw.keys.filter { !it.startsWith("words_") && prefixes.none(it::startsWith) }
        if (strays.isNotEmpty()) fail("These files in res/raw belong to no sound pack: ${strays.joinToString()}")
    }

    @Test fun codePlaysEachPacksOwnFiles() {
        for (p in SoundPack.values()) {
            val expected = cues.map { raw.getValue("${p.file}_$it") }
            assertEquals("${p.label}: the ten cues point at the wrong files", expected, Feedback.resourcesFor(p).toList())
            assertEquals("${p.label}: the letter takes point at the wrong files",
                takes.map { raw.getValue("${p.file}_$it") }, Feedback.takesFor(p).toList())
        }
    }

    @Test fun oldSavedChoicesStillWork() {
        assertEquals(SoundPack.THOCK, SoundPack.fromSaved("SOFT_DEEP"))
        SoundPack.values().forEach { assertEquals(it, SoundPack.fromSaved(it.name)) }
        assertEquals(null, SoundPack.fromSaved("NO_SUCH_PACK"))
    }

    @Test fun volumeStepsAreEven() {
        assertEquals(0f, Feedback.volumeGain(0))
        assertEquals(1f, Feedback.volumeGain(10), 0.0001f)
        for (step in 1..9) {
            val db = 20 * Math.log10(Feedback.volumeGain(step + 1).toDouble() / Feedback.volumeGain(step))
            assertEquals("step $step to ${step + 1}", Design.SOUND_STEP_DB, db, 0.01)
        }
        assertTrue("the default leaves room to go louder", Feedback.volumeGain(KeyboardSettings().soundVolume) < 0.6f)
    }
}
