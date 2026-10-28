package com.example.gridime

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Sounds play in silent mode by default on handhelds and TVs, not on phones; a choice someone made is kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SilentModeDefaultTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun fresh(phone: Boolean): KeyboardSettings {
        context.filesDir.listFiles()?.forEach { it.delete() }
        context.getSharedPreferences(context.packageName + "_preferences", 0).edit().clear().commit()
        shadowOf(context.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY, phone)
        return KeyboardSettings.load(context)
    }

    @Test fun aHandheldPlaysInSilentMode() = assertEquals(true, fresh(phone = false).soundInSilent)

    @Test fun aPhoneRespectsSilentMode() = assertEquals(false, fresh(phone = true).soundInSilent)

    @Test fun aChoiceMadeIn12IsKept() {
        fresh(phone = false).copy(soundInSilent = false).save(context)
        assertEquals(false, KeyboardSettings.load(context).soundInSilent)
    }

    @Test fun anOldOnIsKeptOnAPhone() {
        fresh(phone = true)
        // a 1.1 settings file with "play in silent mode" turned on, and no 1.2 key yet
        val f = context.filesDir.walk().first { it.isFile && it.readText().contains("sound_in_silent") }
        val json = org.json.JSONObject(f.readText()); json.remove("sound_in_silent_2"); json.put("sound_in_silent", true)
        f.writeText(json.toString())
        assertEquals(true, KeyboardSettings.load(context).soundInSilent)
    }
}
