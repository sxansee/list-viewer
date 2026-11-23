package app.fileviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTest {
    private val files = listOf("a", "b", "c", "d")

    @Test
    fun aFileInTheMiddleHasBothNeighbours() {
        val n = neighbours(files, "b")!!
        assertEquals(2, n.position)
        assertEquals(4, n.total)
        assertEquals("a", n.previous)
        assertEquals("c", n.next)
    }

    @Test
    fun theFirstFileHasNoPrevious() {
        val n = neighbours(files, "a")!!
        assertEquals(1, n.position)
        assertNull(n.previous)
        assertEquals("b", n.next)
    }

    @Test
    fun theLastFileHasNoNext() {
        val n = neighbours(files, "d")!!
        assertEquals(4, n.position)
        assertEquals("c", n.previous)
        assertNull(n.next)
    }

    @Test
    fun aFileThatIsNotInTheListHasNoNeighbours() {
        assertNull(neighbours(files, "zzz"))
    }

    @Test
    fun aListOfOneOrNoneHasNothingToStepThrough() {
        assertNull(neighbours(emptyList(), "a"))
        assertNull(neighbours(listOf("a"), "a"))
    }

    @Test
    fun twoFilesStepBetweenEachOther() {
        val first = neighbours(listOf("a", "b"), "a")!!
        assertNull(first.previous)
        assertEquals("b", first.next)
        val second = neighbours(listOf("a", "b"), "b")!!
        assertEquals("a", second.previous)
        assertNull(second.next)
    }
}
