package com.example.gridime

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Remember each app's language (1.3, off by default): saved on a switch, given back when the app's box opens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguagePerAppTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val two = KeyboardSettings(language1 = Language.ENGLISH, language2 = Language.GERMAN, languagePerApp = true)

    @Test fun offByDefaultAndNothingRemembered() {
        assertEquals(false, KeyboardSettings().languagePerApp)
        assertEquals(null, two.copy(languagePerApp = false).rememberLanguage("a.b").languageFor("a.b"))
    }

    @Test fun eachAppGetsItsOwnBack() {
        var s = two.nextLanguage().rememberLanguage("chat.app")            // German in the chat app
        s = s.nextLanguage().rememberLanguage("notes.app")                 // English in the notes app
        assertEquals("chat: German", 1, s.languageFor("chat.app"))
        assertEquals("notes: English", 0, s.languageFor("notes.app"))
        assertEquals("an app never seen: no change", null, s.languageFor("other.app"))
        s.save(context)
        assertEquals("kept across a restart", 1, KeyboardSettings.load(context).languageFor("chat.app"))
    }

    @Test fun aLanguageTurnedOffIsIgnored() {
        val s = two.nextLanguage().rememberLanguage("chat.app").copy(language2 = null)
        assertEquals(null, s.languageFor("chat.app"))
    }

    @Test fun theListStaysSmall() {
        var s = two
        repeat(KeyboardSettings.APP_LANGUAGES_MAX + 10) { s = s.rememberLanguage("app.$it") }
        assertEquals(KeyboardSettings.APP_LANGUAGES_MAX, s.appLanguages.split("|").size)
    }
}
