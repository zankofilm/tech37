package ir.madreseyar.teacher

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

private val EBrand = Color(0xFF0CA6A4)
private val EBrandDark = Color(0xFF075A63)
private val EBg = Color(0xFFF3F9FB)
private val EText = Color(0xFF12313A)
private val EMuted = Color(0xFF71838A)
private val ESuccess = Color(0xFF16A36A)

private fun JSONArray.objects(): List<JSONObject> = buildList { for (i in 0 until length()) optJSONObject(i)?.let(::add) }
private fun JSONObject.prettySummary(): String {
    val preferred = listOf("title","name","subject","className","status","description","body","date","createdAt")
    return preferred.mapNotNull { k -> opt(k)?.takeIf { it != JSONObject.NULL && it.toString().isNotBlank() }?.let { "$k: $it" } }.take(4).joinToString(" · ")
}

@Composable private fun ExtHeader(title: String, subtitle: String = "") {
    TeacherCompactHeader(title, subtitle)
}
@Composable private fun ExtCard(content: @Composable ColumnScope.() -> Unit) {
    TeacherPanel(content = content)
}
@Composable private fun LoadingOrError(loading:Boolean,error:String){ if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color=EBrand); if(error.isNotBlank()) Text(error,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error) }

@Composable
fun TeachingBooksScreen(workspace: JSONObject?, onOpenClasses: () -> Unit = {}) {
    val assignments=workspace?.optJSONArray("assignments")?.objects().orEmpty(); val classes=workspace?.optJSONArray("classes")?.objects().orEmpty(); val students=workspace?.optJSONArray("students")?.objects().orEmpty()
    val cn={id:String->classes.firstOrNull{it.optString("id")==id}?.optString("name",id)?:id}
    Column(Modifier.fillMaxSize().background(EBg)){ ExtHeader("کتاب‌ها و کلاس‌های من","تخصیص‌های واقعی تدریس از سرور"); LazyColumn(contentPadding=PaddingValues(16.dp)){ items(assignments){a-> val count=students.count{it.optString("classId")==a.optString("classId")}; ExtCard{Text(a.optString("subject","درس"),fontWeight=FontWeight.Black,fontSize=17.sp,color=EText);Text(cn(a.optString("classId")),color=EMuted);Text("$count دانش‌آموز · کتاب ${a.optString("bookId","—")}",color=EMuted,fontSize=12.sp); val fs=a.optJSONArray("features")?.let{arr->(0 until arr.length()).joinToString("، "){arr.optString(it)}}.orEmpty(); if(fs.isNotBlank()) Text("مجوزها: $fs",color=EBrand,fontSize=11.sp); OutlinedButton(onClick=onOpenClasses){Icon(Icons.Default.Groups,null);Spacer(Modifier.width(4.dp));Text("ورود به کلاس و دانش‌آموزان")}} }; if(assignments.isEmpty()) item{Text("تخصیص تدریسی ثبت نشده است.",Modifier.padding(24.dp),color=EMuted)} } }
}

@Composable
fun CurriculumScreenNative(onOpenQuestionBank: () -> Unit = {}, onOpenExamStudio: () -> Unit = {}) {
    var data by remember{ mutableStateOf<JSONObject?>(null)}; var loading by remember{mutableStateOf(true)}; var error by remember{mutableStateOf("")}
    LaunchedEffect(Unit){try{data=ApiClient.get("/api/staff/teacher-approved-questions/p67")}catch(e:Throwable){error=e.message?:"خطا"}finally{loading=false}}
    Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("آموزش و فصل‌ها","کتاب‌ها و فصل‌های مجاز برای تدریس");LoadingOrError(loading,error);LazyColumn(contentPadding=PaddingValues(16.dp)){items(data?.optJSONArray("books")?.objects().orEmpty()){b->ExtCard{Text(b.optString("title",b.optString("subject","کتاب")),fontWeight=FontWeight.Black,color=EText);Text("پایه ${b.optString("grade","—")} · ${b.optString("subject")}",color=EMuted);val ch=b.optJSONArray("chapters")?.objects().orEmpty(); if(ch.isNotEmpty()){Spacer(Modifier.height(8.dp));ch.forEachIndexed{i,c->Text("${i+1}. ${c.optString("title",c.optString("name","فصل"))}",fontSize=12.sp,color=EBrandDark)}}; Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick=onOpenQuestionBank){Text("سؤال‌های کتاب")};TextButton(onClick=onOpenExamStudio){Text("ساخت آزمون")}}}}}}
}

@Composable
fun QuestionBankNativeScreen(onOpenExamStudio: (String?) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var personalQuestions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var approvedQuestions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var books by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var source by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var gradeFilter by remember { mutableStateOf("") }
    var bookFilter by remember { mutableStateOf("") }
    var chapterFilter by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("") }
    var difficultyFilter by remember { mutableStateOf("") }
    var selectedQuestionId by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf("") }

    suspend fun reload() {
        loading = true
        error = ""
        val errors = mutableListOf<String>()
        try {
            val result = ApiClient.get("/api/staff/teacher-approved-questions/p67")
            approvedQuestions = result.optJSONArray("questions")?.objects().orEmpty()
            books = result.optJSONArray("books")?.objects().orEmpty()
        } catch (e: Throwable) {
            approvedQuestions = emptyList(); books = emptyList()
            errors.add("مخزن تأییدشده: ${e.message ?: "خطا"}")
        }
        try {
            personalQuestions = ApiClient.get("/api/p635/questions").optJSONArray("questions")?.objects().orEmpty()
            assignments = ApiClient.get("/api/p635/assignments").optJSONArray("assignments")?.objects().orEmpty()
        } catch (e: Throwable) {
            personalQuestions = emptyList(); assignments = emptyList()
            errors.add("بانک شخصی: ${e.message ?: "خطا"}")
        }
        error = errors.joinToString("\n")
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    val all = if (source == 0) approvedQuestions else personalQuestions
    val gradeOptions = books.map { it.optString("grade") }.filter { it.isNotBlank() }.distinct()
    val bookOptions = books.filter { gradeFilter.isBlank() || it.optString("grade") == gradeFilter }
    val chapterOptions = all.map { it.optString("chapter").ifBlank { it.optString("chapterTitle") } }.filter { it.isNotBlank() }.distinct()
    val typeOptions = all.map { it.optString("type") }.filter { it.isNotBlank() }.distinct()
    val difficultyOptions = all.map { it.optString("difficulty") }.filter { it.isNotBlank() }.distinct()
    val bookGradeMap = books.associate { it.optString("id") to it.optString("grade") }
    val selected = all.filter { q ->
        val grade = q.optString("grade").ifBlank { bookGradeMap[q.optString("bookId")].orEmpty() }
        val chapter = q.optString("chapter").ifBlank { q.optString("chapterTitle") }
        val textValues = listOf(q.optString("questionText"), q.optString("text"), q.optString("subject"), chapter, q.optString("difficulty"), q.optString("type")) +
            ((q.optJSONArray("choices") ?: q.optJSONArray("options"))?.let { arr -> List(arr.length()) { arr.optString(it) } }.orEmpty())
        (gradeFilter.isBlank() || grade == gradeFilter) &&
            (bookFilter.isBlank() || q.optString("bookId") == bookFilter) &&
            (chapterFilter.isBlank() || chapter == chapterFilter) &&
            (typeFilter.isBlank() || q.optString("type") == typeFilter) &&
            (difficultyFilter.isBlank() || q.optString("difficulty") == difficultyFilter) &&
            bankQuestionMatchesSearch(search, textValues)
    }
    val selectedQuestion = selected.firstOrNull { it.optString("id") == selectedQuestionId }
    val selectedScore = selectedQuestion?.optDouble("score", 1.0)?.takeIf { it.isFinite() && it > 0 } ?: 0.0

    Column(Modifier.fillMaxSize().background(TeacherPalette.Background)) {
        TeacherHeroHeader(
            title = "مخزن سؤال و آزمون‌ساز",
            subtitle = "جستجو، مدیریت سؤال‌ها و طراحی آزمون حرفه‌ای",
            onRefresh = { scope.launch { reload() } },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TeacherFilterChip("مخزن سؤال", source == 0, { source = 0; selectedQuestionId = ""; expandedId = "" }, Icons.Default.Storage)
            TeacherFilterChip("بانک شخصی", source == 1, { source = 1; selectedQuestionId = ""; expandedId = "" }, Icons.Default.FolderOpen)
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp),
            singleLine = true, placeholder = { Text("جستجوی سؤال، درس یا فصل...", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TeacherPalette.DeepTeal) },
            shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = TeacherPalette.Border, focusedBorderColor = TeacherPalette.Teal),
        )
        androidx.compose.foundation.lazy.LazyRow(
            Modifier.fillMaxWidth().padding(top = 7.dp), contentPadding = PaddingValues(horizontal = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                BankDropdownFilter("پایه", gradeFilter, listOf("" to "همه پایه‌ها") + gradeOptions.map { it to "پایه $it" }, Icons.Default.School) {
                    gradeFilter = it; bookFilter = ""
                }
            }
            item {
                BankDropdownFilter("کتاب", bookFilter, listOf("" to "همه کتاب‌ها") + bookOptions.map { it.optString("id") to it.optString("title", it.optString("subject", "کتاب")) }, Icons.Default.MenuBook) { bookFilter = it }
            }
            item {
                BankDropdownFilter("فصل", chapterFilter, listOf("" to "همه فصل‌ها") + chapterOptions.map { it to it }, Icons.Default.Layers) { chapterFilter = it }
            }
            item {
                BankDropdownFilter("نوع سؤال", typeFilter, listOf("" to "همه انواع") + typeOptions.map { it to bankQuestionTypeLabel(it) }, Icons.Default.List) { typeFilter = it }
            }
            item {
                BankDropdownFilter("سختی", difficultyFilter, listOf("" to "همه سطح‌ها") + difficultyOptions.map { it to bankDifficultyLabel(it) }, Icons.Default.FilterList) { difficultyFilter = it }
            }
        }
        TeacherPanel(Modifier.padding(horizontal = 13.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = TeacherPalette.Green, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (source == 0) "سؤال‌های تأییدشده" else "بانک سؤال شخصی", color = TeacherPalette.Text, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                    Text("${selected.size} سؤال مطابق فیلترهای انتخابی", color = TeacherPalette.Muted, fontSize = 10.sp)
                }
                TeacherTag("${all.size} سؤال", TeacherPalette.DeepTeal, Color(0xFFE5F5F3))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (source == 1) {
                    Button(onClick = { adding = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = TeacherPalette.Teal), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("سؤال شخصی جدید", fontSize = 10.sp)
                    }
                } else {
                    OutlinedButton(onClick = { selectedQuestionId = ""; expandedId = "" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.FilterList, null); Spacer(Modifier.width(4.dp)); Text("پاک‌کردن انتخاب", fontSize = 10.sp)
                    }
                }
                OutlinedButton(onClick = { scope.launch { reload() } }, enabled = !loading, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("تازه‌سازی", fontSize = 10.sp)
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = TeacherPalette.Teal)
        if (error.isNotBlank()) Text(error, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, fontSize = 10.sp)

        LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(selected.size) { index ->
                val q = selected[index]
                val key = q.optString("id").ifBlank { "question-$index" }
                val title = q.optString("questionText").ifBlank { q.optString("text", "سؤال بدون متن") }
                val isSelected = selectedQuestionId == key
                val expanded = expandedId == key
                val imageData = questionImageSource(q)
                val options = (q.optJSONArray("choices") ?: q.optJSONArray("options"))?.let { arr -> List(arr.length()) { arr.optString(it) } }.orEmpty()
                val rawAnswer = q.opt("answer") ?: q.opt("answerIndex") ?: q.opt("correctOptionIndex")
                val answerIndex = when (rawAnswer) {
                    is Number -> rawAnswer.toInt().takeIf { it in 0..3 } ?: -1
                    is String -> rawAnswer.toIntOrNull()?.takeIf { it in 0..3 } ?: listOf("الف", "ب", "ج", "د").indexOf(rawAnswer.trim())
                    else -> -1
                }
                Card(
                    Modifier.fillMaxWidth(), RoundedCornerShape(19.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) TeacherPalette.Teal else TeacherPalette.Border),
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeacherTag("سؤال ${index + 1}", TeacherPalette.Text, Color(0xFFF0F4F7))
                            Spacer(Modifier.width(5.dp))
                            TeacherTag(if (source == 0) "تأییدشده" else "شخصی", if (source == 0) TeacherPalette.Green else TeacherPalette.Blue, if (source == 0) TeacherPalette.PaleGreen else TeacherPalette.PaleBlue)
                            Spacer(Modifier.width(5.dp))
                            TeacherTag(bankDifficultyLabel(q.optString("difficulty")), if (q.optString("difficulty").lowercase() in setOf("hard", "difficult", "سخت")) TeacherPalette.Red else TeacherPalette.Blue)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { expandedId = if (expanded) "" else key }, modifier = Modifier.size(28.dp)) {
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "جزئیات سؤال", tint = TeacherPalette.Muted)
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(title, color = TeacherPalette.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = if (expanded) Int.MAX_VALUE else 3)
                                Spacer(Modifier.height(5.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    TeacherTag(bankQuestionTypeLabel(q.optString("type")), TeacherPalette.Blue, TeacherPalette.PaleBlue)
                                    q.optString("chapter").takeIf { it.isNotBlank() }?.let { TeacherTag(it, TeacherPalette.DeepTeal, Color(0xFFE8F7F5)) }
                                }
                            }
                            if (imageData.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.width(94.dp).height(78.dp).clip(RoundedCornerShape(11.dp)).background(TeacherPalette.PaleBlue), contentAlignment = Alignment.Center) {
                                    QuestionImagePreview(imageData, Modifier.fillMaxSize())
                                }
                            } else if (q.optString("type") in setOf("image", "diagram")) {
                                Spacer(Modifier.width(8.dp))
                                Surface(Modifier.size(78.dp), RoundedCornerShape(11.dp), TeacherPalette.PaleBlue) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, null, tint = TeacherPalette.Blue, modifier = Modifier.size(31.dp)) }
                                }
                            }
                        }
                        if (options.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            options.take(4).chunked(2).forEachIndexed { rowIndex, pair ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    pair.forEachIndexed { columnIndex, option ->
                                        val optionIndex = rowIndex * 2 + columnIndex
                                        val correct = optionIndex == answerIndex
                                        Surface(
                                            Modifier.weight(1f).padding(vertical = 2.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (correct) TeacherPalette.PaleGreen else Color(0xFFF7F9FC),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (correct) TeacherPalette.Green.copy(.55f) else TeacherPalette.Border),
                                        ) {
                                            Row(Modifier.padding(horizontal = 7.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text("${listOf("الف", "ب", "ج", "د").getOrElse(optionIndex) { "گزینه" }})", color = TeacherPalette.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.width(4.dp))
                                                Text(option, Modifier.weight(1f), color = TeacherPalette.Text, fontSize = 9.sp, maxLines = 1)
                                                if (correct) Icon(Icons.Default.CheckCircle, null, tint = TeacherPalette.Green, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        if (expanded) {
                            if (imageData.isNotBlank()) QuestionImagePreview(imageData, Modifier.fillMaxWidth().padding(top = 5.dp))
                            val answerText = q.optString("referenceAnswer")
                            if (answerText.isNotBlank()) Text("پاسخ مرجع: $answerText", color = TeacherPalette.Green, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                            val explanation = q.optString("explanation")
                            if (explanation.isNotBlank()) Text("توضیح: $explanation", color = TeacherPalette.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        HorizontalDivider(Modifier.padding(vertical = 7.dp), color = TeacherPalette.Border)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = TeacherPalette.Orange, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("${q.optDouble("score", 1.0)} نمره", color = TeacherPalette.Muted, fontSize = 9.sp)
                            Spacer(Modifier.weight(1f))
                            if (source == 0) {
                                TextButton(onClick = { onOpenExamStudio(q.optString("id")) }, enabled = q.optString("id").isNotBlank(), contentPadding = PaddingValues(horizontal = 7.dp)) {
                                    Icon(Icons.Default.Edit, null, tint = TeacherPalette.Blue, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(3.dp)); Text("ویرایش کپی", fontSize = 9.sp)
                                }
                            }
                            if (source == 0) {
                                Button(
                                    onClick = { selectedQuestionId = if (isSelected) "" else key },
                                    enabled = q.optString("id").isNotBlank(),
                                    shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) TeacherPalette.Green else TeacherPalette.Teal),
                                ) {
                                    Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Default.Add, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(3.dp)); Text(if (isSelected) "انتخاب شد" else "انتخاب", fontSize = 9.sp)
                                }
                            } else {
                                OutlinedButton(onClick = { expandedId = if (expanded) "" else key }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) {
                                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(3.dp)); Text("مشاهده", fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (selected.isEmpty() && !loading) item { Text("سؤالی با این فیلتر پیدا نشد.", Modifier.padding(24.dp), color = TeacherPalette.Muted) }
        }

        if (source == 0 && selectedQuestion != null) {
            TeacherPanel(Modifier.padding(horizontal = 13.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assignment, null, tint = TeacherPalette.Teal)
                    Spacer(Modifier.width(7.dp))
                    Text("آزمون در حال طراحی", color = TeacherPalette.Text, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeacherTag("۱ سؤال انتخاب‌شده", TeacherPalette.DeepTeal, Color(0xFFE5F5F3))
                    Spacer(Modifier.width(7.dp))
                    TeacherTag("مجموع نمره: $selectedScore", TeacherPalette.Purple, TeacherPalette.PalePurple)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { selectedQuestionId = "" }) { Text("پاک‌کردن", color = TeacherPalette.Red, fontSize = 10.sp) }
                }
                Button(
                    onClick = { onOpenExamStudio(selectedQuestionId) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TeacherPalette.Teal),
                ) { Text("ادامه طراحی آزمون"); Spacer(Modifier.width(7.dp)); Icon(Icons.Default.ArrowBack, null) }
            }
        }
    }
    if (adding) QuestionCreateDialog(assignments, onDismiss = { adding = false }, onSaved = { adding = false; scope.launch { reload() } })
}

@Composable
private fun BankDropdownFilter(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "همه $title"
    Box {
        Surface(
            Modifier.clickable { expanded = true }, shape = RoundedCornerShape(13.dp), color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, TeacherPalette.Border), shadowElevation = 1.dp,
        ) {
            Row(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = TeacherPalette.Teal, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(title, color = TeacherPalette.Muted, fontSize = 8.sp)
                    Text(selectedLabel, color = TeacherPalette.Text, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Icon(Icons.Default.ArrowDropDown, null, tint = TeacherPalette.Muted, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) -> DropdownMenuItem(text = { Text(label, fontSize = 11.sp) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

private fun bankQuestionTypeLabel(raw: String): String = when (raw.lowercase()) {
    "mcq", "multiple_choice", "multiplechoice" -> "چهارگزینه‌ای"
    "true_false", "truefalse" -> "صحیح / غلط"
    "fill_blank", "fillblank" -> "جای خالی"
    "short_answer", "short" -> "پاسخ کوتاه"
    "descriptive" -> "تشریحی"
    "image" -> "تصویری"
    else -> raw.ifBlank { "سؤال" }
}

private fun bankDifficultyLabel(raw: String): String = when (raw.lowercase()) {
    "easy", "آسان" -> "آسان"
    "medium", "متوسط" -> "متوسط"
    "hard", "difficult", "سخت" -> "سخت"
    else -> raw.ifBlank { "سطح نامشخص" }
}

@Composable
private fun QuestionCreateDialog(
    assignments: List<JSONObject>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var assignmentIndex by remember { mutableIntStateOf(0) }
    var chapter by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "", "", "") }
    var correct by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("سؤال چهارگزینه‌ای جدید") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (assignments.isEmpty()) item { Text("تخصیص تدریسی برای ثبت سؤال موجود نیست.", color = MaterialTheme.colorScheme.error) }
                else {
                    item { Text("انتخاب کلاس و درس", fontWeight = FontWeight.Black) }
                    items(assignments.size) { index ->
                        val assignment = assignments[index]
                        FilterChip(selected = assignmentIndex == index, onClick = { assignmentIndex = index },
                            label = { Text("${assignment.optString("className")} · ${assignment.optString("subject")}") })
                    }
                    item {
                        OutlinedTextField(chapter, { chapter = it }, Modifier.fillMaxWidth(), label = { Text("فصل") })
                        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("متن سؤال") }, minLines = 2)
                    }
                    items(4) { index ->
                        OutlinedTextField(options[index], { options[index] = it }, Modifier.fillMaxWidth(),
                            label = { Text("گزینه ${index + 1}") },
                            trailingIcon = { RadioButton(selected = correct == index, onClick = { correct = index }) })
                    }
                    item { Text("پاسخ صحیح را با دایره کنار یکی از گزینه‌ها انتخاب کنید.", fontSize = 11.sp, color = EMuted) }
                }
                if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && assignments.isNotEmpty(), onClick = {
                val issue = questionReviewIssue(QuestionReviewMeta(text = text.trim(), type = "mcq",
                    score = 1.0, choices = options.toList(), answerIndex = correct))
                if (issue.isNotBlank()) { error = issue; return@Button }
                val assignment = assignments.getOrNull(assignmentIndex) ?: return@Button
                scope.launch {
                    busy = true; error = ""
                    try {
                        ApiClient.post("/api/p635/questions", JSONObject()
                            .put("classId", assignment.optString("classId"))
                            .put("subject", assignment.optString("subject"))
                            .put("chapter", chapter.trim())
                            .put("text", text.trim())
                            .put("options", JSONArray(options.map { it.trim() }))
                            .put("correctIndex", correct))
                        onSaved()
                    } catch (e: Throwable) { error = e.message ?: "ثبت سؤال ناموفق بود." }
                    finally { busy = false }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = EBrand)) {
                Text(if (busy) "در حال ثبت…" else "ثبت سؤال")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun OfficialExamCreateScreen(workspace:JSONObject?) {
    val scope=rememberCoroutineScope();var books by remember{mutableStateOf<List<JSONObject>>(emptyList())};var error by remember{mutableStateOf("")};var message by remember{mutableStateOf("")};var title by remember{mutableStateOf("")};var question by remember{mutableStateOf("")};val choices=remember{mutableStateListOf("","","","")};var correct by remember{mutableIntStateOf(0)};var assignmentIndex by remember{mutableIntStateOf(0)};var bookIndex by remember{mutableIntStateOf(0)};var chapterIndex by remember{mutableIntStateOf(0)};var busy by remember{mutableStateOf(false)};val assignments=workspace?.optJSONArray("assignments")?.objects().orEmpty()
    LaunchedEffect(Unit){try{books=ApiClient.get("/api/staff/teacher-approved-questions/p67").optJSONArray("books")?.objects().orEmpty()}catch(e:Throwable){error=e.message?:"خطا"}}
    val a=assignments.getOrNull(assignmentIndex); val subject=a?.optString("subject").orEmpty(); val compatible=books.filter{it.optString("subject")==subject}; val b=compatible.getOrNull(bookIndex); val chapters=b?.optJSONArray("chapters")?.objects().orEmpty(); val ch=chapters.getOrNull(chapterIndex)
    Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("ساخت آزمون","ثبت پیش‌نویس رسمی روی سرور");LazyColumn(contentPadding=PaddingValues(16.dp)){item{ExtCard{Text("کلاس و درس",fontWeight=FontWeight.Black);assignments.forEachIndexed{i,x->FilterChip(selected=i==assignmentIndex,onClick={assignmentIndex=i;bookIndex=0;chapterIndex=0},label={Text("${x.optString("subject")} · ${x.optString("classId")}")})};Spacer(Modifier.height(8.dp));Text("کتاب",fontWeight=FontWeight.Bold);compatible.forEachIndexed{i,x->FilterChip(selected=i==bookIndex,onClick={bookIndex=i;chapterIndex=0},label={Text(x.optString("title",x.optString("subject")))})};Text("فصل",fontWeight=FontWeight.Bold);chapters.forEachIndexed{i,x->FilterChip(selected=i==chapterIndex,onClick={chapterIndex=i},label={Text(x.optString("title",x.optString("name","فصل")))})};OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("عنوان آزمون")});OutlinedTextField(question,{question=it},Modifier.fillMaxWidth(),label={Text("متن سؤال")});choices.indices.forEach{i->OutlinedTextField(choices[i],{choices[i]=it},Modifier.fillMaxWidth(),label={Text("گزینه ${i+1}")},trailingIcon={RadioButton(selected=correct==i,onClick={correct=i})})};if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);if(message.isNotBlank())Text(message,color=ESuccess);Button(enabled=!busy&&a!=null&&b!=null&&ch!=null&&title.length>=3&&question.isNotBlank()&&choices.all{it.isNotBlank()},onClick={scope.launch{busy=true;error="";message="";try{val id="ex_android_"+UUID.randomUUID().toString().replace("-","").take(14);val qid="q_android_"+UUID.randomUUID().toString().replace("-","").take(14);val exam=JSONObject().put("id",id).put("title",title).put("classId",a!!.optString("classId")).put("bookId",b!!.optString("id")).put("subject",subject).put("duration",20).put("examCategory","ongoing").put("academicPeriod","semester1").put("selectedChapterIds",JSONArray().put(ch!!.optString("id"))).put("status","draft").put("questions",JSONArray().put(JSONObject().put("id",qid).put("type","mcq").put("text",question).put("choices",JSONArray(choices)).put("answer",correct).put("score",20).put("chapterId",ch!!.optString("id"))));ApiClient.post("/api/staff/teacher-exams/p673",JSONObject().put("exam",exam));message="آزمون به‌صورت پیش‌نویس ثبت شد ✓";title="";question="";for(i in choices.indices)choices[i]=""}catch(e:Throwable){error=e.message?:"خطا"}finally{busy=false}}},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Text(if(busy)"در حال ثبت…" else "ذخیره پیش‌نویس")}}}}}
}

@Composable
fun GradingCenterNativeScreen(workspace: JSONObject?) {
    val exams = workspace?.optJSONArray("exams")?.objects().orEmpty()
    val submissions = workspace?.optJSONArray("submissions")?.objects().orEmpty()
    val students = workspace?.optJSONArray("students")?.objects().orEmpty()
    val scope = rememberCoroutineScope()
    var statusById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Column(Modifier.fillMaxSize().background(EBg)) {
        ExtHeader("تصحیح و نمرات", "برگه‌های تحویلی آزمون‌ها")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(submissions) { submission ->
                val exam = exams.firstOrNull { it.optString("id") == submission.optString("examId") }
                val student = students.firstOrNull { it.optString("id") == submission.optString("studentId") }
                val submissionId = submission.optString("id")
                val status = statusById[submissionId].orEmpty()

                ExtCard {
                    Text(exam?.optString("title", "آزمون") ?: "آزمون", fontWeight = FontWeight.Black)
                    Text(
                        "${student?.optString("firstName").orEmpty()} ${student?.optString("lastName").orEmpty()}",
                        color = EMuted
                    )
                    Text(submission.optString("submittedAt"), fontSize = 11.sp, color = EMuted)
                    OutlinedButton(onClick = {
                        scope.launch {
                            val next = try {
                                val encoded = URLEncoder.encode(submissionId, "UTF-8")
                                val detail = ApiClient.get("/api/staff/exam-grading/p6722?submissionId=$encoded")
                                if (detail.optBoolean("finalized")) "نهایی شده" else "آماده تصحیح"
                            } catch (e: Throwable) {
                                e.message ?: "خطا"
                            }
                            statusById = statusById + (submissionId to next)
                        }
                    }) {
                        Text("بررسی وضعیت برگه")
                    }
                    if (status.isNotBlank()) {
                        Text(status, color = if (status.contains("نهایی")) ESuccess else EBrand)
                    }
                }
            }
            if (submissions.isEmpty()) {
                item {
                    Text(
                        "برگه تشریحی تحویل‌شده‌ای وجود ندارد.",
                        Modifier.padding(24.dp),
                        color = EMuted
                    )
                }
            }
        }
    }
}

@Composable
fun ClassroomNativeScreen(workspace:JSONObject?) { val classes=workspace?.optJSONArray("classes")?.objects().orEmpty();val students=workspace?.optJSONArray("students")?.objects().orEmpty();Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("کلاس","فهرست کلاس‌ها و دانش‌آموزان");LazyColumn(contentPadding=PaddingValues(16.dp)){items(classes){c->val ss=students.filter{it.optString("classId")==c.optString("id")};ExtCard{Text(c.optString("name","کلاس"),fontWeight=FontWeight.Black,fontSize=17.sp);Text("${ss.size} دانش‌آموز",color=EBrand);ss.take(12).forEach{Text("• ${it.optString("firstName")} ${it.optString("lastName")}",fontSize=12.sp,color=EText)};if(ss.size>12)Text("و ${ss.size-12} نفر دیگر…",color=EMuted,fontSize=11.sp)}}}}}

@Composable
private fun AssignedClassDropdown(
    workspace: JSONObject?,
    selectedClassId: String,
    onSelected: (String) -> Unit
) {
    val assignments = workspace?.optJSONArray("assignments")?.objects().orEmpty()
    val allClasses = workspace?.optJSONArray("classes")?.objects().orEmpty()
    val assignedIds = assignments.map { it.optString("classId") }.filter { it.isNotBlank() }.toSet()
    val classes = allClasses.filter { it.optString("id") in assignedIds }
    val selected = classes.firstOrNull { it.optString("id") == selectedClassId } ?: classes.firstOrNull()
    var expanded by remember { mutableStateOf(false) }

    fun classLabel(c: JSONObject): String {
        val name = c.optString("name", "کلاس")
        val grade = c.optString("grade").trim()
        return if (grade.isNotBlank() && !name.contains("پایه")) "$name · پایه $grade" else name
    }

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.School, null)
            Spacer(Modifier.width(8.dp))
            Text(selected?.let(::classLabel) ?: "کلاس و پایه را انتخاب کنید", Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(.86f)
        ) {
            classes.forEach { c ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(classLabel(c), fontWeight = FontWeight.Bold)
                            val classAssignments = assignments.filter { it.optString("classId") == c.optString("id") }
                            val subjects = classAssignments.map { it.optString("subject") }.filter { it.isNotBlank() }.distinct()
                            if (subjects.isNotEmpty()) Text(subjects.joinToString("، "), fontSize = 11.sp, color = EMuted)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(c.optString("id"))
                    }
                )
            }
        }
    }
}

private fun assignedClassIds(workspace: JSONObject?): List<String> =
    workspace?.optJSONArray("assignments")?.objects().orEmpty()
        .map { it.optString("classId") }
        .filter { it.isNotBlank() }
        .distinct()

private fun classMeta(workspace: JSONObject?, classId: String): JSONObject? =
    workspace?.optJSONArray("classes")?.objects().orEmpty().firstOrNull { it.optString("id") == classId }

private fun classDisplay(workspace: JSONObject?, classId: String): String {
    val c = classMeta(workspace, classId) ?: return "کلاس"
    val name = c.optString("name", "کلاس")
    val grade = c.optString("grade").trim()
    return if (grade.isNotBlank() && !name.contains("پایه")) "$name · پایه $grade" else name
}

@Composable
private fun StudentModuleHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    TeacherCompactHeader(title, subtitle) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
    }
}

@Composable
private fun ReportMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier, RoundedCornerShape(16.dp), CardDefaults.cardColors(tint.copy(alpha = .08f)), CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(34.dp), RoundedCornerShape(11.dp), tint.copy(alpha = .12f)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp)) }
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(value, color = EText, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(label, color = EMuted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun ContinuousReportsNativeScreen(workspace: JSONObject?, dashboard: JSONObject? = null) {
    val allStudents = workspace?.optJSONArray("students")?.objects().orEmpty()
    val assignments = workspace?.optJSONArray("assignments")?.objects().orEmpty()
    val exams = workspace?.optJSONArray("exams")?.objects().orEmpty()
    val assignedIds = assignedClassIds(workspace)
    val currentRecords = dashboard?.optJSONObject("currentClass")?.optJSONObject("attendance")?.optJSONArray("records")?.objects().orEmpty()
    val presentCount = currentRecords.count { it.optString("status") == "present" }
    val absentCount = currentRecords.count { it.optString("status") == "absent" }
    val lateCount = currentRecords.count { it.optString("status") in setOf("late", "tardy") }
    val attendancePct = if (currentRecords.isNotEmpty()) presentCount * 100 / currentRecords.size else null
    val assignmentCount = assignments.size
    val examCount = exams.size
    val homeworkCount = workspace?.optJSONArray("homework")?.length()
        ?: dashboard?.optInt("homeworkCount", -1)?.takeIf { it >= 0 }
    val averageScore = listOf(workspace?.optJSONObject("summary"), dashboard?.optJSONObject("summary"), dashboard)
        .filterNotNull().firstNotNullOfOrNull { obj ->
            listOf("averageScore", "average", "meanScore").firstNotNullOfOrNull { key ->
                val raw = obj.opt(key)
                when (raw) {
                    is Number -> raw.toDouble().takeIf { it.isFinite() }
                    is String -> raw.toDoubleOrNull()?.takeIf { it.isFinite() }
                    else -> null
                }
            }
        }

    var selectedClassId by remember(workspace) { mutableStateOf(assignedIds.firstOrNull().orEmpty()) }
    var selectedStudent by remember { mutableStateOf<JSONObject?>(null) }
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var studentSearch by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val teacher = dashboard?.optJSONObject("teacher")
    val teacherName = listOf(teacher?.optString("firstName").orEmpty(), teacher?.optString("lastName").orEmpty()).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "استاد گرامی" }

    LaunchedEffect(workspace, assignedIds.joinToString("|")) {
        if (selectedClassId !in assignedIds) selectedClassId = assignedIds.firstOrNull().orEmpty()
    }

    BackHandler(enabled = selectedStudent != null) { selectedStudent = null; report = null; error = "" }
    if (selectedStudent != null) {
        val student = selectedStudent!!
        Column(Modifier.fillMaxSize().background(TeacherPalette.Background)) {
            TeacherHeroHeader(
                title = "گزارش دانش‌آموز",
                subtitle = classDisplay(workspace, student.optString("classId")),
                greeting = "${student.optString("firstName")} ${student.optString("lastName")}".trim(),
                greetingSubtitle = "نتایج منتشرشده و عملکرد آموزشی",
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = TeacherPalette.Teal)
            if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            LazyColumn(contentPadding = PaddingValues(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                val subjects = report?.optJSONArray("subjects")?.objects().orEmpty()
                if (!loading && error.isBlank() && subjects.isEmpty()) {
                    item { TeacherPanel { Text("هنوز نتیجه منتشرشده‌ای برای این دانش‌آموز وجود ندارد.", color = TeacherPalette.Muted, fontSize = 11.sp) } }
                }
                items(subjects) { subject ->
                    TeacherPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(subject.optString("subject", "درس"), Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 15.sp, color = TeacherPalette.Text)
                            TeacherTag("${subject.optString("average", "—")}", TeacherPalette.DeepTeal, Color(0xFFE5F5F3))
                        }
                        Text("میانگین ثبت‌شده", color = TeacherPalette.Muted, fontSize = 9.sp)
                        val rows = subject.optJSONArray("exams")?.objects().orEmpty()
                        Text("${rows.size} آزمون", color = TeacherPalette.Muted, fontSize = 10.sp)
                        rows.take(8).forEach { exam ->
                            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = TeacherPalette.Border)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(exam.optString("title", "آزمون"), Modifier.weight(1f), color = TeacherPalette.Text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                Text("${exam.optString("score20", "—")} از ۲۰", color = TeacherPalette.DeepTeal, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val students = allStudents.filter { it.optString("classId") == selectedClassId }
    val filteredStudents = students.filter {
        val name = "${it.optString("firstName")} ${it.optString("lastName")}".trim()
        name.contains(studentSearch.trim(), true) || it.optString("studentCode").contains(studentSearch.trim(), true)
    }
    val subjectCounts = assignments.groupingBy { it.optString("subject").ifBlank { "درس نامشخص" } }.eachCount().entries.sortedByDescending { it.value }.take(4)
    val totalSubjectAssignments = assignments.size.coerceAtLeast(1)
    val activeExamCount = exams.count { it.optString("status").lowercase() in setOf("active", "published", "running", "open") }
    val notifCount = dashboard?.optJSONArray("notifications")?.length() ?: 0

    LazyColumn(
        Modifier.fillMaxSize().background(TeacherPalette.Background),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TeacherHeroHeader(
                title = "گزارش‌ها و عملکرد",
                subtitle = "تحلیل جامع پیشرفت، حضور و وضعیت کلاس‌ها",
                greeting = "سلام $teacherName عزیز",
                greetingSubtitle = "نمای کلی عملکرد آموزشی بر اساس داده‌های ثبت‌شده",
                notificationCount = notifCount,
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TeacherMetricTile("میانگین نمرات", averageScore?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "—", "از داده‌های ثبت‌شده", Icons.Default.Layers, TeacherPalette.Orange, TeacherPalette.PaleOrange, Modifier.weight(1f))
                TeacherMetricTile("درصد حضور", attendancePct?.let { "$it٪" } ?: "—", if (currentRecords.isEmpty()) "داده زنگ جاری" else "$presentCount حاضر", Icons.Default.EventAvailable, TeacherPalette.Green, TeacherPalette.PaleGreen, Modifier.weight(1f))
                TeacherMetricTile("تکالیف", homeworkCount?.toString() ?: "—", "ثبت‌شده در سامانه", Icons.Default.Assignment, TeacherPalette.Purple, TeacherPalette.PalePurple, Modifier.weight(1f))
                TeacherMetricTile("آزمون‌ها", examCount.toString(), if (activeExamCount > 0) "$activeExamCount فعال" else "ثبت‌شده", Icons.Default.AssignmentTurnedIn, TeacherPalette.Blue, TeacherPalette.PaleBlue, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeacherPanel(Modifier.weight(1f)) {
                    TeacherSectionTitle("وضعیت حضور کلاس", icon = Icons.Default.Groups)
                    Spacer(Modifier.height(8.dp))
                    if (currentRecords.isNotEmpty()) {
                        TeacherProgressLine("حاضر", presentCount * 100 / currentRecords.size, TeacherPalette.Green, "%")
                        TeacherProgressLine("غایب", absentCount * 100 / currentRecords.size, TeacherPalette.Red, "%")
                        TeacherProgressLine("تأخیر", lateCount * 100 / currentRecords.size, TeacherPalette.Orange, "%")
                        Text("مبنای محاسبه: ${currentRecords.size} رکورد زنگ جاری", color = TeacherPalette.Muted, fontSize = 8.sp)
                    } else {
                        Text("هنوز داده حضور و غیاب زنگ جاری از سرور دریافت نشده است.", color = TeacherPalette.Muted, fontSize = 10.sp)
                    }
                }
                TeacherPanel(Modifier.weight(1f)) {
                    TeacherSectionTitle("تخصیص‌های تدریس", icon = Icons.Default.Insights)
                    Spacer(Modifier.height(8.dp))
                    if (subjectCounts.isNotEmpty()) {
                        subjectCounts.forEach { entry ->
                            val share = entry.value * 100 / totalSubjectAssignments
                            TeacherProgressLine(entry.key, share, TeacherPalette.Purple, "%")
                        }
                    } else {
                        Text("تخصیص درسی برای نمایش نمودار ثبت نشده است.", color = TeacherPalette.Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        item {
            TeacherPanel(Modifier.padding(horizontal = 13.dp)) {
                TeacherSectionTitle("گزارش تفصیلی کلاس", icon = Icons.Default.Insights)
                Spacer(Modifier.height(7.dp))
                AssignedClassDropdown(workspace, selectedClassId) { selectedClassId = it }
                Spacer(Modifier.height(5.dp))
                Text("${students.size} دانش‌آموز در ${classDisplay(workspace, selectedClassId)} · انتخاب دانش‌آموز برای مشاهده نمرات منتشرشده", color = TeacherPalette.Muted, fontSize = 9.sp)
                OutlinedTextField(
                    value = studentSearch, onValueChange = { studentSearch = it }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    singleLine = true, placeholder = { Text("جستجوی دانش‌آموز...", fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(17.dp)) },
                    shape = RoundedCornerShape(13.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = TeacherPalette.Border, focusedBorderColor = TeacherPalette.Teal),
                )
            }
        }
        items(filteredStudents, key = { it.optString("id").ifBlank { it.toString() } }) { s ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp), RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TeacherPalette.Border),
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val name = "${s.optString("firstName")} ${s.optString("lastName")}".trim()
                    TeacherInitialAvatar(name, Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, color = TeacherPalette.Text, fontSize = 11.sp)
                        Text("${classDisplay(workspace, s.optString("classId"))} · کد ${s.optString("studentCode", "—")}", color = TeacherPalette.Muted, fontSize = 9.sp)
                    }
                    Button(
                        onClick = {
                            selectedStudent = s; report = null; loading = true; error = ""
                            scope.launch {
                                try {
                                    report = ApiClient.get("/api/teacher/continuous-p11?studentId=${URLEncoder.encode(s.optString("id"), "UTF-8")}")
                                } catch (e: Throwable) { error = e.message ?: "خطا در دریافت گزارش" }
                                finally { loading = false }
                            }
                        },
                        shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TeacherPalette.Teal),
                    ) { Icon(Icons.Default.Insights, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("گزارش", fontSize = 10.sp) }
                }
            }
        }
        if (students.isEmpty()) item { TeacherPanel(Modifier.padding(horizontal = 13.dp)) { Text("برای این کلاس دانش‌آموزی پیدا نشد.", color = TeacherPalette.Muted, fontSize = 11.sp) } }
    }
}

@Composable
fun HomeworkNativeScreen(workspace:JSONObject?) {val scope=rememberCoroutineScope();var data by remember{mutableStateOf<JSONObject?>(null)};var error by remember{mutableStateOf("")};var add by remember{mutableStateOf(false)};suspend fun reload(){try{data=ApiClient.get("/api/staff/learning-planner")}catch(e:Throwable){error=e.message?:"خطا"}};LaunchedEffect(Unit){reload()};Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("مرکز تکالیف","تکلیف‌های منتشرشده و پیش‌نویس‌ها");Row(Modifier.padding(16.dp)){Button(onClick={add=true},colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Icon(Icons.Default.Add,null);Text("تکلیف جدید")}};LazyColumn(contentPadding=PaddingValues(horizontal=16.dp)){items(data?.optJSONArray("homework")?.objects().orEmpty()){h->ExtCard{Text(h.optString("title","تکلیف"),fontWeight=FontWeight.Black);Text("${h.optString("subject")} · ${h.optString("className",h.optString("classId"))}",color=EMuted);Text("مهلت: ${h.optString("dueAtJalali",h.optString("dueAt","—"))}",fontSize=11.sp,color=EMuted)}}};if(add) HomeworkCreateDialog(workspace,onDismiss={add=false},onSaved={add=false;scope.launch{reload()}})}
}
@Composable private fun HomeworkCreateDialog(workspace:JSONObject?,onDismiss:()->Unit,onSaved:()->Unit){val assignments=workspace?.optJSONArray("assignments")?.objects().orEmpty();var idx by remember{mutableIntStateOf(0)};var title by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var due by remember{mutableStateOf("")};var publish by remember{mutableStateOf(true)};var err by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=onDismiss,title={Text("تکلیف جدید")},text={LazyColumn{item{assignments.forEachIndexed{i,a->FilterChip(selected=i==idx,onClick={idx=i},label={Text(a.optString("subject"))})};OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("عنوان")});OutlinedTextField(desc,{desc=it},Modifier.fillMaxWidth(),label={Text("توضیحات")},minLines=3);OutlinedTextField(due,{due=it},Modifier.fillMaxWidth(),label={Text("تاریخ میلادی مهلت (YYYY-MM-DD)")},placeholder={Text("2026-11-10")});Row(verticalAlignment=Alignment.CenterVertically){Switch(publish,{publish=it});Text("انتشار برای دانش‌آموزان")};if(err.isNotBlank())Text(err,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(enabled=!busy&&assignments.isNotEmpty(),onClick={val a=assignments.getOrNull(idx)?:return@Button;scope.launch{busy=true;try{ApiClient.post("/api/staff/homework/p652/save",JSONObject().put("classId",a.optString("classId")).put("subject",a.optString("subject")).put("title",title).put("description",desc).put("totalScore",20).put("publicationState",if(publish)"published" else "draft").put("recipientMode","all").put("studentIds",JSONArray()).put("dueAt",due).put("dueTime",if(due.isBlank())"" else "23:59").put("academicPeriod","semester2").put("attachments",JSONArray()));onSaved()}catch(e:Throwable){err=e.message?:"خطا"}finally{busy=false}}}){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})}

@Composable
fun AttendanceDisciplineScreenNative(){ GenericModuleScreen("حضور و انضباط","/api/staff/attendance-discipline",listOf("students","recentBehavior"),"غیبت، تأخیر و پرونده‌های انضباطی") }
@Composable
fun ActionCenterScreenNative(){ GenericModuleScreen("مرکز اقدام","/api/staff/action-center",listOf("tasks","cases","signals"),"کارها، پرونده‌های پیگیری و هشدارها") }
@Composable
fun CommunicationsCenterScreenNative(){ GenericModuleScreen("ارتباطات و پیام‌ها","/api/staff/communications-center",listOf("inbox","announcements"),"صندوق پیام و اطلاعیه‌های مدرسه") }

@Composable
fun LearningPlannerScreenNative(workspace:JSONObject?){val scope=rememberCoroutineScope();var data by remember{mutableStateOf<JSONObject?>(null)};var add by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};suspend fun load(){try{data=ApiClient.get("/api/staff/learning-planner")}catch(e:Throwable){error=e.message?:"خطا"}};LaunchedEffect(Unit){load()};Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("برنامه آموزشی","تقویم آموزشی، رویدادها و تعارض‌ها");Row(Modifier.padding(16.dp)){Button(onClick={add=true},colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Icon(Icons.Default.Event,null);Text("رویداد جدید")}};LazyColumn(contentPadding=PaddingValues(horizontal=16.dp)){item{val s=data?.optJSONObject("summary");if(s!=null)ExtCard{Text("${s.optInt("scheduleEntries")} برنامه · ${s.optInt("homework")} تکلیف · ${s.optInt("exams")} آزمون",fontWeight=FontWeight.Black);Text("${s.optInt("events")} رویداد · ${s.optInt("conflicts")} تعارض",color=EMuted)}};items(data?.optJSONArray("upcoming")?.objects().orEmpty()){x->ExtCard{Text(x.optString("title",x.optString("subject","رویداد")),fontWeight=FontWeight.Black);Text(x.prettySummary(),color=EMuted,fontSize=11.sp)}};if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}};if(add)PlannerEventDialog(workspace,onDismiss={add=false},onSaved={add=false;scope.launch{load()}})}}
@Composable private fun PlannerEventDialog(workspace:JSONObject?,onDismiss:()->Unit,onSaved:()->Unit){val classes=workspace?.optJSONArray("classes")?.objects().orEmpty();var idx by remember{mutableIntStateOf(0)};var title by remember{mutableStateOf("")};var start by remember{mutableStateOf("")};var end by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=onDismiss,title={Text("رویداد آموزشی")},text={Column{classes.forEachIndexed{i,c->FilterChip(selected=i==idx,onClick={idx=i},label={Text(c.optString("name"))})};OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("عنوان")});OutlinedTextField(start,{start=it},Modifier.fillMaxWidth(),label={Text("شروع YYYY-MM-DD")});OutlinedTextField(end,{end=it},Modifier.fillMaxWidth(),label={Text("پایان YYYY-MM-DD")});OutlinedTextField(desc,{desc=it},Modifier.fillMaxWidth(),label={Text("توضیح")});if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}},confirmButton={Button(onClick={scope.launch{try{ApiClient.post("/api/staff/learning-planner/event",JSONObject().put("classId",classes.getOrNull(idx)?.optString("id").orEmpty()).put("title",title).put("startDate",start).put("endDate",end).put("description",desc));onSaved()}catch(e:Throwable){error=e.message?:"خطا"}}}){Text("ثبت")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})}

@Composable
fun GuardianMeetingsNativeScreen(workspace:JSONObject?){val scope=rememberCoroutineScope();var data by remember{mutableStateOf<JSONObject?>(null)};var error by remember{mutableStateOf("")};var add by remember{mutableStateOf(false)};suspend fun load(){try{data=ApiClient.get("/api/staff/guardian-meeting-invitations")}catch(e:Throwable){error=e.message?:"خطا"}};LaunchedEffect(Unit){load()};Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("ملاقات اولیا","دعوت‌ها و پاسخ خانواده‌ها");Row(Modifier.padding(16.dp)){Button(onClick={add=true},colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Icon(Icons.Default.GroupAdd,null);Text("دعوت جدید")}};LazyColumn(contentPadding=PaddingValues(horizontal=16.dp)){items(data?.optJSONArray("meetings")?.objects().orEmpty()){m->ExtCard{Text(m.optString("studentName","دانش‌آموز"),fontWeight=FontWeight.Black);Text("${m.optString("meetingDate")} · ${m.optString("meetingTime")} · ${m.optString("location")}",color=EMuted);Text(m.optString("description"),fontSize=12.sp);Text(m.optString("status","pending_guardian"),color=EBrand,fontSize=11.sp)}};if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}};if(add)MeetingCreateDialog(workspace,onDismiss={add=false},onSaved={add=false;scope.launch{load()}})}}
@Composable private fun MeetingCreateDialog(workspace:JSONObject?,onDismiss:()->Unit,onSaved:()->Unit){val students=workspace?.optJSONArray("students")?.objects().orEmpty();var idx by remember{mutableIntStateOf(0)};var date by remember{mutableStateOf("")};var time by remember{mutableStateOf("")};var location by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=onDismiss,title={Text("دعوت ملاقات")},text={LazyColumn{item{students.take(50).forEachIndexed{i,s->FilterChip(selected=i==idx,onClick={idx=i},label={Text("${s.optString("firstName")} ${s.optString("lastName")}")})};OutlinedTextField(date,{date=it},Modifier.fillMaxWidth(),label={Text("تاریخ شمسی YYYY/MM/DD")});OutlinedTextField(time,{time=it},Modifier.fillMaxWidth(),label={Text("ساعت HH:MM")});OutlinedTextField(location,{location=it},Modifier.fillMaxWidth(),label={Text("محل ملاقات")});OutlinedTextField(desc,{desc=it},Modifier.fillMaxWidth(),label={Text("علت/توضیح")});if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(onClick={val s=students.getOrNull(idx)?:return@Button;scope.launch{try{ApiClient.post("/api/staff/guardian-meeting-invitations",JSONObject().put("studentId",s.optString("id")).put("meetingDate",date).put("meetingTime",time).put("location",location).put("description",desc).put("clientRequestId","android_"+UUID.randomUUID().toString().replace("-","").take(20)));onSaved()}catch(e:Throwable){error=e.message?:"خطا"}}}){Text("ارسال دعوت")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})}

@Composable
fun StudentAnalysisNativeScreen(workspace: JSONObject?) {
    val allStudents = workspace?.optJSONArray("students")?.objects().orEmpty()
    val assignedIds = assignedClassIds(workspace)
    var selectedClassId by remember(workspace) { mutableStateOf(assignedIds.firstOrNull().orEmpty()) }
    var selectedStudent by remember { mutableStateOf<JSONObject?>(null) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(workspace, assignedIds.joinToString("|")) {
        if (selectedClassId !in assignedIds) selectedClassId = assignedIds.firstOrNull().orEmpty()
    }

    BackHandler(enabled = selectedStudent != null) { selectedStudent = null }
    if (selectedStudent != null) {
        val student = selectedStudent!!
        val analysis = data?.optJSONObject("analysis") ?: data
        Column(Modifier.fillMaxSize().background(EBg)) {
            StudentModuleHeader(
                "تحلیل ${student.optString("firstName")} ${student.optString("lastName")}",
                classDisplay(workspace, student.optString("classId")),
                onBack = { selectedStudent = null; data = null; error = "" }
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = EBrand)
            if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                if (!loading && error.isBlank() && analysis == null) {
                    item { ExtCard { Text("داده تحلیلی برای این دانش‌آموز موجود نیست.", color = EMuted) } }
                }
                analysis?.let { a ->
                    item {
                        ExtCard {
                            Text("خلاصه آموزشی", fontWeight = FontWeight.Black, fontSize = 17.sp)
                            val current = a.optJSONObject("current")
                            val attendance = a.optJSONObject("attendance")
                            val trend = a.optJSONObject("trend")
                            val status = a.optJSONObject("status")
                            if (current != null) {
                                Text("میانگین عملکرد: ${current.optString("averagePercent", "—")}%", color = EBrand, fontWeight = FontWeight.Bold)
                                Text("تعداد آزمون: ${current.optInt("examCount", 0)}", color = EText)
                            }
                            if (attendance != null) {
                                Text("غیبت: ${attendance.optInt("absent", 0)} · تأخیر: ${attendance.optInt("late", 0)}", color = EText)
                            }
                            if (status != null) Text("وضعیت: ${status.optString("label", status.optString("key", "—"))}", color = EText)
                            if (trend != null) Text("روند: ${trend.optString("label", trend.optString("key", "—"))}", color = EMuted)
                        }
                    }
                    val subjects = a.optJSONArray("subjects")?.objects().orEmpty()
                    if (subjects.isNotEmpty()) {
                        item { Text("درس‌ها", fontWeight = FontWeight.Black, fontSize = 18.sp, color = EText, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(subjects) { subject ->
                            ExtCard {
                                Text(subject.optString("subject", subject.optString("name", "درس")), fontWeight = FontWeight.Bold)
                                subject.keys().forEach { key ->
                                    val v = subject.opt(key)
                                    if (key !in setOf("subject", "name") && (v is Number || v is String)) Text("$key: $v", color = EMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val students = allStudents.filter { it.optString("classId") == selectedClassId }
    Column(Modifier.fillMaxSize().background(EBg)) {
        ExtHeader("تحلیل دانش‌آموز", "ابتدا کلاس و پایه را انتخاب کنید")
        Column(Modifier.padding(16.dp)) {
            AssignedClassDropdown(workspace, selectedClassId) { selectedClassId = it }
            Spacer(Modifier.height(8.dp))
            Text("${students.size} دانش‌آموز در ${classDisplay(workspace, selectedClassId)}", color = EMuted, fontSize = 12.sp)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(students, key = { it.optString("id") }) { s ->
                ExtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${s.optString("firstName")} ${s.optString("lastName")}", fontWeight = FontWeight.Bold, color = EText)
                            Text("${classDisplay(workspace, s.optString("classId"))} · کد ${s.optString("studentCode", "—")}", color = EMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                selectedStudent = s
                                data = null
                                loading = true
                                error = ""
                                scope.launch {
                                    try {
                                        data = ApiClient.get("/api/staff/student-analysis?studentId=${URLEncoder.encode(s.optString("id"), "UTF-8")}")
                                    } catch (e: Throwable) {
                                        error = e.message ?: "خطا در دریافت تحلیل"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EBrand)
                        ) { Text("تحلیل") }
                    }
                }
            }
            if (students.isEmpty()) item { ExtCard { Text("برای این کلاس دانش‌آموزی پیدا نشد.", color = EMuted) } }
        }
    }
}

@Composable
fun OnlineClassScreenNative(dashboard:JSONObject?){val scope=rememberCoroutineScope();val rows=dashboard?.optJSONArray("weekSchedule")?.objects().orEmpty();var message by remember{mutableStateOf("")};var pendingId by remember { mutableStateOf("") };var requestedIds by remember { mutableStateOf(setOf<String>()) };Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("کلاس آنلاین","درخواست شروع نشست برای زنگ‌های مجاز");LazyColumn(contentPadding=PaddingValues(16.dp)){items(rows){r->ExtCard{Text("${r.optString("subject")} · ${r.optString("className")}",fontWeight=FontWeight.Black);Text("${r.optString("day")} ${r.optString("startTime")}–${r.optString("endTime")}",color=EMuted);if(listOf("online","hybrid").contains(r.optString("effectiveMode"))){Button(enabled=pendingId.isBlank() && r.optString("id") !in requestedIds,onClick={scope.launch{val id=r.optString("id");pendingId=id;try{ApiClient.post("/api/teacher/online-class/session/start",JSONObject().put("scheduleEntryId",id));requestedIds=requestedIds+id;message="درخواست شروع نشست توسط سرور ثبت شد ✓"}catch(e:Throwable){message=e.message?:"خطا"}finally{pendingId=""}}},colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Text(if(r.optString("id") in requestedIds)"درخواست ثبت شد" else if(pendingId==r.optString("id"))"در حال ثبت…" else "شروع کلاس آنلاین")}}else Text("حالت این زنگ حضوری است.",fontSize=11.sp,color=EMuted)}};if(message.isNotBlank())item{Text(message,Modifier.padding(12.dp),color=if(message.contains("✓")) ESuccess else MaterialTheme.colorScheme.error)}}}}

@Composable
private fun GenericModuleScreen(title:String,path:String,arrays:List<String>,subtitle:String){var data by remember{mutableStateOf<JSONObject?>(null)};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf("")};LaunchedEffect(path){try{data=ApiClient.get(path)}catch(e:Throwable){error=e.message?:"خطا"}finally{loading=false}};Column(Modifier.fillMaxSize().background(EBg)){ExtHeader(title,subtitle);LoadingOrError(loading,error);LazyColumn(contentPadding=PaddingValues(16.dp)){item{data?.optJSONObject("summary")?.let{s->ExtCard{Text("خلاصه",fontWeight=FontWeight.Black);s.keys().forEach{k->Text("$k: ${s.opt(k)}",fontSize=12.sp,color=EText)}}}};arrays.forEach{key->items(data?.optJSONArray(key)?.objects().orEmpty()){r->ExtCard{Text(r.optString("title",r.optString("name",r.optString("studentName",key))),fontWeight=FontWeight.Black);val summary=r.prettySummary();if(summary.isNotBlank())Text(summary,color=EMuted,fontSize=11.sp)}}}}}}
