package com.example.gridime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Holding a letter in Russian or Ukrainian offers only that language's own letters (both shared one list, so
 * Ukrainian offered ё and ъ, Russian ґ, і and ї, and both the Belarusian ў; audit, 30 Oct 2026). Same for RT + Y.
 */
class CyrillicAccentsTest {
    private val ru = Language.RUSSIAN
    private val uk = Language.UKRAINIAN

    @Test fun russian() {
        assertEquals(listOf("е", "ё"), Accents.variants('е', ru))
        assertEquals(listOf("и", "й"), Accents.variants('и', ru))
        assertEquals(listOf("ь", "ъ"), Accents.variants('ь', ru))
        assertNull(Accents.variants('г', ru))
        assertNull(Accents.variants('у', ru))
        assertEquals(listOf("Е", "Ё"), Accents.variants('Е', ru))
    }

    @Test fun ukrainian() {
        assertNull(Accents.variants('е', uk))
        assertNull(Accents.variants('ь', uk))
        assertNull(Accents.variants('у', uk))
        assertEquals(listOf("г", "ґ"), Accents.variants('г', uk))
        assertEquals(setOf("и", "і", "ї", "й"), Accents.variants('и', uk)!!.toSet())
        assertEquals("ґ", Accents.next('г', uk))
        assertNull(Accents.next('е', uk))
    }

    @Test fun latinLanguagesKeepEveryAccent() {
        // names and borrowed words: an English keyboard still offers ř and ę
        assertEquals(true, Accents.variants('r', Language.ENGLISH)!!.contains("ř"))
        assertEquals(true, Accents.variants('e', Language.ENGLISH)!!.contains("ę"))
    }
}
