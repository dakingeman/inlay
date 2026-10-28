package com.example.gridime

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Learn my words, Forget my words, and the lone "i" rule. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartTypingTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun onlyRealWordsAreLearned() {
        var day = 20000L
        val words = UserWords(context) { day }
        val known = { w: String -> w == "zebra" }
        // A list word: used twice anywhere, and it moves up
        words.learn(Language.ENGLISH, "zebra", 1); assertTrue(words.matching(Language.ENGLISH, "ze", known).isEmpty())
        words.learn(Language.ENGLISH, "zebra", 1); assertEquals(listOf("zebra"), words.matching(Language.ENGLISH, "ze", known))
        // A new word typed over and over in one box: not yet
        repeat(5) { words.learn(Language.ENGLISH, "Zelda", 1) }
        assertEquals(listOf("zebra"), words.matching(Language.ENGLISH, "ze", known))
        words.learn(Language.ENGLISH, "zelda", 2)
        assertEquals(listOf("zebra"), words.matching(Language.ENGLISH, "ze", known))
        words.learn(Language.ENGLISH, "zelda", 3)                // the third different box
        assertEquals(listOf("zelda", "zebra"), words.matching(Language.ENGLISH, "ze", known))
        // Mashed keys and digits never count
        listOf("zzzz", "zeeeee", "ze1da").forEach { w -> repeat(3) { b -> words.learn(Language.ENGLISH, w, 10L + b) } }
        assertEquals(listOf("zelda", "zebra"), words.matching(Language.ENGLISH, "ze", known))
        // Another language keeps its own words
        repeat(3) { b -> words.learn(Language.GERMAN, "zeitung", 20L + b) }
        assertEquals(listOf("zeitung"), words.matching(Language.GERMAN, "ze") { false })
        words.save()

        // A new word tried once, then left for over two weeks: dropped. Unused for two months: all forgotten
        words.learn(Language.ENGLISH, "zorp", 30); words.save()
        day += 15
        assertTrue(UserWords(context) { day }.matching(Language.ENGLISH, "zo") { false }.isEmpty())
        assertEquals(listOf("zelda", "zebra"), UserWords(context) { day }.matching(Language.ENGLISH, "ze", known))
        day += 61
        assertTrue(UserWords(context) { day }.matching(Language.ENGLISH, "ze", known).isEmpty())
    }

    @Test
    fun suggestionsPutYourWordsFirstAndForgetCanBeUndone() {
        val words = UserWords(context)
        repeat(3) { b -> words.learn(Language.ENGLISH, "zelda", b.toLong()) }
        words.save()
        val list = WordSuggester(context, Language.ENGLISH) {}
        Thread.sleep(1500); shadowOf(Looper.getMainLooper()).idle()
        val mine = words.matching(Language.ENGLISH, "ze") { list.knows(it) }
        val got = list.suggest("Ze", 3, mine)
        assertEquals("Zelda", got[0]); assertEquals(3, got.size)
        assertEquals("no repeats", got.size, got.map { it.lowercase() }.toSet().size)
        assertTrue(list.knows("the"))

        val undo = UserWords.forgetWithUndo(context)
        assertTrue(UserWords(context).matching(Language.ENGLISH, "ze") { false }.isEmpty())
        undo!!.invoke()
        assertEquals(listOf("zelda"), UserWords(context).matching(Language.ENGLISH, "ze") { false })
    }

    @Test
    fun loneI() {
        val r = Regex("""i(['’](m|ll|d|ve))?""")
        listOf("i", "i'm", "i’ll", "i'd", "i've").forEach { assertTrue(it, r.matches(it)) }
        listOf("in", "it", "iv", "ice", "im").forEach { assertTrue(it, !r.matches(it)) }
    }
}
