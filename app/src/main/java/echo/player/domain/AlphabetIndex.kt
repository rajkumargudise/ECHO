package echo.player.domain

import java.text.Normalizer

/** One row of an alphabetically sectioned list. */
sealed interface IndexRow<out T> {
    data class Header(val letter: Char) : IndexRow<Nothing>
    data class Entry<T>(val item: T) : IndexRow<T>
}

/**
 * A sorted list grouped under letter headers, plus where each header sits in the flat row list.
 * The positions are what the alphabet index jumps to, so they always match what is on screen.
 */
class SectionedItems<T>(
    val rows: List<IndexRow<T>>,
    private val headerPositions: Map<Char, Int>,
) {
    val presentLetters: Set<Char> get() = headerPositions.keys

    /** The items in displayed order, without headers (what a "play from here" queue should contain). */
    @Suppress("UNCHECKED_CAST")
    val items: List<T> by lazy { rows.mapNotNull { (it as? IndexRow.Entry<T>)?.item } }

    /**
     * Row position to scroll to for [letter]. A letter with no items jumps to the next letter that has
     * some (like phone contact lists); past the last populated letter it lands on the last section.
     * Null only when the list is empty.
     */
    fun positionFor(letter: Char): Int? {
        if (headerPositions.isEmpty()) return null
        val target = AlphabetIndex.normalizeLetter(letter)
        headerPositions[target]?.let { return it }
        val start = AlphabetIndex.LETTERS.indexOf(target)
        for (i in start + 1 until AlphabetIndex.LETTERS.size) {
            headerPositions[AlphabetIndex.LETTERS[i]]?.let { return it }
        }
        return AlphabetIndex.LETTERS.lastOrNull { it in headerPositions }?.let { headerPositions[it] }
    }
}

object AlphabetIndex {
    /** '#' collects digits, symbols and anything that does not start with a Latin letter. */
    const val OTHER = '#'

    val LETTERS: List<Char> = listOf(OTHER) + ('A'..'Z').toList()

    fun normalizeLetter(letter: Char): Char {
        val upper = letter.uppercaseChar()
        return if (upper in 'A'..'Z') upper else OTHER
    }

    /** Accents are ignored, so "Élan" files under E. */
    fun letterFor(name: String): Char {
        val first = name.trimStart().firstOrNull() ?: return OTHER
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull() ?: return OTHER
        return normalizeLetter(base)
    }

    fun sortKey(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .lowercase()

    /** Sorts [items] by name ('#' bucket first, then A–Z) and inserts a header before each letter group. */
    fun <T> buildSections(items: List<T>, nameOf: (T) -> String): SectionedItems<T> {
        if (items.isEmpty()) return SectionedItems(emptyList(), emptyMap())

        class Keyed(val item: T, val bucket: Int, val key: String)

        val sorted = items
            .map { item ->
                val name = nameOf(item)
                Keyed(item, LETTERS.indexOf(letterFor(name)), sortKey(name))
            }
            .sortedWith(compareBy<Keyed> { it.bucket }.thenBy { it.key })

        val rows = ArrayList<IndexRow<T>>(sorted.size + LETTERS.size)
        val positions = LinkedHashMap<Char, Int>()
        var currentBucket = -1
        for (entry in sorted) {
            if (entry.bucket != currentBucket) {
                currentBucket = entry.bucket
                positions[LETTERS[currentBucket]] = rows.size
                rows.add(IndexRow.Header(LETTERS[currentBucket]))
            }
            rows.add(IndexRow.Entry(entry.item))
        }
        return SectionedItems(rows, positions)
    }
}
