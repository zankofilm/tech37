package ir.madreseyar.teacher

import org.json.JSONObject

fun main() {
    val contacts = listOf(
        JSONObject().put("studentId", "a").put("subject", "علوم"),
        JSONObject().put("studentId", "b").put("subject", "ریاضی"))
    check(findContactForTarget(contacts, MessageTarget("a", "علوم")) != null)
    check(findContactForTarget(contacts, MessageTarget("a", "ریاضی")) == null)
    check(findContactForTarget(contacts, MessageTarget("c", "علوم")) == null)
    check(findContactForTarget(contacts, MessageTarget("", "علوم")) == null)
    val good = QuestionReviewMeta(text="کدام درست است؟",type="mcq",score=1.0,
        choices=listOf("الف","ب","ج","د"),answerIndex=2)
    check(questionTypeMatchesFilter("image", "mcq", "image", "image"))
    check(questionTypeMatchesFilter("mcq", "mcq", "image", "image"))
    check(!questionTypeMatchesFilter("image", "mcq", "", "mcq"))
    check(questionImageIsRequired(visualQuestion = true, bankRequiresImage = false))
    check(questionImageIsRequired(visualQuestion = false, bankRequiresImage = true))
    check(!questionImageIsRequired(visualQuestion = false, bankRequiresImage = false))
    check(questionReviewIssue(good).isEmpty())
    check(questionReviewIssue(good.copy(answerIndex=null)).contains("کلید"))
    check(questionReviewIssue(good.copy(choices=listOf("الف", "الف", "ج", "د"))).contains("تکراری"))
    check(bankQuestionMatchesSearch("كتاب",listOf("کتاب علوم")))
    check(!bankQuestionMatchesSearch("ریاضی",listOf("کتاب علوم")))
    check(ApiPath.resolve("https://school.example/app/", "/api/staff/me").toString() == "https://school.example/app/api/staff/me")
    val rejected = listOf("/api/../admin", "https://example.com/evil", "/admin")
    rejected.forEach { path -> check(runCatching { ApiPath.resolve("https://school.example/app/", path) }.isFailure) }
    check(isSupportedQuestionImageReference("/student/media/question-images/q1.png"))
    check(questionReviewIssue(good.copy(imageDataUrl="/student/media/question-images/q1.png", imageRequired=true)).isEmpty())
    check(resolveQuestionImageUrl("https://school.example/app/", "/student/media/question-images/q1.png").toString() ==
        "https://school.example/app/student/media/question-images/q1.png")
    check(resolveQuestionImageUrl("https://school.example/app/", "/app/student/media/question-images/q1.png").toString() ==
        "https://school.example/app/student/media/question-images/q1.png")
    check(resolveQuestionImageUrl("https://school.example/app/", "https://attacker.example/image.png") == null)
    check(resolveQuestionImageUrl("https://school.example/app/", "/../private/image.png") == null)
    println("PASS local smoke: message scope, question review, bank search, API URL guard, image path normalization and origin checks")
}
