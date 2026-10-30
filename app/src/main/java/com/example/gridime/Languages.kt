package com.example.gridime

import java.util.Locale

/**
 * Typing languages. Each one is just its three letter rows, written the way that
 * language's phone keyboards lay them out, plus a few small details.
 *
 * Languages that use the plain English letters (Italian, Dutch, Polish, Czech…)
 * don't need their own entry: they type on English and reach accents with RT + Y.
 *
 * [rows]       the three letter rows, left to right
 * [classicRow2] the middle row for the Classic layout, if it should differ
 * [locale]     used for capitals (Turkish i → İ, ı → I)
 * [lettersKey] the label on the key that brings the letters back from symbols
 * [currency]   the extra money sign on the symbols page
 * [accentFirst] accented letters this language uses most, offered first by RT + Y
 */
enum class Language(
    val label: String,
    val rows: List<String>,
    val classicRow2: String? = null,
    val locale: Locale = Locale.ROOT,
    val lettersKey: String = "ABC",
    val currency: String = "€",
    val accentFirst: String = ""
) {
    ENGLISH("English", listOf("qwertyuiop", "asdfghjkl'", "zxcvbnm"),
        classicRow2 = "asdfghjkl", locale = Locale.ENGLISH, currency = "£"),
    GERMAN("Deutsch", listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnmß"),
        locale = Locale.GERMAN, accentFirst = "äöü"),
    FRENCH("Français", listOf("azertyuiop", "qsdfghjklm", "wxcvbn'"),
        locale = Locale.FRENCH, accentFirst = "éèêëàâçùûôîï"),
    SPANISH("Español", listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"),
        locale = Locale.forLanguageTag("es"), accentFirst = "áéíóúü"),
    PORTUGUESE("Português", listOf("qwertyuiop", "asdfghjklç", "zxcvbnm"),
        locale = Locale.forLanguageTag("pt"), accentFirst = "ãáâàéêíõóôú"),
    NORWEGIAN("Norsk", listOf("qwertyuiopå", "asdfghjkløæ", "zxcvbnm"),
        locale = Locale.forLanguageTag("nb"), accentFirst = "éèêó"),
    DANISH("Dansk", listOf("qwertyuiopå", "asdfghjklæø", "zxcvbnm"),
        locale = Locale.forLanguageTag("da"), accentFirst = "é"),
    SWEDISH("Svenska · Suomi", listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm"),
        locale = Locale.forLanguageTag("sv"), accentFirst = "éšž"),
    TURKISH("Türkçe", listOf("qwertyuıopğü", "asdfghjklşi'", "zxcvbnmöç"),
        locale = Locale.forLanguageTag("tr"), currency = "₺", accentFirst = "âîû"),
    RUSSIAN("Русский", listOf("йцукенгшщзхъ", "фывапролджэё", "ячсмитьбю"),
        locale = Locale.forLanguageTag("ru"), lettersKey = "АБВ", currency = "₽", accentFirst = "ё"),
    UKRAINIAN("Українська", listOf("йцукенгшщзхї", "фівапролджєґ", "ячсмитьбю"),
        locale = Locale.forLanguageTag("uk"), lettersKey = "АБВ", currency = "₴", accentFirst = "ґ");

    /** Grid columns: the longest letter row. Every letter is exactly one column wide. */
    val columns: Int get() = rows.maxOf { it.length }

    /** Capital form of a key's letter. A few letters have no one-letter capital, so they stay as they are. */
    fun upper(text: String): String {
        if (text == "ß") return text                   // "SS" would type two letters
        val up = text.uppercase(locale)
        return if (up.length == text.length) up else text
    }
}

/**
 * RT + Y swaps the letter just typed for its accented forms, one press at a time:
 * e → é → è → ê → ë → … → back to e. This replaces the long press of a touch keyboard.
 */
object Accents {
    private val groups = listOf(
        "aàáâäãåāąæ", "cçćč", "dďđ", "eéèêëēėęě", "gğġ", "iíìîïīįı", "lłľĺ",
        "nñńň", "oóòôöõøōőœ", "rřŕ", "sśšşß", "tťţ", "uúùûüūůűų", "yýÿ", "zźżž",
        // Cyrillic
        "её", "гґ", "иіїй", "ьъ", "уў"
    )

    /**
     * The letters a Cyrillic language may offer: only its own (they are all on its keys). Russian and Ukrainian share
     * one list above, so without this Ukrainian offered ё and ъ, Russian ґ, і and ї, and both the Belarusian ў
     * (audit, 30 Oct 2026). Latin languages keep every accent, for names and borrowed words.
     */
    private fun own(language: Language): Set<Char>? {
        val letters = language.rows.joinToString("")
        return if (letters.any { it in '\u0400'..'\u04FF' }) letters.toSet() else null
    }

    /**
     * Hold A: the letter just typed first, then its accents, this language's own ones first.
     * Null when the letter has no accented forms.
     */
    fun variants(c: Char, language: Language): List<String>? {
        val upper = c.isUpperCase()
        val lower = c.lowercaseChar()
        val group = groups.firstOrNull { lower in it } ?: return null
        val base = group[0]
        val preferred = language.accentFirst.filter { it in group && it != base }
        var cycle = (base.toString() + preferred + group.drop(1).filterNot { it in preferred }).toList()
        own(language)?.let { mine -> cycle = cycle.filter { it in mine } }
        if (upper) cycle = cycle.filter { language.upper(it.toString()).length == 1 && it != 'ß' && it != 'ı' }
        val list = listOf(lower) + cycle.filter { it != lower }
        if (list.size < 2) return null
        return list.map { if (upper) language.upper(it.toString()) else it.toString() }
    }

    /** What [c] becomes on the next press, or null when it has no accented forms. */
    fun next(c: Char, language: Language): String? {
        val upper = c.isUpperCase()
        val lower = c.lowercaseChar()
        val group = groups.firstOrNull { lower in it } ?: return null
        val base = group[0]
        // The language's own accents first, in its order, then the rest
        val preferred = language.accentFirst.filter { it in group && it != base }
        val mine = own(language)
        val cycle = (base.toString() + preferred + group.drop(1).filterNot { it in preferred })
            .filter { mine == null || it in mine }
            .filter { !upper || language.upper(it.toString()).length == 1 && it != 'ß' && it != 'ı' }
        if (cycle.length < 2) return null
        val index = cycle.indexOf(lower).let { if (it < 0) 0 else it }
        val nextChar = cycle[(index + 1) % cycle.length].toString()
        return if (upper) language.upper(nextChar) else nextChar
    }
}
