package app.fileviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortingTest {
    private fun sorted(vararg names: String): List<String> = names.sortedWith(Comparator { a, b -> naturalCompare(a, b) })

    @Test
    fun numbersAreComparedByValue() {
        assertEquals(listOf("file 2", "file 10", "file 100"), sorted("file 100", "file 2", "file 10"))
        assertEquals(listOf("a1b2", "a1b10", "a2b1"), sorted("a2b1", "a1b10", "a1b2"))
    }

    @Test
    fun caseDoesNotMatter() {
        assertEquals(listOf("apple", "Banana", "cherry"), sorted("cherry", "Banana", "apple"))
        assertEquals(0, naturalCompare("ABC", "abc"))
        assertEquals(listOf("ёж", "Яблоко"), sorted("Яблоко", "ёж"))
    }

    @Test
    fun leadingZerosAndPrefixes() {
        assertEquals(0, naturalCompare("file007", "file7"))
        assertTrue(naturalCompare("file", "file1") < 0)
        assertTrue(naturalCompare("file2", "file") > 0)
        assertTrue(naturalCompare("", "a") < 0)
        assertEquals(0, naturalCompare("", ""))
        assertTrue(naturalCompare("99999999999999999999999", "100000000000000000000000") < 0)
    }

    private class Row(val name: String, val time: Long, val size: Long, val type: Int)

    private val rows = listOf(
        Row("b.pdf", 30L, 500L, 1),
        Row("a.docx", 10L, -1L, 2),
        Row("c.pdf", 20L, 900L, 1),
        Row("d10.txt", 40L, 100L, 3),
        Row("d2.txt", 40L, 100L, 3)
    )

    private fun order(sort: Sort): List<String> =
        sortRows(rows, sort, { it.time }, { it.name }, { it.size }, { it.type }).map { it.name }

    @Test
    fun everySortOrder() {
        assertEquals(listOf("d10.txt", "d2.txt", "b.pdf", "c.pdf", "a.docx"), order(Sort.RECENT))
        assertEquals(listOf("a.docx", "b.pdf", "c.pdf", "d2.txt", "d10.txt"), order(Sort.NAME))
        assertEquals(listOf("c.pdf", "b.pdf", "d10.txt", "d2.txt", "a.docx"), order(Sort.SIZE))
        assertEquals(listOf("b.pdf", "c.pdf", "a.docx", "d10.txt", "d2.txt"), order(Sort.TYPE))
    }
}
