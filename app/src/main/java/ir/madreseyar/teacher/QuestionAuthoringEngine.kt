package ir.madreseyar.teacher

/** A platform-independent preview row. Invalid questions are shown with an explanation, not discarded. */
internal data class PastedQuestion(
    val text: String,
    val type: String,
    val choices: List<String> = emptyList(),
    val answerIndex: Int? = null,
    val answerText: String = "",
    val boolAnswer: Boolean = true,
    val score: Double = 1.0,
    val issue: String = "",
)

internal data class QuestionSuggestionMeta(
    val id: String,
    val chapterId: String,
    val type: String,
    val difficulty: String,
)

/** Balanced, deterministic suggestions from the *approved* bank, never AI-generated facts. */
internal fun suggestApprovedQuestionIds(
    available: List<QuestionSuggestionMeta>,
    count: Int,
    excludedIds: Set<String> = emptySet(),
): List<String> {
    val pool = available.filter { it.id.isNotBlank() && it.id !in excludedIds }.distinctBy { it.id }.toMutableList()
    val chapterCounts = mutableMapOf<String, Int>()
    val typeCounts = mutableMapOf<String, Int>()
    val difficultyCounts = mutableMapOf<String, Int>()
    val result = mutableListOf<String>()
    repeat(count.coerceIn(0, 40)) {
        if (pool.isEmpty()) return result
        val next = pool.minWithOrNull(compareBy<QuestionSuggestionMeta> {
            (chapterCounts[it.chapterId] ?: 0) * 6 +
                (typeCounts[it.type] ?: 0) * 2 +
                (difficultyCounts[it.difficulty] ?: 0)
        }.thenBy { available.indexOfFirst { original -> original.id == it.id } }) ?: return result
        pool.remove(next)
        result += next.id
        chapterCounts[next.chapterId] = (chapterCounts[next.chapterId] ?: 0) + 1
        typeCounts[next.type] = (typeCounts[next.type] ?: 0) + 1
        difficultyCounts[next.difficulty] = (difficultyCounts[next.difficulty] ?: 0) + 1
    }
    return result
}

private fun normalizePersianNumber(text: String): String = buildString {
    text.forEach { ch ->
        append(when (ch) {
            in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
            in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
            '٫', '،' -> '.'
            else -> ch
        })
    }
}

private val questionHeader = Regex("^(?:س[ؤو]ال|پرسش)(?:\\s*[0-9۰-۹٠-٩]+)?\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)
private val optionLine = Regex("^\\s*(الف|ب|ج|د|[AaBbCcDd]|[1-4۱-۴])\\s*[).:：\\-،]\\s*(.+)$")
private val answerLine = Regex("^(?:پاسخ(?:\\s+صحیح)?|جواب|کلید)\\s*[:：]\\s*(.*)$")
private val scoreLine = Regex("^(?:بارم|نمره)\\s*[:：]\\s*(.*)$")
private val typeLine = Regex("^نوع\\s*[:：]\\s*(.*)$")

private fun answerOptionIndex(answer: String, options: List<String>): Int? {
    val normalized = normalizePersianNumber(answer.trim()).lowercase()
    val key = normalized.removeSuffix(")").removeSuffix(".").trim()
    val idx = when (key) {
        "الف", "a", "1" -> 0
        "ب", "b", "2" -> 1
        "ج", "c", "3" -> 2
        "د", "d", "4" -> 3
        else -> options.indexOfFirst { it.trim().equals(answer.trim(), ignoreCase = true) }.takeIf { it >= 0 }
    }
    return idx?.takeIf { it in 0..3 }
}

private fun questionBlocks(input: String): List<String> {
    val lines = input.replace("\r\n", "\n").replace('\r', '\n').lines()
    val out = mutableListOf<String>()
    val current = mutableListOf<String>()
    fun flush() {
        val block = current.joinToString("\n").trim()
        if (block.isNotBlank()) out += block
        current.clear()
    }
    for (raw in lines) {
        val line = raw.trim()
        if (line.matches(Regex("-{3,}"))) { flush(); continue }
        // Two consecutive empty lines delimit questions; also support a new numbered question header.
        if (line.isNotEmpty() && questionHeader.matches(line) && current.any { it.isNotBlank() }) flush()
        if (line.isNotEmpty() && current.lastOrNull()?.isBlank() == true && current.dropLast(1).lastOrNull()?.isBlank() == true) flush()
        current += raw
    }
    flush()
    return out
}

/**
 * Accepts blocks with «سؤال: ...», four labelled choices and «پاسخ: ب».
 * Also accepts descriptive, short-answer, fill-blank and true/false rows.
 * Ambiguous MCQs are *never* auto-keyed to the first option.
 */
internal fun parsePastedQuestions(input: String): List<PastedQuestion> = questionBlocks(input).take(60).map { block ->
    val questionLines = mutableListOf<String>()
    val options = linkedMapOf<Int, String>()
    var answer = ""
    var score = 1.0
    var scoreIssue = ""
    var statedType = ""
    for (line0 in block.lines()) {
        val line = line0.trim()
        if (line.isBlank()) continue
        val header = questionHeader.matchEntire(line)
        val option = optionLine.matchEntire(line)
        val answerMatch = answerLine.matchEntire(line)
        val scoreMatch = scoreLine.matchEntire(line)
        val typeMatch = typeLine.matchEntire(line)
        when {
            header != null -> questionLines += header.groupValues[1]
            answerMatch != null -> answer = answerMatch.groupValues[1].trim()
            scoreMatch != null -> {
                val parsed = normalizePersianNumber(scoreMatch.groupValues[1]).toDoubleOrNull()
                if (parsed == null || !parsed.isFinite() || parsed <= 0.0 || parsed > 20.0) scoreIssue = "بارم باید عددی بین صفر تا ۲۰ باشد."
                else score = parsed
            }
            typeMatch != null -> statedType = typeMatch.groupValues[1].trim()
            option != null && questionLines.any { it.isNotBlank() } -> {
                val index = answerOptionIndex(option.groupValues[1], listOf("", "", "", ""))
                if (index != null) options[index] = option.groupValues[2].trim()
            }
            else -> questionLines += line
        }
    }
    val text = questionLines.joinToString("\n").trim()
    val type = when {
        statedType.contains("چهار") || statedType.contains("تست") || statedType.contains("تصویر") || options.isNotEmpty() -> "mcq"
        statedType.contains("صحیح") || statedType.contains("غلط") || statedType.contains("درست") -> "true_false"
        statedType.contains("جای") || text.contains("____") || text.contains("□□□□") -> "fill_blank"
        statedType.contains("تشریح") -> "descriptive"
        statedType.contains("کوتاه") -> "short_answer"
        answer.lowercase() in setOf("صحیح", "غلط", "درست", "نادرست", "true", "false") -> "true_false"
        answer.isNotBlank() -> "short_answer"
        else -> "descriptive"
    }
    val choices = if (type == "mcq") List(4) { options[it].orEmpty() } else emptyList()
    val correctIndex = if (type == "mcq") answerOptionIndex(answer, choices) else null
    val bool = when (answer.lowercase()) {
        "درست", "صحیح", "بله", "true" -> true
        "غلط", "نادرست", "خیر", "false" -> false
        else -> true
    }
    val issue = when {
        text.isBlank() -> "متن سؤال مشخص نیست."
        scoreIssue.isNotBlank() -> scoreIssue
        type == "mcq" && (options.size != 4 || choices.any { it.isBlank() }) -> "برای سؤال تستی باید چهار گزینه الف، ب، ج، د وارد شود."
        type == "mcq" && choices.map { it.trim() }.distinct().size != 4 -> "گزینه‌های تستی نباید تکراری باشند."
        type == "mcq" && correctIndex == null -> "کلید صحیح را بنویسید؛ مثال: پاسخ: ب"
        type == "true_false" && answer.lowercase() !in setOf("درست", "صحیح", "بله", "true", "غلط", "نادرست", "خیر", "false") -> "برای صحیح/غلط، پاسخ باید «صحیح» یا «غلط» باشد."
        type in setOf("short_answer", "fill_blank") && answer.isBlank() -> "پاسخ صحیح را وارد کنید."
        else -> ""
    }
    PastedQuestion(text, type, choices, correctIndex, answer, bool, score, issue)
}
