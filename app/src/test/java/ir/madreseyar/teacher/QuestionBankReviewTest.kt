package ir.madreseyar.teacher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionBankReviewTest {
    private fun mcq(answer: Int? = 1, choices: List<String> = listOf("یک", "دو", "سه", "چهار")) =
        QuestionReviewMeta("چه عددی بزرگ‌تر است؟", "mcq", 1.0, choices = choices, answerIndex = answer)

    @Test fun validMcqPasses() = assertEquals("", questionReviewIssue(mcq()))
    @Test fun mcqWithoutKeyMustBeFixed() = assertTrue(questionReviewIssue(mcq(null)).contains("کلید"))
    @Test fun missingOptionMustBeFixed() = assertTrue(questionReviewIssue(mcq(1, listOf("یک", "", "سه", "چهار"))).contains("چهار گزینه"))
    @Test fun duplicateOptionMustBeFixed() = assertTrue(questionReviewIssue(mcq(1, listOf("یک", "دو", "دو", "چهار"))).contains("تکراری"))
    @Test fun unknownTrueFalseMustBeFixed() = assertTrue(questionReviewIssue(
        QuestionReviewMeta("آب مایع است", "true_false", 1.0, boolAnswerKnown = false)).contains("صحیح/غلط"))
    @Test fun imageMustBeProvided() = assertTrue(questionReviewIssue(
        QuestionReviewMeta("به شکل نگاه کنید", "mcq", 1.0, listOf("۱", "۲", "۳", "۴"), 0, imageRequired = true)).contains("تصویر"))
    @Test fun shortAnswerNeedsAnswer() = assertTrue(questionReviewIssue(
        QuestionReviewMeta("نام کتاب؟", "short_answer", 1.0)).contains("پاسخ"))
    @Test fun serverMediaPathIsAcceptedAsQuestionImage() = assertTrue(
        questionReviewIssue(QuestionReviewMeta("سؤال تصویری", "mcq", 1.0,
            listOf("الف", "ب", "ج", "د"), 0,
            imageDataUrl = "/student/media/question-images/q1.png", imageRequired = true)).isEmpty())

    @Test fun serverMediaPathIsRecognizedAsImageReference() = assertTrue(
        isSupportedQuestionImageReference("/student/media/question-images/q1.png"))

    @Test fun persianSearchNormalizesArabicCharacters() = assertTrue(
        bankQuestionMatchesSearch("کتاب فارسی", listOf("كِتابِ فارسي".replace("ِ", ""))))
    @Test fun matchesOptions() = assertTrue(bankQuestionMatchesSearch("دما", listOf("علوم", "گرما", "دما")))
}
