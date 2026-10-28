package com.example.gridime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks for the preview features: hold-A accents, word slots, number row, number pad. */
class PreviewFeaturesTest {

    @Test
    fun accentsStartWithTheTypedLetterThenTheLanguagesOwn() {
        val fr = Accents.variants('e', Language.FRENCH)!!
        assertEquals("e", fr[0])
        assertEquals("é", fr[1])                          // French: é first
        val de = Accents.variants('a', Language.GERMAN)!!
        assertEquals("ä", de[1])                          // German: ä first
    }

    @Test
    fun capitalsGiveCapitalAccents() {
        val list = Accents.variants('E', Language.ENGLISH)!!
        assertEquals("E", list[0])
        assertTrue(list.drop(1).all { it == it.uppercase() })
    }

    @Test
    fun lettersWithoutAccentsOpenNothing() {
        assertNull(Accents.variants('q', Language.ENGLISH))
        assertNull(Accents.variants('5', Language.ENGLISH))
    }

    @Test
    fun accentListsFitInTheStrip() {
        // The longest group must never be wider than the keyboard (10 columns at least)
        for (lang in Language.values()) for (c in "abcdefghijklmnopqrstuvwxyzабвгдеиуь") {
            Accents.variants(c, lang)?.let { assertTrue("$c in $lang", it.size <= lang.columns) }
        }
    }

    @Test
    fun wordSlotsKeepTheStripTheSameWidth() {
        for (n in 1..3) {
            val words = listOf("hello", "help", "held").take(n)
            for (selectAll in listOf(false, true)) {
                val row = Layouts.toolRow(selectAll, words)
                assertEquals(10f, row.sumOf { it.weight.toDouble() }.toFloat(), 0.001f)
                assertEquals("hello", row.first { it.best }.label)
                assertEquals(ControllerButton.R3, row.first { it.best }.hint)
                assertEquals(3 - n, row.count { it.type == KeyType.GAP })
            }
        }
    }

    @Test
    fun numberRowMovesTheSymbolsNumbersAway() {
        val with = Layouts.symbolRows(KeyLayout.GRID, Language.ENGLISH, numberRow = true)
        val without = Layouts.symbolRows(KeyLayout.GRID, Language.ENGLISH, numberRow = false)
        assertEquals("1", without[0][0].label)
        assertTrue(with[0].none { it.label.first().isDigit() })   // no digits twice
        assertEquals(10, Layouts.numberRow().size)
    }

    @Test
    fun numberPadIsACompactBlock() {
        Layouts.numpadRows(FieldKind.NUMBER).forEach { row ->
            assertEquals(6.4f, row.sumOf { it.weight.toDouble() }.toFloat(), 0.001f)
        }
    }
}
