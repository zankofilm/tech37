package ir.madreseyar.teacher

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TeacherUiHelpersTest {
    private val contacts = listOf(
        JSONObject().put("studentId", "s1").put("subject", "ریاضی").put("name", "شاگرد یک"),
        JSONObject().put("studentId", "s1").put("subject", "علوم").put("name", "شاگرد یک"),
        JSONObject().put("studentId", "s2").put("subject", "علوم").put("name", "شاگرد دو")
    )
    @Test fun classChatOpensOnlyMatchingStudentAndSubject() {
        val match = findContactForTarget(contacts, MessageTarget("s1", "علوم"))
        assertNotNull(match)
        assertEquals("s1", match!!.optString("studentId"))
        assertEquals("علوم", match.optString("subject"))
    }
    @Test fun cannotOpenOutOfScopeContact() {
        assertNull(findContactForTarget(contacts, MessageTarget("s9", "علوم")))
        assertNull(findContactForTarget(contacts, MessageTarget("s1", "زبان انگلیسی")))
        assertNull(findContactForTarget(contacts, MessageTarget("", "علوم")))
    }
    @Test fun bankSearchHandlesPersianVariants() {
        assertTrue(bankQuestionMatchesSearch("كتاب", listOf("کتاب علوم")))
        assertFalse(bankQuestionMatchesSearch("مطالعات", listOf("کتاب علوم")))
    }
    @Test fun bankQuestionRequiresExplicitOptionKey() {
        val invalid = QuestionReviewMeta("درست‌ترین عبارت کدام است؟", "mcq", 1.0,
            listOf("گزینه یک", "گزینه دو", "گزینه سه", "گزینه چهار"), answerIndex = null)
        assertTrue(questionReviewIssue(invalid).contains("کلید"))
        assertEquals("", questionReviewIssue(invalid.copy(answerIndex = 2)))
    }
}
