package echo.player

import echo.player.domain.AlphabetIndex
import echo.player.domain.IndexRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphabetIndexTests {
    private fun entries(sections: echo.player.domain.SectionedItems<String>) =
        sections.rows.filterIsInstance<IndexRow.Entry<String>>().map { it.item }

    @Test
    fun lettersMapToTheirUppercaseLetter() {
        assertEquals('A', AlphabetIndex.letterFor("Adele"))
        assertEquals('A', AlphabetIndex.letterFor("a-ha"))
        assertEquals('Z', AlphabetIndex.letterFor("zayn"))
        assertEquals('M', AlphabetIndex.letterFor("  Madonna"))
    }

    @Test
    fun accentedLettersFileUnderTheirBaseLetter() {
        assertEquals('E', AlphabetIndex.letterFor("Élan"))
        assertEquals('O', AlphabetIndex.letterFor("Ólafur Arnalds"))
        assertEquals('N', AlphabetIndex.letterFor("Ñandú"))
    }

    @Test
    fun digitsSymbolsBlanksAndOtherScriptsGoToHash() {
        assertEquals('#', AlphabetIndex.letterFor("01 - Intro"))
        assertEquals('#', AlphabetIndex.letterFor("(Untitled)"))
        assertEquals('#', AlphabetIndex.letterFor("[www.example.com]"))
        assertEquals('#', AlphabetIndex.letterFor(""))
        assertEquals('#', AlphabetIndex.letterFor("   "))
        assertEquals('#', AlphabetIndex.letterFor("తెలుగు"))
        assertEquals('#', AlphabetIndex.letterFor("🎵 Party"))
    }

    @Test
    fun indexCoversHashPlusAToZ() {
        assertEquals(27, AlphabetIndex.LETTERS.size)
        assertEquals('#', AlphabetIndex.LETTERS.first())
        assertEquals('Z', AlphabetIndex.LETTERS.last())
    }

    @Test
    fun sectionsAreSortedWithHashFirstAndHeadersBeforeEachGroup() {
        val sections = AlphabetIndex.buildSections(listOf("banana", "Apple", "01 Song", "avocado", "Cherry", "_x")) { it }

        assertEquals(listOf("01 Song", "_x", "Apple", "avocado", "banana", "Cherry"), entries(sections))
        val headers = sections.rows.filterIsInstance<IndexRow.Header>().map { it.letter }
        assertEquals(listOf('#', 'A', 'B', 'C'), headers)
        // Header, 2 entries, header, 2 entries, header, entry, header, entry
        assertTrue(sections.rows[0] is IndexRow.Header)
        assertTrue(sections.rows[1] is IndexRow.Entry<*>)
        assertEquals(3, sections.positionFor('A'))
    }

    @Test
    fun jumpPositionPointsAtTheHeaderOfThatLetterInTheRealRowList() {
        val names = listOf("Alpha", "Beta", "Bravo", "Mango", "Mike", "Zulu")
        val sections = AlphabetIndex.buildSections(names) { it }

        for (letter in listOf('A', 'B', 'M', 'Z')) {
            val position = sections.positionFor(letter)!!
            val row = sections.rows[position]
            assertEquals(IndexRow.Header(letter), row)
            // The row right after the header is the first item of that letter.
            val first = (sections.rows[position + 1] as IndexRow.Entry<String>).item
            assertEquals(letter, AlphabetIndex.letterFor(first))
        }
    }

    @Test
    fun missingLetterJumpsToTheNextPopulatedLetter() {
        val sections = AlphabetIndex.buildSections(listOf("Alpha", "Mango", "Zulu")) { it }

        assertEquals(sections.positionFor('M'), sections.positionFor('F'))
        assertEquals(sections.positionFor('Z'), sections.positionFor('X'))
        assertEquals(sections.positionFor('A'), sections.positionFor('#'))
    }

    @Test
    fun missingLetterAfterTheLastPopulatedOneLandsOnTheLastSection() {
        val sections = AlphabetIndex.buildSections(listOf("Alpha", "Mango")) { it }
        assertEquals(sections.positionFor('M'), sections.positionFor('Z'))
    }

    @Test
    fun presentLettersListsOnlyLettersWithItems() {
        val sections = AlphabetIndex.buildSections(listOf("Alpha", "Mango", "9 lives")) { it }
        assertEquals(setOf('#', 'A', 'M'), sections.presentLetters)
    }

    @Test
    fun emptyListHasNoSectionsAndNoJumpTarget() {
        val sections = AlphabetIndex.buildSections(emptyList<String>()) { it }
        assertTrue(sections.rows.isEmpty())
        assertTrue(sections.presentLetters.isEmpty())
        assertNull(sections.positionFor('A'))
    }

    @Test
    fun sortingIsCaseAndAccentInsensitive() {
        val sections = AlphabetIndex.buildSections(listOf("zebra", "Éclair", "apple", "Eagle", "Banana")) { it }
        assertEquals(listOf("apple", "Banana", "Eagle", "Éclair", "zebra"), entries(sections))
    }

    @Test
    fun largeLibraryIsSectionedCorrectlyAndQuickly() {
        val random = java.util.Random(42)
        val letters = ('A'..'Z').toList()
        val names = List(60_000) { i ->
            val prefix = if (i % 50 == 0) random.nextInt(10).toString() else letters[random.nextInt(26)].toString()
            "$prefix${Integer.toString(random.nextInt(1_000_000), 36)}"
        }

        val started = System.nanoTime()
        val sections = AlphabetIndex.buildSections(names) { it }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(names.size, entries(sections).size)
        // Every header position really holds that header, and headers appear in index order.
        var lastPosition = -1
        for (letter in AlphabetIndex.LETTERS) {
            val position = sections.positionFor(letter) ?: continue
            assertTrue(position >= lastPosition)
            lastPosition = position
        }
        for (letter in sections.presentLetters) {
            assertEquals(IndexRow.Header(letter), sections.rows[sections.positionFor(letter)!!])
        }
        // Items are in non-decreasing sort order.
        val sorted = entries(sections)
        val buckets = sorted.map { AlphabetIndex.LETTERS.indexOf(AlphabetIndex.letterFor(it)) }
        assertEquals(buckets.sorted(), buckets)
        assertTrue("sectioning 60k items took ${elapsedMs}ms", elapsedMs < 5_000)
    }
}
