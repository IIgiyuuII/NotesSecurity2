package edu.androidsec.notes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteValidatorTest {

    // ---------- допустимые значения ----------

    @Test
    fun validNote_hasNoErrors() {
        val errors = NoteValidator.validate(
            title = "Список покупок",
            body = "Молоко,\nхлеб,\tкофе.",
            tags = listOf("shopping", "room-2", "a", "-", "123"),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun emptyNote_isValid() {
        assertTrue(NoteValidator.validate("", "", emptyList()).isEmpty())
    }

    // ---------- заголовок ----------

    @Test
    fun title_of100Chars_isValid() {
        assertNull(NoteValidator.checkTitle("a".repeat(100)))
    }

    @Test
    fun title_of101Chars_isTooLong() {
        assertEquals(NoteError.TITLE_TOO_LONG, NoteValidator.checkTitle("a".repeat(101)))
    }

    @Test
    fun title_lengthIsCountedInCodePoints() {
        // 100 эмодзи = 200 char в UTF-16, но 100 символов.
        assertNull(NoteValidator.checkTitle("😀".repeat(100)))
        assertEquals(NoteError.TITLE_TOO_LONG, NoteValidator.checkTitle("😀".repeat(101)))
    }

    @Test
    fun title_withLineBreak_isRejected() {
        assertEquals(NoteError.TITLE_MULTILINE, NoteValidator.checkTitle("a\nb"))
        assertEquals(NoteError.TITLE_MULTILINE, NoteValidator.checkTitle("a\rb"))
        assertEquals(NoteError.TITLE_MULTILINE, NoteValidator.checkTitle("a\u2028b"))
    }

    @Test
    fun title_withControlChars_isRejected() {
        assertEquals(NoteError.TITLE_BAD_CHARS, NoteValidator.checkTitle("a\tb"))
        assertEquals(NoteError.TITLE_BAD_CHARS, NoteValidator.checkTitle("a\u0000b"))
    }

    // ---------- текст ----------

    @Test
    fun body_of5000Chars_isValid() {
        assertNull(NoteValidator.checkBody("a".repeat(5000)))
    }

    @Test
    fun body_of5001Chars_isTooLong() {
        assertEquals(NoteError.BODY_TOO_LONG, NoteValidator.checkBody("a".repeat(5001)))
    }

    @Test
    fun body_allowsNewlinesAndTabs() {
        assertNull(NoteValidator.checkBody("строка 1\r\nстрока 2\n\tс отступом"))
    }

    @Test
    fun body_withControlChars_isRejected() {
        assertEquals(NoteError.BODY_BAD_CHARS, NoteValidator.checkBody("a\u0000b"))
        assertEquals(NoteError.BODY_BAD_CHARS, NoteValidator.checkBody("a\u0007b"))
    }

    // ---------- теги при сохранении ----------

    @Test
    fun tags_fiveTagsAreValid() {
        assertNull(NoteValidator.checkTags(listOf("a", "b", "c", "d", "e")))
    }

    @Test
    fun tags_sixTagsAreTooMany() {
        assertEquals(
            NoteError.TAGS_TOO_MANY,
            NoteValidator.checkTags(listOf("a", "b", "c", "d", "e", "f")),
        )
    }

    @Test
    fun tag_of20CharsIsValid_21IsTooLong() {
        assertNull(NoteValidator.checkTags(listOf("a".repeat(20))))
        assertEquals(NoteError.TAG_TOO_LONG, NoteValidator.checkTags(listOf("a".repeat(21))))
    }

    @Test
    fun tag_empty_isRejected() {
        assertEquals(NoteError.TAG_EMPTY, NoteValidator.checkTags(listOf("")))
        assertEquals(NoteError.TAG_EMPTY, NoteValidator.checkTags(listOf("a", "")))
    }

    @Test
    fun tag_withForbiddenChars_isRejected() {
        for (bad in listOf("Kotlin", "a_b", "тег", "a b", "a#", "a.b", "a\nb", "😀")) {
            assertEquals("tag «$bad»", NoteError.TAG_BAD_CHARS, NoteValidator.checkTags(listOf(bad)))
        }
    }

    @Test
    fun tag_dashAndDigitsAreValid() {
        assertNull(NoteValidator.checkTags(listOf("-", "--", "0", "a-1-b")))
    }

    // ---------- теги во время ввода ----------

    @Test
    fun tagsInput_validStrings() {
        for (ok in listOf("", "a", "a ", "a b", "kotlin room-2 x1", "a b c d e")) {
            assertNull("input «$ok»", NoteValidator.checkTagsInput(ok))
        }
    }

    @Test
    fun tagsInput_forbiddenChars() {
        for (bad in listOf("A", "a_b", "тег", "a,b", "a\nb", "a\tb")) {
            assertEquals("input «$bad»", NoteError.TAG_BAD_CHARS, NoteValidator.checkTagsInput(bad))
        }
    }

    @Test
    fun tagsInput_leadingOrDoubleSpace_isRejected() {
        for (bad in listOf(" ", " a", "a  b", "a  ")) {
            assertEquals("input «$bad»", NoteError.TAG_EMPTY, NoteValidator.checkTagsInput(bad))
        }
    }

    @Test
    fun tagsInput_tooLongTag() {
        assertEquals(NoteError.TAG_TOO_LONG, NoteValidator.checkTagsInput("a".repeat(21)))
        assertEquals(NoteError.TAG_TOO_LONG, NoteValidator.checkTagsInput("a " + "b".repeat(21)))
    }

    @Test
    fun tagsInput_tooManyTags() {
        assertEquals(NoteError.TAGS_TOO_MANY, NoteValidator.checkTagsInput("a b c d e f"))
        // После пятого тега пробел уже не нужен: шестого не будет.
        assertEquals(NoteError.TAGS_TOO_MANY, NoteValidator.checkTagsInput("a b c d e "))
    }

    // ---------- разбор и сборка ----------

    @Test
    fun parseAndFormatTags() {
        assertEquals(listOf("a", "b"), NoteValidator.parseTags("a b"))
        assertEquals(listOf("a"), NoteValidator.parseTags("a "))
        assertEquals(emptyList<String>(), NoteValidator.parseTags(""))
        assertEquals("a b", NoteValidator.formatTags(listOf("a", "b")))
        assertEquals("", NoteValidator.formatTags(emptyList()))
    }

    // ---------- вся заметка ----------

    @Test
    fun validate_collectsErrorsOfAllFields() {
        val errors = NoteValidator.validate(
            title = "a".repeat(101),
            body = "b".repeat(5001),
            tags = listOf("BAD"),
        )
        assertEquals(
            listOf(NoteError.TITLE_TOO_LONG, NoteError.BODY_TOO_LONG, NoteError.TAG_BAD_CHARS),
            errors,
        )
    }
}
