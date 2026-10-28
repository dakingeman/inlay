package com.example.gridime

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Finds the most common words that continue the word being typed.
 *
 * Each language has a plain word list in res/raw, most common first (see licenses/WORD_LISTS.md),
 * so the first matches are the best ones. A list loads in the background the first time its
 * language is used; until then there are simply no suggestions. [onReady] runs on the main
 * thread once it's in.
 */
class WordSuggester(context: Context, val language: Language, onReady: () -> Unit) {

    @Volatile
    private var words: Array<String> = emptyArray()

    init {
        val resources = context.applicationContext.resources
        Thread {
            words = runCatching {
                resources.openRawResource(rawFor(language)).bufferedReader().useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toList()
                        .toTypedArray()
                }
            }.getOrDefault(emptyArray())
            Handler(Looper.getMainLooper()).post(onReady)
        }.apply {
            isDaemon = true
            start()
        }
    }

    @Volatile private var known: HashSet<String>? = null

    /** Is this (lower-case) word in the built-in list? False until the list has loaded. */
    fun knows(word: String): Boolean {
        val list = words
        if (list.isEmpty()) return false
        val set = known ?: HashSet<String>(list.size * 2).apply { list.forEach { add(it) } }.also { known = it }
        return word in set
    }

    /** Up to [limit] words that continue [typed], most common first, in the same style (Capital, CAPS or lower case). */
    fun suggest(typed: String, limit: Int, mine: List<String> = emptyList()): List<String> {
        val list = words
        if (typed.isEmpty()) return emptyList()
        val prefix = typed.replace('’', '\'').lowercase(language.locale)
        val found = ArrayList<String>(limit)
        // Your own words first (most used first), then the common ones
        for (word in mine) {
            if (found.size == limit) return found
            found += matchCase(word, typed)
        }
        for (word in list) {
            if (found.any { it.equals(word, ignoreCase = true) }) continue
            if (word.length > prefix.length && word.startsWith(prefix)) {
                found += matchCase(word, typed)
                if (found.size == limit) break
            }
        }
        return found
    }

    private fun matchCase(word: String, typed: String): String = when {
        // Two or more capitals and no small letters: someone typing in capitals
        typed.count { it.isLetter() } > 1 && typed.none { it.isLowerCase() } -> word.uppercase(language.locale)
        typed.first().isUpperCase() -> language.upper(word.take(1)) + word.drop(1)
        // English "I" is always a capital, also in I'm, I'll ...
        language == Language.ENGLISH && (word == "i" || word.startsWith("i'")) -> "I" + word.drop(1)
        else -> word
    }

    companion object {
        fun rawFor(language: Language): Int = when (language) {
            Language.ENGLISH -> R.raw.words_en
            Language.GERMAN -> R.raw.words_de
            Language.FRENCH -> R.raw.words_fr
            Language.SPANISH -> R.raw.words_es
            Language.PORTUGUESE -> R.raw.words_pt
            Language.NORWEGIAN -> R.raw.words_nb
            Language.DANISH -> R.raw.words_da
            Language.SWEDISH -> R.raw.words_sv
            Language.TURKISH -> R.raw.words_tr
            Language.RUSSIAN -> R.raw.words_ru
            Language.UKRAINIAN -> R.raw.words_uk
        }
    }
}
