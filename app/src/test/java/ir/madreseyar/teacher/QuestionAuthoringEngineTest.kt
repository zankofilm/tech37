package ir.madreseyar.teacher

import org.junit.Assert.*
import org.junit.Test

class QuestionAuthoringEngineTest {
    @Test fun parsesFourChoicesAndPersianScoreAndCorrectKey() {
        val parsed = parsePastedQuestions("""سؤال: مرکز استان تهران چیست؟
الف) کرج
ب) تهران
ج) قم
د) سمنان
پاسخ: ب
بارم: ۱٫۵""").single()
        assertEquals("mcq", parsed.type)
        assertEquals(4, parsed.choices.size)
        assertEquals(1, parsed.answerIndex)
        assertEquals(1.5, parsed.score, 0.0001)
        assertTrue(parsed.issue.isEmpty())
    }

    @Test fun missingCorrectOptionNeverDefaultsToFirst() {
        val parsed = parsePastedQuestions("""سؤال: گزینه درست؟
الف) یک
ب) دو
ج) سه
د) چهار""").single()
        assertNull(parsed.answerIndex)
        assertTrue(parsed.issue.contains("کلید"))
    }

    @Test fun invalidMultipleChoiceGetsExplicitIssue() {
        val parsed = parsePastedQuestions("""سؤال: نمونه
الف) یک
ب) دو
ج) سه
پاسخ: الف""").single()
        assertTrue(parsed.issue.contains("چهار گزینه"))
    }

    @Test fun mixesDescriptiveAndTrueFalseBlocks() {
        val parsed = parsePastedQuestions("""سؤال: این گزاره نادرست است.
نوع: صحیح/غلط
پاسخ: غلط

سؤال: یک نمونه توضیح دهید.
نوع: تشریحی""")
        assertEquals(2, parsed.size)
        assertEquals("true_false", parsed[0].type)
        assertFalse(parsed[0].boolAnswer)
        assertEquals("descriptive", parsed[1].type)
        assertTrue(parsed.all { it.issue.isEmpty() })
    }

    @Test fun suggestionsBalanceChaptersAndRespectExclusions() {
        val rows = listOf(
            QuestionSuggestionMeta("q1", "ch1", "mcq", "easy"),
            QuestionSuggestionMeta("q2", "ch1", "mcq", "easy"),
            QuestionSuggestionMeta("q3", "ch2", "true_false", "hard"),
            QuestionSuggestionMeta("q4", "ch2", "short_answer", "medium"),
        )
        assertEquals(setOf("q1", "q3"), suggestApprovedQuestionIds(rows, 2).toSet())
        val selection = suggestApprovedQuestionIds(rows, 10, setOf("q1"))
        assertEquals(3, selection.size)
        assertFalse(selection.contains("q1"))
        assertEquals(selection.size, selection.distinct().size)
    }
}
