package com.example.gridime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The words you type often, per language, so they come first in the suggestions and words the
 * built-in list doesn't know (game titles, names) get suggested too. Kept in the app's own
 * storage on this device only, never from password, web address, email or "no learning" boxes,
 * and cleared with "Forget my words".
 *
 * Careful about what counts, so typos, tests and one-off strings never turn up as suggestions:
 *  - A word only counts once you've moved on: the next word ends and it's still there
 *    (deleted or corrected words are never counted). The service does that check.
 *  - A word from the built-in list is only moved up, after you've used it twice.
 *  - A new word (not in the list) must turn up in three different text boxes first, so typing
 *    something over and over in one place, or once to try the keyboard, never teaches it.
 *  - Mashed keys ("asdfff", "zzz") and words with digits are never kept.
 *  - Words fade: a word used only once is dropped after two weeks, and anything unused
 *    for two months is forgotten.
 */
class UserWords(context: Context, private val today: () -> Long = { System.currentTimeMillis() / DAY_MS }) {
    private val file = File(context.applicationContext.filesDir, FILE)
    private var entries: MutableMap<String, Entry>? = null
    private var dirty = false

    private class Entry(var uses: Int, var boxes: Int, var lastBox: Long, var day: Long)

    private fun data(): MutableMap<String, Entry> = entries ?: load().also { entries = it }

    private fun load(): MutableMap<String, Entry> {
        val map = runCatching {
            val o = JSONObject(file.readText())
            o.keys().asSequence().mapNotNull { k ->
                val a = o.optJSONArray(k) ?: return@mapNotNull null
                k to Entry(a.getInt(0), a.getInt(1), a.getLong(2), a.getLong(3))
            }.toMap().toMutableMap()
        }.getOrDefault(mutableMapOf())
        // Fading: a word used just once is dropped after two weeks, anything unused after two months
        val now = today()
        val before = map.size
        map.entries.removeAll { (_, e) -> now - e.day > FORGET_DAYS || (e.boxes < NEW_WORD_BOXES && e.uses < KNOWN_USES && now - e.day > TRIAL_DAYS) }
        if (map.size != before) dirty = true
        return map
    }

    /** True for something that looks like a word worth keeping. */
    fun keepable(word: String): Boolean {
        val w = word.trim('\'', '’')
        if (w.length < MIN_LENGTH || w.length > MAX_LENGTH) return false
        if (!w.all { it.isLetter() || it == '\'' || it == '’' }) return false
        if (MASHED.containsMatchIn(w.lowercase())) return false       // "zzz", "aaaargh" style runs
        return true
    }

    /** A word you typed and kept, in text box number [box]. */
    fun learn(language: Language, word: String, box: Long) {
        if (!keepable(word)) return
        val key = "${language.name}:${word.trim('\'', '’').lowercase(language.locale)}"
        val map = data()
        val e = map.getOrPut(key) { Entry(0, 0, -1, today()) }
        e.uses++
        if (e.lastBox != box) { e.boxes++; e.lastBox = box }
        e.day = today()
        if (map.size > MAX_WORDS) map.entries.sortedBy { it.value.day * 1000 + it.value.uses }.take(map.size - MAX_WORDS).forEach { map.remove(it.key) }
        dirty = true
    }

    /**
     * Your words that continue [prefix] (lower case), most used first: list words you've used
     * twice, and new words seen in three different text boxes. [known] says whether a word is
     * in the built-in list.
     */
    fun matching(language: Language, prefix: String, known: (String) -> Boolean): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val head = "${language.name}:$prefix"
        return data().entries.asSequence()
            .filter { it.key.startsWith(head) && it.key.length > head.length }
            .filter { (k, e) -> if (known(k.substringAfter(':'))) e.uses >= KNOWN_USES else e.boxes >= NEW_WORD_BOXES }
            .sortedByDescending { it.value.uses }
            .map { it.key.substringAfter(':') }
            .toList()
    }

    /** Reads the file again (Settings may have cleared it). Call only after [save]. */
    fun reload() { entries = null; dirty = false }

    fun save() {
        if (!dirty) return
        dirty = false
        val o = JSONObject()
        data().forEach { (k, e) -> o.put(k, JSONArray(listOf(e.uses, e.boxes, e.lastBox, e.day))) }
        runCatching { file.writeText(o.toString()) }
    }

    companion object {
        private const val FILE = "my_words.json"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        const val KNOWN_USES = 2         // a list word you've used twice moves up
        const val NEW_WORD_BOXES = 3     // a new word must turn up in three different text boxes
        const val TRIAL_DAYS = 14        // ...within two weeks
        const val FORGET_DAYS = 60       // anything unused for two months is forgotten
        private const val MIN_LENGTH = 3
        private const val MAX_LENGTH = 30
        private const val MAX_WORDS = 1000
        private val MASHED = Regex("""(.)\1\1""")

        /** Clears your words; returns a way to bring them back (or null if there were none). */
        fun forgetWithUndo(context: Context): (() -> Unit)? {
            val f = File(context.applicationContext.filesDir, FILE)
            val kept = runCatching { f.readText() }.getOrNull() ?: return null
            f.delete()
            return { runCatching { f.writeText(kept) } }
        }
    }
}
