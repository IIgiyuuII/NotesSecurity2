package edu.androidsec.notes.data

import edu.androidsec.notes.data.db.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryTest {

    @Test
    fun plainText_isUnchanged() {
        assertEquals("abc def", SearchQuery.escape("abc def"))
    }

    @Test
    fun percent_isEscaped() {
        assertEquals("""100\%""", SearchQuery.escape("100%"))
    }

    @Test
    fun underscore_isEscaped() {
        assertEquals("""a\_b""", SearchQuery.escape("a_b"))
    }

    @Test
    fun backslash_isEscaped() {
        assertEquals("""a\\b""", SearchQuery.escape("""a\b"""))
    }

    @Test
    fun backslashBeforePercent_bothEscapedOnce() {
        // \% -> \\ и \%, то есть три обратных слэша и процент: повторного экранирования нет.
        assertEquals("""\\\%""", SearchQuery.escape("""\%"""))
    }

    @Test
    fun likePattern_wrapsInPercents() {
        assertEquals("%abc%", SearchQuery.likePattern("abc"))
        assertEquals("""%50\%%""", SearchQuery.likePattern("50%"))
        assertEquals("%%", SearchQuery.likePattern(""))
    }

    @Test
    fun likePattern_cutsOverlongQuery() {
        val pattern = SearchQuery.likePattern("a".repeat(500))
        assertEquals("%" + "a".repeat(SearchQuery.MAX_LENGTH) + "%", pattern)
    }
}
