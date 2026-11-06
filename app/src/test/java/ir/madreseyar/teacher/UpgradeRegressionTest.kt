package ir.madreseyar.teacher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpgradeRegressionTest {
    @Test fun questionImageLegacyServerPathGetsCpanelBasePath() {
        val resolved = resolveQuestionImageUrl("https://school.example/app/", "/student/media/question-images/q1.png")
        assertEquals("https://school.example/app/student/media/question-images/q1.png", resolved.toString())
    }

    @Test fun questionImageAlreadyContainingBasePathIsNotDuplicated() {
        val resolved = resolveQuestionImageUrl("https://school.example/app/", "/app/student/media/question-images/q1.png")
        assertEquals("https://school.example/app/student/media/question-images/q1.png", resolved.toString())
    }

    @Test fun questionImageRejectsCrossOriginAndTraversalPaths() {
        assertNull(resolveQuestionImageUrl("https://school.example/app/", "https://attacker.example/image.png"))
        assertNull(resolveQuestionImageUrl("https://school.example/app/", "/../private/image.png"))
    }

    @Test fun apiPathRemainsInsideSchoolOrigin() {
        val url = ApiPath.resolve("https://school.example/app/", "/api/teacher/workspace?classId=3")
        assertEquals("https://school.example/app/api/teacher/workspace?classId=3", url.toString())
    }

    @Test fun apiPathRejectsInsecureAndTraversingUrls() {
        val base = "https://school.example/app/"
        listOf("https://attacker.test/api", "//attacker.test/api", "/api/../admin", "/api/a#fragment")
            .forEach { path -> assertThrows(IllegalArgumentException::class.java) { ApiPath.resolve(base, path) } }
        assertThrows(IllegalArgumentException::class.java) { ApiPath.resolve("http://school.example/app/", "/api/staff/me") }
    }

    @Test fun imageMultipleChoicePreservesAutoGradingAnswerAndImage() {
        val src = JSONObject()
            .put("id", "image-1").put("type", "image")
            .put("questionText", "کدام تصویر درست است؟")
            .put("imageDataUrl", "data:image/png;base64,AA==")
            .put("choices", JSONArray(listOf("اول", "دوم", "سوم", "چهارم")))
            .put("answer", 2).put("score", 1.5)
        val draft = requireNotNull(bankQuestionToDraft(src))
        assertEquals("mcq", draft.type)
        assertEquals(2, draft.answerIndex)
        assertEquals("image", draft.visualType)
        assertEquals(4, draft.choices.size)
        assertEquals(1.5, draft.score, 0.0001)
    }

    @Test fun imageBankQuestionMatchesVisualFilterEvenWhenAnswerFormatIsMcq() {
        assertTrue(questionTypeMatchesFilter("image", "mcq", "image", "image"))
        assertTrue(questionTypeMatchesFilter("mcq", "mcq", "image", "image"))
        assertFalse(questionTypeMatchesFilter("image", "mcq", "", "mcq"))
    }

    @Test fun visualQuestionModeRequiresAnImageWithoutChangingAnswerFormat() {
        assertTrue(questionImageIsRequired(visualQuestion = true, bankRequiresImage = false))
        assertTrue(questionImageIsRequired(visualQuestion = false, bankRequiresImage = true))
        assertFalse(questionImageIsRequired(visualQuestion = false, bankRequiresImage = false))
    }

    @Test fun questionImageSourceReadsLegacyImageUrlFields() {
        val src = JSONObject().put("type", "image").put("imageUrl", "/student/media/question-images/q2.webp")
        assertEquals("/student/media/question-images/q2.webp", questionImageSource(src))
    }

    @Test fun imageWithoutChoiceAnswerCannotBeAutoGradedAsFirstOption() {
        val src = JSONObject()
            .put("id", "image-2").put("type", "image")
            .put("imageDataUrl", "data:image/png;base64,AA==")
            .put("choices", JSONArray(listOf("اول", "دوم", "سوم", "چهارم")))
        assertNull(bankQuestionToDraft(src))
    }

    @Test fun imageShortAnswerRemainsShortAnswer() {
        // A publishable question must have a prompt; its picture does not change its answer type.
        val src = JSONObject().put("type", "image")
            .put("questionText", "با توجه به تصویر، پاسخ کوتاه بنویسید.")
            .put("imageDataUrl", "data:image/png;base64,AA==")
            .put("referenceAnswer", "پاسخ نمونه")
        val draft = requireNotNull(bankQuestionToDraft(src))
        assertEquals("short_answer", draft.type)
        assertEquals("پاسخ نمونه", draft.answerText)
        assertEquals("image", draft.visualType)
        assertEquals("data:image/png;base64,AA==", draft.imageDataUrl)
    }

    @Test fun imageWithoutQuestionTextIsRepairableButNotAutomaticallySelected() {
        // Bank records missing the prompt stay visible for teacher repair, never auto-selected.
        val src = JSONObject().put("type", "image")
            .put("imageDataUrl", "data:image/png;base64,AA==")
            .put("referenceAnswer", "پاسخ نمونه")
        val editable = requireNotNull(bankQuestionToEditableDraft(src))
        assertEquals("short_answer", editable.type)
        assertEquals("", editable.text)
        assertNull(bankQuestionToDraft(src))
    }
    @Test fun trueFalseBankQuestionRequiresExplicitKey() {
        assertNull(bankQuestionToDraft(JSONObject().put("id", "tf-missing").put("type", "truefalse")
            .put("questionText", "این یک عبارت آزمونی است.")))
        val falseQuestion = JSONObject().put("id", "tf-explicit").put("type", "truefalse")
            .put("questionText", "این یک عبارت آزمونی است.").put("answer", false)
        val draft = requireNotNull(bankQuestionToDraft(falseQuestion))
        assertEquals("true_false", draft.type)
        assertFalse(draft.boolAnswer)
    }

    @Test fun duplicateChoicesInBankAreRejectedForAutoGrading() {
        val source = JSONObject().put("id", "bad-mcq").put("type", "mcq")
            .put("questionText", "چهار گزینه متفاوت لازم است؟")
            .put("choices", JSONArray(listOf("الف", "الف", "ج", "د"))).put("answer", 0)
        assertNull(bankQuestionToDraft(source))
    }

}
