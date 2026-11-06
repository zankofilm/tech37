package ir.madreseyar.teacher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.random.Random

private val XBrand = Color(0xFF079C9A)
private val XBrandDark = Color(0xFF075A63)
private val XBg = Color(0xFFF3F9FB)
private val XText = Color(0xFF12313A)
private val XMuted = Color(0xFF71838A)
private val XSuccess = Color(0xFF168B5B)
private val XWarning = Color(0xFFE58A19)

private fun JSONArray.examObjects(): List<JSONObject> = buildList {
    for (i in 0 until length()) optJSONObject(i)?.let(::add)
}

internal data class ExamQuestionDraft(
    val id: String = "q_android_${UUID.randomUUID().toString().replace("-", "").take(14)}",
    val type: String,
    val text: String,
    val score: Double,
    val chapterId: String,
    val choices: List<String> = emptyList(),
    val answerIndex: Int? = null,
    val answerText: String = "",
    val boolAnswer: Boolean = true,
    val boolAnswerKnown: Boolean = true,
    val sourceQuestionId: String = "",
    val sourceChapterId: String = "",
    val explanation: String = "",
    val visualType: String = "",
    val imageDataUrl: String = "",
)

private fun questionTypeLabel(type: String) = when (type) {
    "mcq" -> "چهارگزینه‌ای"
    "descriptive" -> "تشریحی"
    "true_false" -> "صحیح / غلط"
    "fill_blank" -> "جای خالی"
    "short_answer" -> "پاسخ کوتاه"
    else -> "سؤال"
}

private fun bankTypeLabel(type: String) = when (type) {
    "mcq" -> "چهارگزینه‌ای"
    "descriptive" -> "تشریحی"
    "truefalse" -> "صحیح / غلط"
    "fillblank" -> "جای خالی"
    "short" -> "پاسخ کوتاه"
    "image" -> "تصویری"
    else -> type
}

private fun bankQuestionAllowed(question: JSONObject, examCategory: String, academicPeriod: String): Boolean {
    val scope = question.optString("assessmentScope", "shared").ifBlank { "shared" }
    return if (examCategory == "final") {
        scope == "shared" || (scope == "final" && question.optString("academicPeriod") == academicPeriod)
    } else scope == "shared" || scope == "ongoing"
}

/** Bank images may be auto-graded MCQ; a missing answer must never silently default to A. */
private fun bankAnswerIndex(question: JSONObject, choices: List<String>): Int? {
    val raw = listOf("answer", "answerIndex", "correctOptionIndex")
        .firstNotNullOfOrNull { field -> question.opt(field)?.takeUnless { it == JSONObject.NULL } }
    val answer = when (raw) {
        is Number -> raw.toInt().takeIf { raw.toDouble() == it.toDouble() && it in 0..3 }
        is String -> {
            val trimmed = raw.trim()
            trimmed.toIntOrNull()?.takeIf { it in 0..3 }
                ?: when (trimmed.lowercase()) {
                    "a", "الف" -> 0
                    "b", "ب" -> 1
                    "c", "ج" -> 2
                    "d", "د" -> 3
                    else -> choices.indexOfFirst { it.trim() == trimmed }.takeIf { it >= 0 }
                }
        }
        else -> null
    }
    return answer?.takeIf { it in 0..3 }
}

/** Read a bank record even if incomplete, so the teacher can repair a private exam copy. */
internal fun bankQuestionToEditableDraft(question: JSONObject): ExamQuestionDraft? {
    val sourceType = question.optString("type")
    val rawImage = questionImageSource(question)
    val rawChoices = question.optJSONArray("choices")?.let { arr -> List(arr.length()) { arr.optString(it) } }.orEmpty()
    val mappedType = when (sourceType) {
        "mcq" -> "mcq"
        "descriptive" -> "descriptive"
        "truefalse" -> "true_false"
        "fillblank" -> "fill_blank"
        "short" -> "short_answer"
        "image" -> if (rawChoices.isNotEmpty()) "mcq" else "short_answer"
        else -> return null
    }
    val rawBoolean = question.opt("answer")
    val referenceAnswer = question.optString("referenceAnswer")
    val knownBool = when (rawBoolean) {
        is Boolean -> rawBoolean
        else -> when (listOf(referenceAnswer, rawBoolean?.toString().orEmpty())
            .firstOrNull { it.isNotBlank() }?.trim()?.lowercase()) {
            "صحیح", "درست", "بله", "true", "1" -> true
            "غلط", "نادرست", "خیر", "false", "0" -> false
            else -> null
        }
    }
    return ExamQuestionDraft(
        type = mappedType,
        text = question.optString("questionText").ifBlank { question.optString("text") }.trim(),
        score = question.optDouble("score", 1.0).takeIf { it.isFinite() && it > 0 && it <= 20 } ?: 1.0,
        chapterId = question.optString("chapterId"),
        choices = if (mappedType == "mcq") List(4) { rawChoices.getOrNull(it).orEmpty() } else rawChoices,
        answerIndex = if (mappedType == "mcq") bankAnswerIndex(question, rawChoices) else null,
        answerText = referenceAnswer,
        boolAnswer = knownBool ?: true,
        boolAnswerKnown = knownBool != null,
        sourceQuestionId = question.optString("id"),
        sourceChapterId = question.optString("chapterId"),
        explanation = question.optString("explanation"),
        visualType = if (sourceType == "image" || rawImage.isNotBlank()) "image" else "",
        imageDataUrl = rawImage,
    )
}

private fun ExamQuestionDraft.reviewIssue(imageRequired: Boolean = false): String = questionReviewIssue(
    QuestionReviewMeta(text, type, score, choices, answerIndex, answerText, boolAnswerKnown, imageDataUrl, imageRequired)
)

/** Only validated bank records are added without the teacher repairing them. */
internal fun bankQuestionToDraft(question: JSONObject): ExamQuestionDraft? {
    val draft = bankQuestionToEditableDraft(question) ?: return null
    return draft.takeIf { it.reviewIssue(question.optString("type") == "image").isBlank() }
}

private fun formatLocalDateTime(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.format(date)

private fun defaultSchedulePair(durationMinutes: Int): Pair<String, String> {
    val start = Calendar.getInstance().apply { add(Calendar.MINUTE, 15); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val end = (start.clone() as Calendar).apply { add(Calendar.MINUTE, durationMinutes.coerceAtLeast(1) + 30) }
    return formatLocalDateTime(start.time) to formatLocalDateTime(end.time)
}

private fun localInputToIso(value: String): String? = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
    val date = parser.parse(value.trim()) ?: return@runCatching null
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
}.getOrNull()

private fun dateToIsoUtc(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)

private fun immediateSchedule(durationMinutes: Int): Pair<String, String> {
    val now = System.currentTimeMillis()
    return dateToIsoUtc(Date(now - 60_000L)) to dateToIsoUtc(Date(now + (durationMinutes.coerceAtLeast(1) + 45L) * 60_000L))
}

private fun shuffleMcqChoices(question: ExamQuestionDraft, random: Random): ExamQuestionDraft {
    if (question.type != "mcq" || question.choices.size != 4 || question.answerIndex !in 0..3) return question
    val answer = question.answerIndex ?: return question
    val rows = question.choices.mapIndexed { i, text -> i to text }.shuffled(random)
    val newAnswer = rows.indexOfFirst { it.first == answer }
    return question.copy(choices = rows.map { it.second }, answerIndex = newAnswer)
}

@Composable
fun ExamStudioScreen(workspace: JSONObject?, initialBankQuestionId: String? = null) {
    val scope = rememberCoroutineScope()
    val assignments = workspace?.optJSONArray("assignments")?.examObjects().orEmpty()
    var library by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf("") }
    var libraryRefreshKey by remember { mutableIntStateOf(0) }
    var libraryLoading by remember { mutableStateOf(true) }
    var assignmentIndex by remember { mutableIntStateOf(0) }
    var bookIndex by remember { mutableIntStateOf(0) }
    var examCategory by remember { mutableStateOf("ongoing") }
    var academicPeriod by remember { mutableStateOf("semester1") }
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("20") }
    var showInstantResult by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<ExamQuestionDraft>>(emptyList()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var policyLoading by remember { mutableStateOf(false) }
    var continuousMax by remember { mutableStateOf<Double?>(null) }
    var finalMax by remember { mutableStateOf<Double?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var success by remember { mutableStateOf("") }
    var bankPickerOpen by remember { mutableStateOf(false) }
    var expandedExamQuestionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var quickImportOpen by remember { mutableStateOf(false) }
    var deleteQuestionId by remember { mutableStateOf<String?>(null) }
    var lastRemoved by remember { mutableStateOf<Pair<Int, ExamQuestionDraft>?>(null) }
    var audiencePickerOpen by remember { mutableStateOf(false) }
    var previewOpen by remember { mutableStateOf(false) }
    var customAudience by remember { mutableStateOf(false) }
    var selectedStudentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shuffleQuestions by remember { mutableStateOf(true) }
    var shuffleChoices by remember { mutableStateOf(false) }
    var scheduleEnabled by remember { mutableStateOf(false) }
    val initialSchedule = remember { defaultSchedulePair(20) }
    var startInput by remember { mutableStateOf(initialSchedule.first) }
    var endInput by remember { mutableStateOf(initialSchedule.second) }
    var pendingExamId by remember { mutableStateOf("") }
    var pendingBankDeepLink by remember(initialBankQuestionId) { mutableStateOf(initialBankQuestionId.orEmpty()) }

    LaunchedEffect(libraryRefreshKey) {
        libraryLoading = true
        try {
            library = ApiClient.get("/api/staff/teacher-approved-questions/p67")
            loadError = ""
        } catch (e: Throwable) {
            loadError = e.message ?: "دریافت مخزن سؤال ناموفق بود."
        } finally {
            libraryLoading = false
        }
    }

    val assignment = assignments.getOrNull(assignmentIndex)
    val subject = assignment?.optString("subject").orEmpty()
    val books = library?.optJSONArray("books")?.examObjects().orEmpty().filter {
        it.optString("subject") == subject || it.optString("id") == assignment?.optString("bookId")
    }
    val book = books.getOrNull(bookIndex)
    val chapters = book?.optJSONArray("chapters")?.examObjects().orEmpty()
    val totalScore = questions.sumOf { it.score }
    val classStudents = workspace?.optJSONArray("students")?.examObjects().orEmpty().filter {
        it.optString("classId") == assignment?.optString("classId")
    }
    val bankQuestions = library?.optJSONArray("questions")?.examObjects().orEmpty().filter { q ->
        q.optString("bookId") == book?.optString("id") && bankQuestionAllowed(q, examCategory, academicPeriod)
    }

    // A bank-card action opens the exact question as an editable *exam copy*.
    // The approved repository entry is never modified by this flow.
    LaunchedEffect(library, initialBankQuestionId) {
        val requestedId = pendingBankDeepLink
        if (requestedId.isBlank()) return@LaunchedEffect
        val source = library?.optJSONArray("questions")?.examObjects()
            ?.firstOrNull { it.optString("id") == requestedId } ?: return@LaunchedEffect
        val editable = bankQuestionToEditableDraft(source) ?: return@LaunchedEffect
        val targetAssignment = assignments.indexOfFirst { a ->
            a.optString("subject") == source.optString("subject") ||
                a.optString("bookId") == source.optString("bookId")
        }
        if (targetAssignment < 0) {
            error = "درس مربوط به سؤال در تخصیص‌های تدریس شما پیدا نشد."
            pendingBankDeepLink = ""
            return@LaunchedEffect
        }
        assignmentIndex = targetAssignment
        val targetSubject = assignments[targetAssignment].optString("subject")
        val eligibleBooks = library?.optJSONArray("books")?.examObjects().orEmpty().filter {
            it.optString("subject") == targetSubject || it.optString("id") == assignments[targetAssignment].optString("bookId")
        }
        bookIndex = eligibleBooks.indexOfFirst { it.optString("id") == source.optString("bookId") }
            .takeIf { it >= 0 } ?: 0
        questions = listOf(editable)
        editingIndex = 0
        editorOpen = true
        pendingBankDeepLink = ""
    }

    LaunchedEffect(examCategory, academicPeriod, assignmentIndex, subject) {
        if (examCategory != "final" || assignment == null || subject.isBlank()) {
            continuousMax = null; finalMax = null; return@LaunchedEffect
        }
        policyLoading = true
        try {
            val c = URLEncoder.encode(assignment.optString("classId"), "UTF-8")
            val s = URLEncoder.encode(subject, "UTF-8")
            val gradebook = ApiClient.get("/api/staff/gradebook?classId=$c&subject=$s&academicPeriod=$academicPeriod")
            val continuous = gradebook.optJSONObject("config")?.optDouble("outputMaxScore", Double.NaN)
            if (continuous != null && continuous.isFinite()) {
                continuousMax = continuous
                finalMax = kotlin.math.round((20.0 - continuous) * 100.0) / 100.0
            } else {
                continuousMax = null; finalMax = null
            }
        } catch (e: Throwable) {
            continuousMax = null; finalMax = null
            error = "بارم آزمون پایانی از سرور خوانده نشد: ${e.message ?: "خطا"}"
        } finally { policyLoading = false }
    }

    fun validateBeforeSubmit(forPublish: Boolean): String? {
        if (assignment == null) return "کلاس و درس انتخاب نشده است."
        if (book == null) return "کتاب مرتبط با این درس پیدا نشد."
        if (chapters.isEmpty()) return "برای این کتاب فصلی ثبت نشده است."
        if (title.trim().length < 3) return "عنوان آزمون را کامل وارد کنید."
        val mins = duration.toIntOrNull() ?: return "مدت آزمون معتبر نیست."
        if (mins !in 1..300) return "مدت آزمون باید بین ۱ تا ۳۰۰ دقیقه باشد."
        if (questions.isEmpty()) return "حداقل یک سؤال به آزمون اضافه کنید."
        if (questions.any { it.text.isBlank() || !it.score.isFinite() || it.score <= 0.0 || it.score > 20.0 })
            return "متن و بارم سؤال‌ها را بازبینی کنید؛ هر سؤال باید متن و بارم معتبر داشته باشد."
        if (questions.any { q ->
                q.type == "mcq" && (q.choices.size != 4 || q.choices.any { it.isBlank() } ||
                    q.choices.map { it.trim() }.distinct().size != 4 || q.answerIndex !in 0..3)
            }) return "یک سؤال چهارگزینه‌ای ناقص است؛ چهار گزینه متفاوت و کلید صحیح الزامی است."
        if (questions.any { it.type == "true_false" && !it.boolAnswerKnown })
            return "پاسخ صحیح یکی از سؤال‌های صحیح/غلط مشخص نشده است."
        if (questions.any { it.type in listOf("fill_blank", "short_answer") && it.answerText.isBlank() })
            return "پاسخ صحیح سؤال‌های جای خالی و پاسخ کوتاه را مشخص کنید."
        if (totalScore <= 0) return "جمع بارم سؤال‌ها باید بیشتر از صفر باشد."
        if (customAudience && selectedStudentIds.isEmpty()) return "حداقل یک دانش‌آموز را انتخاب کنید یا حالت «همه کلاس» را فعال کنید."
        if (examCategory == "final") {
            val max = finalMax ?: return "بارم پایانی هنوز از سرور دریافت نشده است."
            if (kotlin.math.abs(totalScore - max) > 0.001) return "جمع بارم سؤال‌ها باید دقیقاً $max باشد؛ اکنون $totalScore است."
        }
        if (forPublish && scheduleEnabled) {
            localInputToIso(startInput) ?: return "زمان شروع معتبر نیست. نمونه: 2026-11-02 08:00"
            localInputToIso(endInput) ?: return "زمان پایان معتبر نیست. نمونه: 2026-11-02 09:00"
            // Parsing is repeated below by the server; here we only reject an obviously reversed local range.
            val localParser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
            val localStart = runCatching { localParser.parse(startInput.trim())?.time }.getOrNull()
            val localEnd = runCatching { localParser.parse(endInput.trim())?.time }.getOrNull()
            if (localStart == null || localEnd == null || localEnd <= localStart) return "زمان پایان باید بعد از زمان شروع باشد."
            if (localEnd - localStart < mins * 60_000L) return "بازه آزمون باید حداقل به اندازه مدت آزمون باشد."
        }
        return null
    }

    fun preparedQuestionsFor(examId: String): List<ExamQuestionDraft> {
        if (!shuffleChoices) return questions
        val random = Random(examId.hashCode())
        return questions.map { shuffleMcqChoices(it, random) }
    }

    fun questionsJson(rows: List<ExamQuestionDraft>): JSONArray {
        val out = JSONArray()
        rows.forEach { q ->
            val row = JSONObject()
                .put("id", q.id)
                .put("type", q.type)
                .put("text", q.text.trim())
                .put("score", q.score)
                .put("chapterId", q.chapterId)
            if (q.sourceQuestionId.isNotBlank()) row.put("sourceQuestionId", q.sourceQuestionId)
            if (q.sourceChapterId.isNotBlank()) row.put("sourceChapterId", q.sourceChapterId)
            if (q.explanation.isNotBlank()) row.put("explanation", q.explanation)
            if (q.visualType.isNotBlank()) row.put("visualType", q.visualType)
            if (q.imageDataUrl.isNotBlank()) row.put("imageDataUrl", q.imageDataUrl)
            when (q.type) {
                "mcq" -> row.put("choices", JSONArray(q.choices)).put("answer", requireNotNull(q.answerIndex) { "پاسخ صحیح سؤال تستی مشخص نیست." })
                "true_false" -> row.put("answer", q.boolAnswer)
                "fill_blank" -> row.put("acceptedAnswers", JSONArray().put(q.answerText.trim())).put("answer", q.answerText.trim())
                "short_answer", "descriptive" -> row.put("referenceAnswer", q.answerText.trim())
            }
            out.put(row)
        }
        return out
    }

    suspend fun submit(status: String) {
        error = ""; success = ""
        val publishing = status == "active"
        validateBeforeSubmit(publishing)?.let { error = it; return }
        val a = assignment ?: return
        val b = book ?: return
        val mins = duration.toInt()
        val examId = pendingExamId.ifBlank {
            "ex_android_${UUID.randomUUID().toString().replace("-", "").take(18)}".also { pendingExamId = it }
        }
        val preparedQuestions = preparedQuestionsFor(examId)
        val qJson = questionsJson(preparedQuestions)
        val selectedChapterIds = preparedQuestions.map { it.chapterId }.filter { it.isNotBlank() }.distinct()
        val schedule = if (publishing) {
            if (scheduleEnabled) (localInputToIso(startInput)!! to localInputToIso(endInput)!!)
            else immediateSchedule(mins)
        } else {
            if (scheduleEnabled) ((localInputToIso(startInput) ?: "") to (localInputToIso(endInput) ?: "")) else ("" to "")
        }
        val audience = if (customAudience) selectedStudentIds.toList() else emptyList()
        val exam = JSONObject()
            .put("id", examId)
            .put("title", title.trim())
            .put("classId", a.optString("classId"))
            .put("bookId", b.optString("id"))
            .put("subject", subject)
            .put("duration", mins)
            .put("examCategory", examCategory)
            .put("academicPeriod", academicPeriod)
            .put("selectedChapterIds", JSONArray(selectedChapterIds))
            .put("targetScore", totalScore)
            .put("showInstantResult", showInstantResult && examCategory == "ongoing")
            .put("shuffleQuestions", shuffleQuestions)
            .put("shuffleOptionsOnce", shuffleChoices)
            .put("studentIds", JSONArray(audience))
            .put("startAt", schedule.first)
            .put("endAt", schedule.second)
            .put("status", "draft")
            .put("questions", qJson)
        busy = true
        try {
            var draftConfirmed = false
            try {
                ApiClient.post("/api/staff/teacher-exams/p673", JSONObject().put("exam", exam))
                draftConfirmed = true
            } catch (createError: ApiException) {
                // If publication succeeded but its response was lost, P67.6 can verify the same publish request idempotently.
                if (!(publishing && createError.status == 409)) throw createError
            }
            if (publishing) {
                val fields = JSONObject()
                    .put("title", title.trim())
                    .put("classId", a.optString("classId"))
                    .put("bookId", b.optString("id"))
                    .put("examCategory", examCategory)
                    .put("academicPeriod", academicPeriod)
                    .put("duration", mins)
                    .put("selectedChapterIds", JSONArray(selectedChapterIds))
                    .put("questions", qJson)
                    .put("studentIds", JSONArray(audience))
                    .put("showInstantResult", showInstantResult && examCategory == "ongoing")
                    .put("startAt", schedule.first)
                    .put("endAt", schedule.second)
                ApiClient.post("/api/staff/teacher-exams/p676", JSONObject().put("action", "publish").put("examId", examId).put("fields", fields))
                success = if (scheduleEnabled) "آزمون با زمان‌بندی تعیین‌شده منتشر شد ✓" else "آزمون منتشر شد و برای کلاس فعال است ✓"
            } else {
                if (!draftConfirmed) throw IllegalStateException("تأیید ذخیره پیش‌نویس دریافت نشد.")
                success = "پیش‌نویس آزمون روی سرور ذخیره شد ✓"
            }
            pendingExamId = ""
        } catch (e: Throwable) { error = e.message ?: "ثبت آزمون ناموفق بود." }
        finally { busy = false }
    }

    Column(Modifier.fillMaxSize().background(XBg)) {
        TeacherCompactHeader("استودیو طراحی آزمون", "آزمون مستمر و پایانی با ثبت مستقیم روی سرور")

        if (loadError.isNotBlank()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(loadError, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            TextButton(onClick = { libraryRefreshKey++ }, enabled = !libraryLoading) { Text("تلاش مجدد") }
        }
        if (busy || policyLoading || libraryLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = XBrand)

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ExamStudioCard {
                    Text("نوع آزمون", fontWeight = FontWeight.Black, color = XText)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = examCategory == "ongoing",
                            onClick = { examCategory = "ongoing"; showInstantResult = true; error = "" },
                            label = { Text("آزمون مستمر") },
                            leadingIcon = { Icon(Icons.Default.AutoGraph, null, Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            selected = examCategory == "final",
                            onClick = { examCategory = "final"; showInstantResult = false; error = "" },
                            label = { Text("آزمون پایانی") },
                            leadingIcon = { Icon(Icons.Default.WorkspacePremium, null, Modifier.size(18.dp)) },
                        )
                    }
                    Text(
                        if (examCategory == "ongoing") "برای ارزشیابی‌های کلاسی، فصل، میان‌ترم و آزمون‌های مستمر."
                        else "برای امتحان رسمی پایان نیم‌سال؛ بارم از سیاست رسمی مدرسه خوانده می‌شود.",
                        color = XMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }

            item {
                ExamStudioCard {
                    Text("نیم‌سال", fontWeight = FontWeight.Black, color = XText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = academicPeriod == "semester1", onClick = { academicPeriod = "semester1" }, label = { Text("نیم‌سال اول") })
                        FilterChip(selected = academicPeriod == "semester2", onClick = { academicPeriod = "semester2" }, label = { Text("نیم‌سال دوم") })
                    }
                    if (examCategory == "final") {
                        Spacer(Modifier.height(8.dp))
                        Surface(color = Color(0xFFE7F8F2), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("بارم رسمی پایان‌ترم", fontWeight = FontWeight.Bold, color = XBrandDark)
                                if (policyLoading) Text("در حال دریافت بارم مصوب…", color = XMuted)
                                else if (finalMax != null) Text("مستمر: ${continuousMax ?: 0.0}  +  پایانی: ${finalMax}  =  ۲۰", color = XText)
                                else Text("برای نمایش بارم، کلاس و درس معتبر را انتخاب کنید.", color = XMuted)
                            }
                        }
                    }
                }
            }

            item {
                ExamStudioCard {
                    Text("کلاس و درس", fontWeight = FontWeight.Black, color = XText)
                    if (assignments.isEmpty()) Text("تخصیص تدریس فعالی برای شما ثبت نشده است.", color = MaterialTheme.colorScheme.error)
                    else assignments.forEachIndexed { i, x ->
                        FilterChip(
                            selected = i == assignmentIndex,
                            onClick = { assignmentIndex = i; bookIndex = 0; questions = emptyList(); customAudience = false; selectedStudentIds = emptySet(); pendingExamId = ""; error = "" },
                            label = { Text("${x.optString("subject")} · ${x.optString("className", x.optString("classId"))}") }
                        )
                    }
                    if (books.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp)); Text("کتاب", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        books.forEachIndexed { i, x -> FilterChip(selected = i == bookIndex, onClick = { bookIndex = i; questions = emptyList(); pendingExamId = "" }, label = { Text(x.optString("title", x.optString("subject"))) }) }
                    }
                    if (chapters.isNotEmpty()) Text("${chapters.size} فصل برای افزودن سؤال در دسترس است.", color = XMuted, fontSize = 11.sp)
                }
            }

            item {
                ExamStudioCard {
                    Text("مشخصات آزمون", fontWeight = FontWeight.Black, color = XText)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(title, { title = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("عنوان آزمون") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("مدت آزمون (دقیقه)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    if (examCategory == "ongoing") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Switch(showInstantResult, { showInstantResult = it })
                            Spacer(Modifier.width(7.dp)); Text("نمایش نتیجه فوری به دانش‌آموز", color = XText, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                ExamStudioCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = XBrand)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("زمان‌بندی آزمون", fontWeight = FontWeight.Black, color = XText)
                            Text(if (scheduleEnabled) "شروع و پایان مشخص؛ دانش‌آموز فقط در بازه تعیین‌شده وارد می‌شود." else "انتشار فوری؛ سرور مهلت امن متناسب با مدت آزمون ایجاد می‌کند.", color = XMuted, fontSize = 11.sp)
                        }
                        Switch(scheduleEnabled, { enabled ->
                            scheduleEnabled = enabled
                            if (enabled) {
                                val pair = defaultSchedulePair(duration.toIntOrNull() ?: 20)
                                startInput = pair.first; endInput = pair.second
                            }
                        })
                    }
                    if (scheduleEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(startInput, { startInput = it.take(16) }, Modifier.fillMaxWidth(), label = { Text("شروع · YYYY-MM-DD HH:MM") }, singleLine = true)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(endInput, { endInput = it.take(16) }, Modifier.fillMaxWidth(), label = { Text("پایان · YYYY-MM-DD HH:MM") }, singleLine = true)
                    }
                }
            }

            item {
                ExamStudioCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, null, tint = XBrand)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("مخاطبان آزمون", fontWeight = FontWeight.Black, color = XText)
                            Text(
                                if (!customAudience) "همه ${classStudents.size} دانش‌آموز این کلاس" else "${selectedStudentIds.size} دانش‌آموز انتخاب شده",
                                color = XMuted, fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = { audiencePickerOpen = true }, enabled = classStudents.isNotEmpty()) { Text("انتخاب") }
                    }
                }
            }

            item {
                ExamStudioCard {
                    Text("تنظیمات تصادفی‌سازی", fontWeight = FontWeight.Black, color = XText)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(shuffleQuestions, { shuffleQuestions = it })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("جابه‌جایی ترتیب سؤال‌ها", fontWeight = FontWeight.Bold, color = XText, fontSize = 13.sp)
                            Text("ترتیب سؤال‌ها برای اجرای آزمون قابل جابه‌جایی است.", color = XMuted, fontSize = 10.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(shuffleChoices, { shuffleChoices = it })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("جابه‌جایی گزینه‌های تستی", fontWeight = FontWeight.Bold, color = XText, fontSize = 13.sp)
                            Text("گزینه‌های چهارگزینه‌ای هنگام ثبت نهایی با حفظ کلید صحیح بازچینی می‌شوند.", color = XMuted, fontSize = 10.sp)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("سؤال‌ها", fontWeight = FontWeight.Black, fontSize = 18.sp, color = XBrandDark)
                        Text("${questions.size} سؤال · جمع بارم ${formatScore(totalScore)}", color = XMuted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text("سؤال‌های مخزن را ببینید، انتخاب کنید و قبل از افزودن به آزمون اصلاح کنید.", color = XMuted, fontSize = 11.sp)
                Spacer(Modifier.height(7.dp))
                Button(onClick = { bankPickerOpen = true }, enabled = book != null && bankQuestions.isNotEmpty(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = XBrand)) {
                    Icon(Icons.Default.LibraryBooks, null); Spacer(Modifier.width(6.dp)); Text("مشاهده و انتخاب از مخزن سؤال")
                }
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { quickImportOpen = true }, enabled = chapters.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("ورود گروهی")
                    }
                    OutlinedButton(onClick = { editingIndex = null; editorOpen = true }, enabled = chapters.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("نوشتن سؤال")
                    }
                }
                if (book != null) {
                    Text("${bankQuestions.size} سؤال مخزن در دسترس است؛ سؤال‌های ناقص را می‌توانید اصلاح کنید.", color = XMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    if (bankQuestions.isEmpty() && !libraryLoading) Text("برای کتاب و نوع آزمون انتخاب‌شده سؤالی در مخزن پیدا نشد؛ کتاب یا نوع آزمون را بررسی کنید.", color = XWarning, fontSize = 11.sp)
                }
                lastRemoved?.let {
                    TextButton(onClick = {
                        val (index, removed) = it
                        if (questions.none { q -> q.id == removed.id }) {
                            questions = questions.toMutableList().also { rows -> rows.add(index.coerceIn(0, rows.size), removed) }
                        }
                        lastRemoved = null
                    }) { Icon(Icons.Default.Undo, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("بازگرداندن آخرین سؤال حذف‌شده") }
                }
            }

            itemsIndexed(questions) { index, q ->
                ExamStudioCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(36.dp), RoundedCornerShape(12.dp), XBrand.copy(.1f)) { Box(contentAlignment = Alignment.Center) { Text("${index + 1}", fontWeight = FontWeight.Black, color = XBrandDark) } }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(questionTypeLabel(q.type), color = XBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(q.text, fontWeight = FontWeight.Bold, color = XText)
                            if (q.imageDataUrl.isNotBlank()) QuestionImagePreview(q.imageDataUrl)
                            Text("بارم ${formatScore(q.score)}", color = XMuted, fontSize = 11.sp)
                            if (q.type == "mcq" && q.answerIndex in 0..3 && q.choices.size == 4) {
                                Text("کلید صحیح: ${listOf("الف", "ب", "ج", "د")[q.answerIndex!!]} · ${q.choices[q.answerIndex!!]}", color = XSuccess, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                expandedExamQuestionIds = if (q.id in expandedExamQuestionIds) expandedExamQuestionIds - q.id else expandedExamQuestionIds + q.id
                            }, contentPadding = PaddingValues(horizontal = 2.dp)) {
                                Icon(if (q.id in expandedExamQuestionIds) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(if (q.id in expandedExamQuestionIds) "بستن جزئیات" else "نمایش گزینه‌ها و پاسخ", fontSize = 11.sp)
                            }
                            if (q.id in expandedExamQuestionIds) {
                                if (q.type == "mcq") q.choices.forEachIndexed { i, option ->
                                    Text("${listOf("الف", "ب", "ج", "د").getOrElse(i) { "•" }}. $option", fontSize = 11.sp,
                                        color = if (i == q.answerIndex) XSuccess else XText)
                                }
                                if (q.type == "true_false") Text("پاسخ صحیح: ${if (q.boolAnswer) "صحیح" else "غلط"}", color = XSuccess, fontSize = 11.sp)
                                if (q.answerText.isNotBlank()) Text("پاسخ مرجع: ${q.answerText}", color = XSuccess, fontSize = 11.sp)
                                if (q.explanation.isNotBlank()) Text("توضیح: ${q.explanation}", color = XMuted, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    questions = moveQuestion(questions, index, index - 1)
                                }, enabled = index > 0, contentPadding = PaddingValues(horizontal = 4.dp)) { Icon(Icons.Default.ArrowUpward, "بالا", Modifier.size(15.dp)); Text("بالا", fontSize = 10.sp) }
                                TextButton(onClick = {
                                    questions = moveQuestion(questions, index, index + 1)
                                }, enabled = index < questions.lastIndex, contentPadding = PaddingValues(horizontal = 4.dp)) { Icon(Icons.Default.ArrowDownward, "پایین", Modifier.size(15.dp)); Text("پایین", fontSize = 10.sp) }
                                TextButton(onClick = {
                                    questions = questions + q.copy(
                                        id = "q_android_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                                        sourceQuestionId = ""
                                    )
                                }, contentPadding = PaddingValues(horizontal = 4.dp)) { Icon(Icons.Default.ContentCopy, "کپی", Modifier.size(15.dp)); Text("کپی", fontSize = 10.sp) }
                            }
                        }
                        Column {
                            IconButton(onClick = { editingIndex = index; editorOpen = true }) { Icon(Icons.Default.Edit, "ویرایش سؤال", tint = XBrandDark) }
                            IconButton(onClick = { deleteQuestionId = q.id }) { Icon(Icons.Default.DeleteOutline, "حذف سؤال", tint = Color(0xFFC64D4D)) }
                        }
                    }
                }
            }

            if (questions.isEmpty()) item {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Quiz, null, tint = XBrand, modifier = Modifier.size(42.dp))
                        Text("هنوز سؤالی اضافه نشده", fontWeight = FontWeight.Black, color = XText, modifier = Modifier.padding(top = 8.dp))
                        Text("چهارگزینه‌ای، تشریحی، صحیح/غلط، جای‌خالی و پاسخ کوتاه پشتیبانی می‌شود.", color = XMuted, fontSize = 11.sp)
                    }
                }
            }

            item {
                ExamStudioCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("جمع بارم سؤال‌ها", fontWeight = FontWeight.Bold, color = XText)
                        Text(formatScore(totalScore), fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (examCategory == "final" && finalMax != null && kotlin.math.abs(totalScore - finalMax!!) > .001) XWarning else XBrand)
                    }
                    if (examCategory == "final" && finalMax != null) {
                        val remaining = kotlin.math.round((finalMax!! - totalScore) * 100) / 100.0
                        Text(
                            when {
                                kotlin.math.abs(remaining) < .001 -> "بارم آزمون پایانی کامل است ✓"
                                remaining > 0 -> "${formatScore(remaining)} نمره دیگر باید بین سؤال‌ها توزیع شود."
                                else -> "جمع بارم ${formatScore(-remaining)} نمره بیشتر از سقف رسمی است."
                            },
                            color = if (kotlin.math.abs(remaining) < .001) XSuccess else XWarning,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }

            if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(4.dp)) }
            if (success.isNotBlank()) item { Text(success, color = XSuccess, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp)) }

            item {
                OutlinedButton(enabled = !busy && questions.isNotEmpty(), onClick = { previewOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Preview, null); Spacer(Modifier.width(5.dp)); Text("پیش‌نمایش برگه و تنظیمات")
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { scope.launch { submit("draft") } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text("ذخیره پیش‌نویس")
                    }
                    Button(enabled = !busy, onClick = { scope.launch { submit("active") } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = XBrand)) {
                        Icon(Icons.Default.Publish, null); Spacer(Modifier.width(4.dp)); Text("انتشار آزمون")
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    if (editorOpen) {
        QuestionEditorDialog(
            chapters = chapters,
            initial = editingIndex?.let { questions.getOrNull(it) },
            onDismiss = { editorOpen = false; editingIndex = null },
            onSave = { q ->
                val idx = editingIndex
                questions = if (idx == null) questions + q else questions.toMutableList().also { it[idx] = q }
                editorOpen = false; editingIndex = null
            }
        )
    }

    if (bankPickerOpen) {
        BankQuestionPickerDialog(
            questions = bankQuestions,
            chapters = chapters,
            existingSourceIds = questions.map { it.sourceQuestionId }.filter { it.isNotBlank() }.toSet(),
            onDismiss = { bankPickerOpen = false },
            onAdd = { selectedDrafts ->
                val usedIds = questions.map { it.sourceQuestionId }.filter { it.isNotBlank() }.toSet()
                val usedTexts = questions.map { normalizeQuestionText(it.text) }.toSet()
                val fresh = selectedDrafts.filter {
                    (it.sourceQuestionId.isBlank() || it.sourceQuestionId !in usedIds) &&
                        normalizeQuestionText(it.text) !in usedTexts
                }.distinctBy { normalizeQuestionText(it.text) }
                questions = questions + fresh
                error = if (fresh.size < selectedDrafts.size) "${selectedDrafts.size - fresh.size} سؤال تکراری افزوده نشد." else ""
                success = "${fresh.size} سؤال انتخابی به آزمون اضافه شد. برای هر سؤال می‌توانید ویرایش، حذف یا جابه‌جایی انجام دهید."
                bankPickerOpen = false
            }
        )
    }

    if (quickImportOpen) {
        QuickQuestionImportDialog(
            chapters = chapters,
            onDismiss = { quickImportOpen = false },
            onAdd = { rows ->
                val existingTexts = questions.map { normalizeQuestionText(it.text) }.toSet()
                val fresh = rows.filter { normalizeQuestionText(it.text) !in existingTexts }
                    .distinctBy { normalizeQuestionText(it.text) }
                questions = questions + fresh
                error = if (fresh.size < rows.size) "${rows.size - fresh.size} سؤال تکراری وارد نشد." else ""
                success = "${fresh.size} سؤال از متن وارد شد؛ پیش از انتشار کلیدها و بارم‌ها را بازبینی کنید."
                quickImportOpen = false
            }
        )
    }

    deleteQuestionId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteQuestionId = null },
            title = { Text("حذف سؤال از آزمون؟") },
            text = { Text("این سؤال فقط از آزمون در حال طراحی حذف می‌شود؛ بانک سؤال اصلی تغییری نمی‌کند.") },
            confirmButton = {
                TextButton(onClick = {
                    val i = questions.indexOfFirst { it.id == id }
                    if (i >= 0) {
                        lastRemoved = i to questions[i]
                        questions = questions.toMutableList().also { it.removeAt(i) }
                    }
                    deleteQuestionId = null
                }) { Text("حذف", color = Color(0xFFC64D4D)) }
            },
            dismissButton = { TextButton(onClick = { deleteQuestionId = null }) { Text("انصراف") } },
        )
    }

    if (audiencePickerOpen) {
        AudiencePickerDialog(
            students = classStudents,
            customAudience = customAudience,
            selectedIds = selectedStudentIds,
            onDismiss = { audiencePickerOpen = false },
            onApply = { custom, ids ->
                customAudience = custom
                selectedStudentIds = ids
                audiencePickerOpen = false
            }
        )
    }

    if (previewOpen) {
        ExamPreviewDialog(
            title = title.ifBlank { "بدون عنوان" },
            examCategory = examCategory,
            academicPeriod = academicPeriod,
            duration = duration.toIntOrNull() ?: 0,
            questions = questions,
            totalScore = totalScore,
            audienceText = if (!customAudience) "همه ${classStudents.size} دانش‌آموز کلاس" else "${selectedStudentIds.size} دانش‌آموز منتخب",
            scheduleText = if (scheduleEnabled) "$startInput  ←  $endInput" else "انتشار فوری",
            shuffleQuestions = shuffleQuestions,
            shuffleChoices = shuffleChoices,
            onDismiss = { previewOpen = false }
        )
    }
}

@Composable
private fun ExamStudioCard(content: @Composable ColumnScope.() -> Unit) {
    TeacherPanel(content = content)
}

private fun normalizeQuestionText(text: String): String = text.trim().lowercase().replace(Regex("\\s+"), " ")

private fun moveQuestion(rows: List<ExamQuestionDraft>, from: Int, to: Int): List<ExamQuestionDraft> {
    if (from !in rows.indices || to !in rows.indices || from == to) return rows
    return rows.toMutableList().apply { add(to, removeAt(from)) }
}

private data class ReadyBankQuestion(val key: String, val source: JSONObject, val draft: ExamQuestionDraft)

private fun difficultyLabel(raw: String): String = when (raw.lowercase()) {
    "easy", "ساده", "آسان" -> "آسان"
    "medium", "normal", "متوسط" -> "متوسط"
    "hard", "difficult", "سخت", "دشوار" -> "دشوار"
    else -> raw.ifBlank { "نامشخص" }
}

@Composable
private fun BankQuestionPickerDialog(
    questions: List<JSONObject>,
    chapters: List<JSONObject>,
    existingSourceIds: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (List<ExamQuestionDraft>) -> Unit,
) {
    val prepared = remember(questions) {
        questions.mapIndexedNotNull { index, source ->
            bankQuestionToEditableDraft(source)?.let { draft ->
                ReadyBankQuestion(source.optString("id").ifBlank { "local_$index" }, source, draft)
            }
        }
    }
    val unsupportedCount = questions.size - prepared.size
    var selected by remember(questions) { mutableStateOf<Set<String>>(emptySet()) }
    var excluded by remember(questions) { mutableStateOf<Set<String>>(emptySet()) }
    var edited by remember(questions) { mutableStateOf<Map<String, ExamQuestionDraft>>(emptyMap()) }
    var editKey by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var chapterFilter by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var showExcluded by remember { mutableStateOf(false) }
    var expandedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun draftOf(item: ReadyBankQuestion): ExamQuestionDraft = edited[item.key] ?: item.draft
    fun issueOf(item: ReadyBankQuestion): String = draftOf(item).reviewIssue(item.source.optString("type") == "image")
    val readyCount = prepared.count { issueOf(it).isBlank() }
    val needsFixCount = prepared.size - readyCount
    val visible = prepared.filter { entry ->
        val draft = draftOf(entry)
        val issue = issueOf(entry)
        (showExcluded || entry.key !in excluded) &&
            bankQuestionMatchesSearch(search, listOf(draft.text, draft.answerText, draft.explanation) + draft.choices) &&
            (chapterFilter.isBlank() || draft.chapterId == chapterFilter) &&
            (typeFilter.isBlank() || draft.type == typeFilter) &&
            (levelFilter.isBlank() || difficultyLabel(entry.source.optString("difficulty")) == levelFilter) &&
            when (statusFilter) {
                "ready" -> issue.isBlank()
                "fix" -> issue.isNotBlank()
                "selected" -> entry.key in selected
                else -> true
            }
    }
    val available = visible.filter { entry ->
        issueOf(entry).isBlank() && entry.key !in excluded &&
            (draftOf(entry).sourceQuestionId.isBlank() || draftOf(entry).sourceQuestionId !in existingSourceIds)
    }
    val readySelected = prepared.filter { it.key in selected && it.key !in excluded && issueOf(it).isBlank() &&
        (draftOf(it).sourceQuestionId.isBlank() || draftOf(it).sourceQuestionId !in existingSourceIds) }
    val selectedScore = readySelected.sumOf { draftOf(it).score }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.97f).fillMaxHeight(.94f),
            shape = RoundedCornerShape(20.dp), color = XBg
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مخزن سؤال‌های تأییدشده", fontSize = 18.sp, fontWeight = FontWeight.Black, color = XBrandDark)
                        Text("سؤال‌ها را بررسی و نسخه مخصوص آزمون خود را اصلاح کنید.", fontSize = 11.sp, color = XMuted)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "بستن مخزن") }
                }
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = search, onValueChange = { search = it.take(120) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("جست‌وجو در متن، پاسخ و گزینه‌ها") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(Modifier.height(5.dp))
                Text("$readyCount آماده · $needsFixCount نیازمند اصلاح · $unsupportedCount قالب ناشناخته", color = XMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    item {
                        Text("فصل", fontSize = 11.sp, color = XMuted)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            item { FilterChip(selected = chapterFilter.isBlank(), onClick = { chapterFilter = "" }, label = { Text("همه") }) }
                            itemsIndexed(chapters) { _, chapter ->
                                val id = chapter.optString("id")
                                FilterChip(selected = chapterFilter == id, onClick = { chapterFilter = id },
                                    label = { Text(chapter.optString("title", chapter.optString("name", "فصل"))) })
                            }
                        }
                        Text("نوع سؤال", fontSize = 11.sp, color = XMuted)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            item { FilterChip(selected = typeFilter.isBlank(), onClick = { typeFilter = "" }, label = { Text("همه") }) }
                            itemsIndexed(listOf("mcq", "true_false", "fill_blank", "short_answer", "descriptive")) { _, t ->
                                FilterChip(selected = typeFilter == t, onClick = { typeFilter = t },
                                    label = { Text(questionTypeLabel(t)) })
                            }
                        }
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            itemsIndexed(listOf("", "آسان", "متوسط", "دشوار")) { _, level ->
                                FilterChip(selected = levelFilter == level, onClick = { levelFilter = level },
                                    label = { Text(level.ifBlank { "همه سطوح" }, fontSize = 11.sp) })
                            }
                        }
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            itemsIndexed(listOf("all" to "همه", "ready" to "آماده", "fix" to "نیازمند اصلاح", "selected" to "انتخاب‌شده")) { _, option ->
                                FilterChip(selected = statusFilter == option.first,
                                    onClick = { statusFilter = option.first }, label = { Text(option.second) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${visible.size} سؤال مطابق فیلتر", modifier = Modifier.weight(1f), fontSize = 11.sp, color = XMuted)
                            if (excluded.isNotEmpty()) TextButton(onClick = { showExcluded = !showExcluded }) {
                                Text(if (showExcluded) "پنهان‌کردن کنارگذاشته‌ها" else "نمایش ${excluded.size} کنارگذاشته‌شده", fontSize = 11.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedButton(onClick = {
                                val choices = available.filter { it.key !in selected }
                                val recommendations = suggestApprovedQuestionIds(choices.map {
                                    QuestionSuggestionMeta(it.key, draftOf(it).chapterId, draftOf(it).type,
                                        difficultyLabel(it.source.optString("difficulty")))
                                }, 5)
                                selected = selected + recommendations.take((60 - selected.size).coerceAtLeast(0))
                            }, enabled = available.any { it.key !in selected } && selected.size < 60,
                                contentPadding = PaddingValues(horizontal = 8.dp)) { Text("انتخاب ۵", fontSize = 11.sp) }
                            OutlinedButton(onClick = {
                                val choices = available.filter { it.key !in selected }
                                val recommendations = suggestApprovedQuestionIds(choices.map {
                                    QuestionSuggestionMeta(it.key, draftOf(it).chapterId, draftOf(it).type,
                                        difficultyLabel(it.source.optString("difficulty")))
                                }, 10)
                                selected = selected + recommendations.take((60 - selected.size).coerceAtLeast(0))
                            }, enabled = available.any { it.key !in selected } && selected.size < 60,
                                contentPadding = PaddingValues(horizontal = 8.dp)) { Text("انتخاب ۱۰", fontSize = 11.sp) }
                            TextButton(onClick = { selected = emptySet() }) { Text("لغو انتخاب‌ها", fontSize = 11.sp) }
                        }
                        TextButton(onClick = { selected = selected + available.filter { it.key !in selected }
                            .take((60 - selected.size).coerceAtLeast(0)).map { it.key } }, enabled = available.isNotEmpty() && selected.size < 60) {
                            Text("انتخاب سؤال‌های این فیلتر (تا ۶۰ مورد)", fontSize = 11.sp)
                        }
                    }
                    itemsIndexed(visible, key = { _, entry -> entry.key }) { index, entry ->
                        val draft = draftOf(entry)
                        val issue = issueOf(entry)
                        val already = draft.sourceQuestionId.isNotBlank() && draft.sourceQuestionId in existingSourceIds
                        val skipped = entry.key in excluded
                        val expandable = entry.key in expandedKeys
                        Card(colors = CardDefaults.cardColors(
                            containerColor = if (skipped || already) Color(0xFFF0F3F4) else Color.White
                        )) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = already || (entry.key in selected && !skipped && issue.isBlank()),
                                        enabled = !already && !skipped && issue.isBlank() &&
                                            (entry.key in selected || selected.size < 60),
                                        onCheckedChange = { checked -> selected = if (checked) selected + entry.key else selected - entry.key }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(draft.text.ifBlank { "متن سؤال ثبت نشده" }, color = XText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${questionTypeLabel(draft.type)} · ${difficultyLabel(entry.source.optString("difficulty"))} · ${formatScore(draft.score)} نمره", color = XMuted, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { expandedKeys = if (expandable) expandedKeys - entry.key else expandedKeys + entry.key }) {
                                        Icon(if (expandable) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "نمایش سؤال و پاسخ")
                                    }
                                }
                                if (already) Text("این سؤال قبلاً در آزمون قرار گرفته است.", fontSize = 11.sp, color = XSuccess)
                                if (issue.isNotBlank()) Text("نیاز به اصلاح: $issue", fontSize = 11.sp, color = Color(0xFFC64D4D))
                                if (skipped) Text("این سؤال از انتخاب‌های فعلی کنار گذاشته شده است.", fontSize = 11.sp, color = XMuted)
                                if (expandable) {
                                    if (draft.imageDataUrl.isNotBlank()) QuestionImagePreview(draft.imageDataUrl)
                                    if (draft.type == "mcq") draft.choices.forEachIndexed { i, choice ->
                                        Text("${listOf("الف", "ب", "ج", "د").getOrElse(i) { "•" }}. ${choice.ifBlank { "گزینه خالی" }}${if (i == draft.answerIndex) "  ✓ پاسخ صحیح" else ""}",
                                            fontSize = 12.sp, color = if (i == draft.answerIndex) XSuccess else XText)
                                    }
                                    if (draft.type == "true_false" && draft.boolAnswerKnown) Text("پاسخ: ${if (draft.boolAnswer) "صحیح" else "غلط"}", color = XSuccess, fontSize = 12.sp)
                                    if (draft.type in listOf("fill_blank", "short_answer", "descriptive") && draft.answerText.isNotBlank()) Text("پاسخ: ${draft.answerText}", color = XSuccess, fontSize = 12.sp)
                                    if (draft.explanation.isNotBlank()) Text("توضیح: ${draft.explanation}", color = XMuted, fontSize = 11.sp)
                                    Text("فقط نسخه این سؤال در آزمون ویرایش می‌شود؛ مخزن اصلی تغییر نمی‌کند.", color = XMuted, fontSize = 10.sp)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { editKey = entry.key }, enabled = !already) {
                                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(3.dp))
                                        Text(if (issue.isBlank()) "ویرایش" else "اصلاح و تکمیل", fontSize = 12.sp)
                                    }
                                    TextButton(onClick = {
                                        if (skipped) excluded = excluded - entry.key else {
                                            excluded = excluded + entry.key; selected = selected - entry.key
                                        }
                                    }, enabled = !already) {
                                        Icon(if (skipped) Icons.Default.Undo else Icons.Default.RemoveCircleOutline, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(if (skipped) "بازگرداندن" else "کنار گذاشتن", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (visible.isEmpty()) item {
                        Text("سؤالی مطابق فیلتر پیدا نشد؛ فیلترها را تغییر دهید یا موارد کنارگذاشته‌شده را نمایش دهید.",
                            color = XMuted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                    }
                }
                Spacer(Modifier.height(5.dp))
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${readySelected.size} سؤال انتخاب‌شده", fontWeight = FontWeight.Bold, color = XText)
                        Text("جمع بارم: ${formatScore(selectedScore)}", color = XMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = onDismiss) { Text("بازگشت") }
                    Spacer(Modifier.width(5.dp))
                    Button(onClick = { onAdd(readySelected.map { draftOf(it) }) }, enabled = readySelected.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = XBrand)) {
                        Text("افزودن به آزمون")
                    }
                }
            }
        }
    }
    editKey?.let { key ->
        val entry = prepared.firstOrNull { it.key == key }
        if (entry != null) QuestionEditorDialog(
            chapters = chapters,
            initial = draftOf(entry),
            requireImage = entry.source.optString("type") == "image",
            onDismiss = { editKey = null },
            onSave = { updated ->
                edited = edited + (key to updated)
                if (updated.reviewIssue(entry.source.optString("type") == "image").isBlank() && key !in excluded && (key in selected || selected.size < 60)) {
                    selected = selected + key
                } else selected = selected - key
                editKey = null
            }
        )
    }
}

@Composable
private fun QuickQuestionImportDialog(
    chapters: List<JSONObject>,
    onDismiss: () -> Unit,
    onAdd: (List<ExamQuestionDraft>) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var chapterIndex by remember { mutableIntStateOf(0) }
    val parsed = remember(input) { parsePastedQuestions(input) }
    val drafts = remember(parsed, chapterIndex, chapters) {
        val chapterId = chapters.getOrNull(chapterIndex)?.optString("id").orEmpty()
        parsed.map { row ->
            ExamQuestionDraft(
                type = row.type, text = row.text, score = row.score, chapterId = chapterId,
                choices = row.choices, answerIndex = row.answerIndex,
                answerText = row.answerText, boolAnswer = row.boolAnswer
            )
        }
    }
    var selected by remember(input) { mutableStateOf(parsed.indices.filter { parsed[it].issue.isBlank() }.toSet()) }
    var edits by remember(input) { mutableStateOf<Map<Int, ExamQuestionDraft>>(emptyMap()) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    val validIndices = parsed.indices.filter { parsed[it].issue.isBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ورود سریع چند سؤال", color = XBrandDark, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("سؤال‌ها را از Word، پیام‌رسان یا فایل متنی کپی کنید. هر سؤال با «سؤال:» شروع شود و برای تستی‌ها کلید پاسخ الزامی است.", color = XMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(input, { input = it.take(30_000) }, Modifier.fillMaxWidth(),
                    label = { Text("متن سؤال‌ها") }, minLines = 5, maxLines = 9,
                    placeholder = { Text("سؤال: کدام گزینه درست است؟\nالف) گزینه اول\nب) گزینه دوم\nج) گزینه سوم\nد) گزینه چهارم\nپاسخ: ب\nبارم: ۱\n\nسؤال: توضیح دهید که ...\nنوع: تشریحی", fontSize = 11.sp) })
                Text("برای سؤال جدید از «سؤال:» یا خط جداکننده --- استفاده کنید؛ حداکثر ۶۰ سؤال در هر نوبت.", color = XMuted, fontSize = 10.sp)
                Text("فصل سؤال‌های واردشده", color = XText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(chapters) { i, chapter ->
                        FilterChip(selected = i == chapterIndex, onClick = { chapterIndex = i }, label = { Text(chapter.optString("title", chapter.optString("name", "فصل ${i+1}"))) })
                    }
                }
                Text("${parsed.size} شناسایی‌شده · ${validIndices.size} آماده · ${selected.size} انتخاب‌شده", fontSize = 11.sp, color = XMuted)
                LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(parsed) { i, row ->
                        val draft = edits[i] ?: drafts[i]
                        Card(colors = CardDefaults.cardColors(if (row.issue.isBlank()) Color.White else Color(0xFFFFF4EE))) {
                            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = i in selected, enabled = row.issue.isBlank(), onCheckedChange = { checked ->
                                    selected = if (checked) selected + i else selected - i
                                })
                                Column(Modifier.weight(1f)) {
                                    Text("${i+1}. ${draft.text.take(145)}", fontSize = 12.sp, color = XText, fontWeight = FontWeight.Bold)
                                    Text(if (row.issue.isNotBlank()) row.issue else "${questionTypeLabel(draft.type)} · ${formatScore(draft.score)} نمره", color = if (row.issue.isNotBlank()) Color(0xFFC64D4D) else XMuted, fontSize = 10.sp)
                                    if (draft.type == "mcq" && row.issue.isBlank()) Text("کلید: ${listOf("الف", "ب", "ج", "د")[draft.answerIndex ?: 0]}", color = XSuccess, fontSize = 10.sp)
                                }
                                if (row.issue.isBlank()) IconButton(onClick = { editIndex = i }) { Icon(Icons.Default.Edit, "ویرایش سؤال", tint = XBrandDark) }
                            }
                        }
                    }
                }
                if (parsed.any { it.issue.isNotBlank() }) Text("سؤال‌های ناقص انتخاب نمی‌شوند. متن را اصلاح کنید تا وارد آزمون شوند.", color = Color(0xFFC64D4D), fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(selected.sorted().filter { it in validIndices }.map { edits[it] ?: drafts[it] }) },
                enabled = selected.isNotEmpty() && chapters.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = XBrand)) {
                Text("افزودن ${selected.size} سؤال")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بازگشت") } }
    )
    editIndex?.let { index ->
        if (index in validIndices) QuestionEditorDialog(
            chapters = chapters,
            initial = edits[index] ?: drafts[index],
            onDismiss = { editIndex = null },
            onSave = { edits = edits + (index to it); editIndex = null }
        )
    }
}

@Composable
private fun AudiencePickerDialog(
    students: List<JSONObject>,
    customAudience: Boolean,
    selectedIds: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Boolean, Set<String>) -> Unit,
) {
    var custom by remember { mutableStateOf(customAudience) }
    var selected by remember(students) { mutableStateOf(if (selectedIds.isEmpty()) students.map { it.optString("id") }.toSet() else selectedIds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دانش‌آموزان دریافت‌کننده آزمون", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !custom, onClick = { custom = false })
                    Text("همه دانش‌آموزان کلاس (${students.size})")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = custom, onClick = { custom = true })
                    Text("انتخاب دانش‌آموزان خاص")
                }
                if (custom) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { selected = students.map { it.optString("id") }.toSet() }) { Text("انتخاب همه") }
                        TextButton(onClick = { selected = emptySet() }) { Text("پاک کردن") }
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        itemsIndexed(students) { _, st ->
                            val id = st.optString("id")
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = id in selected, onCheckedChange = { checked -> selected = if (checked) selected + id else selected - id })
                                Column {
                                    Text("${st.optString("firstName")} ${st.optString("lastName")}".trim().ifBlank { "دانش‌آموز" }, fontWeight = FontWeight.Bold, color = XText)
                                    Text(st.optString("studentCode"), color = XMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(custom, if (custom) selected else emptySet()) }, enabled = !custom || selected.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = XBrand)) {
                Text("تأیید مخاطبان")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun ExamPreviewDialog(
    title: String,
    examCategory: String,
    academicPeriod: String,
    duration: Int,
    questions: List<ExamQuestionDraft>,
    totalScore: Double,
    audienceText: String,
    scheduleText: String,
    shuffleQuestions: Boolean,
    shuffleChoices: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پیش‌نمایش آزمون", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    Surface(color = Color(0xFFE7F8F7), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(title, fontWeight = FontWeight.Black, color = XBrandDark, fontSize = 17.sp)
                            Text("${if (examCategory == "final") "پایانی" else "مستمر"} · ${if (academicPeriod == "semester2") "نیم‌سال دوم" else "نیم‌سال اول"} · $duration دقیقه", color = XText, fontSize = 11.sp)
                            Text("$audienceText · $scheduleText", color = XMuted, fontSize = 10.sp)
                            Text("${questions.size} سؤال · جمع بارم ${formatScore(totalScore)}", color = XText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            if (shuffleQuestions || shuffleChoices) Text("تصادفی‌سازی: ${listOfNotNull(if (shuffleQuestions) "سؤال‌ها" else null, if (shuffleChoices) "گزینه‌ها" else null).joinToString(" و ")}", color = XBrand, fontSize = 10.sp)
                        }
                    }
                }
                itemsIndexed(questions) { index, q ->
                    Card(colors = CardDefaults.cardColors(Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("سؤال ${index + 1} · ${questionTypeLabel(q.type)}", color = XBrand, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${formatScore(q.score)} نمره", color = XMuted, fontSize = 10.sp)
                            }
                            Text(q.text, color = XText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (q.imageDataUrl.isNotBlank()) QuestionImagePreview(q.imageDataUrl)
                            if (q.type == "mcq") q.choices.forEachIndexed { i, choice ->
                                Text("${listOf("الف", "ب", "ج", "د").getOrElse(i) { "•" }}. $choice", color = if (q.answerIndex == i) XSuccess else XText, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = XBrand)) { Text("تأیید پیش‌نمایش") } }
    )
}

@Composable
private fun QuestionEditorDialog(
    chapters: List<JSONObject>,
    initial: ExamQuestionDraft?,
    requireImage: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (ExamQuestionDraft) -> Unit,
) {
    var type by remember(initial) { mutableStateOf(initial?.type ?: "mcq") }
    var text by remember(initial) { mutableStateOf(initial?.text ?: "") }
    var score by remember(initial) { mutableStateOf(initial?.score?.let(::formatScore) ?: "1") }
    var chapterIndex by remember(initial, chapters) {
        mutableIntStateOf(chapters.indexOfFirst { it.optString("id") == initial?.chapterId }.coerceAtLeast(0))
    }
    val choices = remember(initial) { mutableStateListOf<String>().apply { addAll(initial?.choices?.takeIf { it.size == 4 } ?: listOf("", "", "", "")) } }
    var answerIndex by remember(initial) { mutableIntStateOf(initial?.answerIndex ?: -1) }
    var answerText by remember(initial) { mutableStateOf(initial?.answerText ?: "") }
    var boolAnswer by remember(initial) { mutableStateOf(initial?.boolAnswer ?: true) }
    var boolSelected by remember(initial) { mutableStateOf(initial?.type == "true_false" && initial.boolAnswerKnown) }
    var imageDataUrl by remember(initial) { mutableStateOf(initial?.imageDataUrl.orEmpty()) }
    var localError by remember { mutableStateOf("") }
    var imageLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            imageLoading = true; localError = ""
            try { imageDataUrl = withContext(Dispatchers.IO) { encodeQuestionImage(context, uri) } }
            catch (e: Throwable) { localError = e.message ?: "بارگذاری تصویر ناموفق بود." }
            finally { imageLoading = false }
        }
    }
    val types = listOf("mcq", "descriptive", "true_false", "fill_blank", "short_answer")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "افزودن سؤال" else "ویرایش سؤال", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    Text("نوع سؤال", fontWeight = FontWeight.Bold)
                    types.forEach { t -> FilterChip(selected = type == t, onClick = { type = t; localError = "" }, label = { Text(questionTypeLabel(t)) }) }
                    Spacer(Modifier.height(5.dp))
                    Text("فصل", fontWeight = FontWeight.Bold)
                    chapters.forEachIndexed { i, c ->
                        FilterChip(selected = i == chapterIndex, onClick = { chapterIndex = i }, label = { Text(c.optString("title", c.optString("name", "فصل ${i + 1}"))) })
                    }
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("متن سؤال") }, minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !imageLoading,
                            onClick = { (context as? MainActivity)?.suppressNextUnlock(); imagePicker.launch("image/*") }
                        ) { Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(4.dp)); Text(if (imageDataUrl.isBlank()) "افزودن تصویر" else "تغییر تصویر") }
                        if (imageDataUrl.isNotBlank()) TextButton(onClick = { imageDataUrl = "" }) { Text("حذف تصویر") }
                    }
                    if (imageLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (imageDataUrl.isNotBlank()) QuestionImagePreview(imageDataUrl)
                    Text("تصاویر JPG، PNG یا WebP تا ۹۰۰ کیلوبایت پذیرفته می‌شوند.", fontSize = 10.sp, color = XMuted)
                    OutlinedTextField(score, { score = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, Modifier.fillMaxWidth(), label = { Text("بارم سؤال") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    when (type) {
                        "mcq" -> {
                            Text("گزینه صحیح را با دایره کنار آن مشخص کنید.", fontSize = 11.sp, color = XMuted)
                            choices.indices.forEach { i ->
                            OutlinedTextField(
                                choices[i], { choices[i] = it }, Modifier.fillMaxWidth(), label = { Text("گزینه ${i + 1}") }, singleLine = true,
                                trailingIcon = { RadioButton(selected = answerIndex == i, onClick = { answerIndex = i }) }
                            )
                            }
                        }
                        "true_false" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = boolSelected && boolAnswer, onClick = { boolAnswer = true; boolSelected = true }, label = { Text("صحیح") })
                            FilterChip(selected = boolSelected && !boolAnswer, onClick = { boolAnswer = false; boolSelected = true }, label = { Text("غلط") })
                        }
                        "fill_blank" -> OutlinedTextField(answerText, { answerText = it }, Modifier.fillMaxWidth(), label = { Text("پاسخ صحیح جای خالی") }, singleLine = true)
                        "short_answer" -> OutlinedTextField(answerText, { answerText = it }, Modifier.fillMaxWidth(), label = { Text("پاسخ مرجع کوتاه") }, minLines = 2)
                        "descriptive" -> OutlinedTextField(answerText, { answerText = it }, Modifier.fillMaxWidth(), label = { Text("پاسخ مرجع / راهنمای تصحیح (اختیاری)") }, minLines = 3)
                    }
                    if (localError.isNotBlank()) Text(localError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(colors = ButtonDefaults.buttonColors(containerColor = XBrand), onClick = {
                val parsed = score.toDoubleOrNull()
                localError = when {
                    chapters.isEmpty() -> "فصل آزمون مشخص نیست."
                    imageLoading -> "بارگذاری تصویر هنوز کامل نشده است."
                    requireImage && imageDataUrl.isBlank() -> "برای این سؤال تصویری باید تصویر اضافه کنید."
                    text.trim().isEmpty() -> "متن سؤال را وارد کنید."
                    parsed == null || parsed <= 0.0 || parsed > 20.0 -> "بارم سؤال باید بیشتر از صفر و حداکثر ۲۰ باشد."
                    type == "mcq" && choices.any { it.trim().isEmpty() } -> "هر چهار گزینه را کامل کنید."
                    type == "mcq" && choices.map { it.trim() }.distinct().size < 4 -> "گزینه‌های تستی باید با هم متفاوت باشند."
                    type == "mcq" && answerIndex !in 0..3 -> "لطفاً پاسخ صحیح سؤال تستی را مشخص کنید."
                    type == "true_false" && !boolSelected -> "مشخص کنید جمله صحیح است یا غلط."
                    type == "fill_blank" && answerText.trim().isEmpty() -> "پاسخ صحیح جای خالی را وارد کنید."
                    type == "short_answer" && answerText.trim().isEmpty() -> "پاسخ مرجع کوتاه را وارد کنید."
                    else -> ""
                }
                if (localError.isBlank()) {
                    onSave(
                        ExamQuestionDraft(
                            id = initial?.id ?: "q_android_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                            type = type,
                            text = text.trim(),
                            score = parsed!!,
                            chapterId = chapters.getOrNull(chapterIndex)?.optString("id").orEmpty(),
                            choices = if (type == "mcq") choices.toList() else emptyList(),
                            answerIndex = if (type == "mcq") answerIndex else null,
                            answerText = answerText.trim(),
                            boolAnswer = boolAnswer,
                            boolAnswerKnown = if (type == "true_false") boolSelected else true,
                            sourceQuestionId = initial?.sourceQuestionId.orEmpty(),
                            sourceChapterId = initial?.sourceChapterId.orEmpty(),
                            explanation = initial?.explanation.orEmpty(),
                            visualType = if (imageDataUrl.isNotBlank()) "image" else "",
                            imageDataUrl = imageDataUrl,
                        )
                    )
                }
            }) { Text("ثبت سؤال") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

private fun formatScore(v: Double): String = if (kotlin.math.abs(v - v.toInt()) < .0001) v.toInt().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
