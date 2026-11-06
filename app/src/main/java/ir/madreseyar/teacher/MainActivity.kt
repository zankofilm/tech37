package ir.madreseyar.teacher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private val BrandDark = Color(0xFF075A63)
private val BrandDeep = Color(0xFF087782)
private val Brand = Color(0xFF0CA6A4)
private val BrandBright = Color(0xFF20C9BE)
private val Sky = Color(0xFF2BA7E8)
private val AppBackground = Color(0xFFF3F9FB)
private val TextPrimary = Color(0xFF12313A)
private val TextSecondary = Color(0xFF71838A)
private val Border = Color(0xFFDDEBED)
private val Success = Color(0xFF16A36A)
private val Warning = Color(0xFFF5A524)

class MainActivity : FragmentActivity() {
    private lateinit var tokenStore: SecureTokenStore
    var onRequireUnlock: (() -> Unit)? = null
    private var stoppedAt: Long = 0L
    private var skipNextUnlock: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        tokenStore = SecureTokenStore(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Brand,
                        onPrimary = Color.White,
                        secondary = Sky,
                        background = AppBackground,
                        surface = Color.White,
                        onSurface = TextPrimary,
                    )
                ) { TeacherDirectApiApp(this, tokenStore) }
            }
        }
    }

    override fun onStop() {
        stoppedAt = System.currentTimeMillis()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0L) {
            if (skipNextUnlock) {
                skipNextUnlock = false
            } else {
                onRequireUnlock?.invoke()
            }
        }
    }

    fun suppressNextUnlock() {
        skipNextUnlock = true
    }

    fun biometricAvailable(): Boolean {
        val result = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateBiometric(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError(errString.toString())
                }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود امن به مدرسه‌یار")
            .setSubtitle("اثر انگشت یا قفل دستگاه را تأیید کنید")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}

private enum class Screen { Unlock, Login, ChangePassword, Home, Classes, Messages, More, Attendance, Students, Grades, Exams, Schedule, Notifications, Practice, IndependentExamCenter, Library, Account, MyTeaching, Curriculum, QuestionBank, ExamCreate, Grading, Classroom, Reports, Homework, AttendanceDiscipline, LearningPlanner, Communications, GuardianMeetings, ActionCenter, StudentAnalysis, OnlineClass }

private data class MenuFeature(
    val title: String,
    val icon: ImageVector,
    val screen: Screen,
    val subtitle: String,
    val tint: Color = Brand,
)

@Composable
private fun TeacherDirectApiApp(activity: MainActivity, tokenStore: SecureTokenStore) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(if (tokenStore.isBiometricEnabled()) Screen.Unlock else Screen.Login) }
    var offerBiometric by remember { mutableStateOf(false) }
    var unlockPrompted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var coreLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var mfaRequired by remember { mutableStateOf(false) }
    var recoveryAllowed by remember { mutableStateOf(false) }
    var pendingUser by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf("") }
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var dashboard by remember { mutableStateOf<JSONObject?>(null) }
    var workspace by remember { mutableStateOf<JSONObject?>(null) }
    var currentPasswordForChange by remember { mutableStateOf("") }
    var messageTarget by remember { mutableStateOf<MessageTarget?>(null) }
    var initialBankQuestionId by remember { mutableStateOf<String?>(null) }

    suspend fun loadCore() = coroutineScope {
        coreLoading = true
        val sessionAtStart = ApiClient.token
        try {
            val dashboardJob = async { runCatching { ApiClient.get("/api/teacher/dashboard") } }
            val workspaceJob = async { runCatching { ApiClient.get("/api/teacher/workspace") } }
            val dashResult = dashboardJob.await()
            val spaceResult = workspaceJob.await()
            if (sessionAtStart != ApiClient.token) return@coroutineScope
            val authFailure = listOfNotNull(dashResult.exceptionOrNull(), spaceResult.exceptionOrNull())
                .filterIsInstance<ApiException>().firstOrNull { it.status == 401 }
            if (authFailure != null) {
                ApiClient.token = ""
                tokenStore.clear()
                clearStudentPhotoCache()
                dashboard = null
                workspace = null
                screen = Screen.Login
                error = "نشست شما منقضی شده است. دوباره وارد شوید."
                return@coroutineScope
            }
            dashResult.getOrNull()?.let { dashboard = it }
            spaceResult.getOrNull()?.let { workspace = it }
            val failed = listOfNotNull(dashResult.exceptionOrNull(), spaceResult.exceptionOrNull())
            error = if (failed.isEmpty()) "" else {
                "دریافت اطلاعات ${if (failed.size == 2) "داشبورد و کلاس‌ها" else if (dashResult.isFailure) "داشبورد" else "کلاس‌ها"} ناموفق بود. برای تلاش دوباره تازه‌سازی کنید. ${failed.first().message.orEmpty()}"
            }
        } finally { coreLoading = false }
    }

    fun fail(e: Throwable) {
        error = e.message ?: "خطای نامشخص"
        if (e is ApiException && e.status == 401) {
            ApiClient.token = ""
            tokenStore.clear()
            clearStudentPhotoCache()
            dashboard = null; workspace = null
            screen = Screen.Login
        }
    }

    fun finishLogin(data: JSONObject, enteredPassword: String) {
        val user = data.optJSONObject("user")
        if (user?.optString("role") != "teacher") {
            error = "این اپ فقط برای حساب معلم قابل استفاده است."
            return
        }
        val token = data.optString("token")
        if (token.isBlank()) {
            error = "سرور توکن ورود برنگرداند."
            return
        }
        ApiClient.token = token
        clearStudentPhotoCache() // A previous teacher's photos must never survive a new login.
        dashboard = null; workspace = null; error = ""
        if (tokenStore.isBiometricEnabled()) tokenStore.save(token) else if (activity.biometricAvailable()) offerBiometric = true
        currentPasswordForChange = if (data.optBoolean("mustChangePassword", false)) enteredPassword else ""
        mfaRequired = false
        recoveryAllowed = false
        pendingUser = ""
        pendingPassword = ""
        loginPassword = ""
        if (data.optBoolean("mustChangePassword", false)) {
            screen = Screen.ChangePassword
        } else {
            // ورود باید فوری تمام شود؛ داده‌های داشبورد در پس‌زمینه و موازی بارگذاری می‌شوند.
            screen = Screen.Home
            scope.launch { loadCore() }
        }
    }

    fun login(identifier: String, password: String) {
        scope.launch {
            busy = true; error = ""
            try {
                val body = JSONObject().put("identifier", identifier).put("password", password)
                val data = ApiClient.post("/api/staff/login", body, auth = false)
                finishLogin(data, password)
            } catch (e: ApiException) {
                if (e.status == 428 || e.code == "MFA_REQUIRED" || e.payload?.optBoolean("mfaRequired") == true) {
                    mfaRequired = true
                    recoveryAllowed = e.payload?.optBoolean("recoveryAllowed", false) == true
                    pendingUser = identifier
                    pendingPassword = password
                    error = ""
                } else fail(e)
            } catch (e: Throwable) { fail(e) } finally { busy = false }
        }
    }

    fun verifyMfa(code: String, useRecovery: Boolean) {
        val identifier = pendingUser
        val password = pendingPassword
        if (identifier.isBlank() || password.isBlank()) {
            mfaRequired = false
            recoveryAllowed = false
            error = "نشست ورود منقضی شده است؛ دوباره وارد شوید."
            return
        }
        scope.launch {
            busy = true; error = ""
            try {
                val body = JSONObject().put("identifier", identifier).put("password", password)
                if (useRecovery) body.put("recoveryCode", code.trim()) else body.put("otp", code.filter(Char::isDigit))
                val data = ApiClient.post("/api/staff/login", body, auth = false)
                finishLogin(data, password)
            } catch (e: ApiException) {
                if (e.status == 401 || e.code == "MFA_INVALID") error = if (useRecovery) "کد بازیابی صحیح نیست." else "کد تأیید صحیح نیست."
                else fail(e)
            } catch (e: Throwable) { fail(e) } finally { busy = false }
        }
    }

    fun cancelMfa() {
        mfaRequired = false
        recoveryAllowed = false
        pendingUser = ""
        pendingPassword = ""
        error = "ورود دومرحله‌ای لغو شد."
    }

    fun biometricLogin() {
        activity.authenticateBiometric(onSuccess = {
            scope.launch {
                error = ""
                try {
                    val token = tokenStore.load() ?: throw IllegalStateException("نشست ذخیره‌شده پیدا نشد.")
                    ApiClient.token = token
                    // قفل دستگاه تأیید شده است؛ UI بلافاصله باز می‌شود و اعتبار نشست در پس‌زمینه چک می‌شود.
                    screen = Screen.Home
                    busy = false
                    scope.launch {
                        try {
                            val me = ApiClient.get("/api/staff/me")
                            if (me.optJSONObject("user")?.optString("role") != "teacher") throw IllegalStateException("نشست معلم معتبر نیست.")
                            loadCore()
                        } catch (e: Throwable) {
                            // A temporary network outage must not erase a valid encrypted biometric session.
                            if ((e is ApiException && e.status in listOf(401, 403)) || e is IllegalStateException) {
                                tokenStore.clear(); ApiClient.token = ""; clearStudentPhotoCache()
                                dashboard = null; workspace = null; screen = Screen.Login
                            }
                            fail(e)
                        }
                    }
                } catch (e: Throwable) { tokenStore.clear(); screen = Screen.Login; fail(e) }
            }
        }, onError = { error = it })
    }

    DisposableEffect(activity) {
        activity.onRequireUnlock = {
            if (tokenStore.isBiometricEnabled() && screen !in listOf(Screen.Login, Screen.ChangePassword, Screen.Unlock)) {
                screen = Screen.Unlock
                unlockPrompted = false
            }
        }
        onDispose { activity.onRequireUnlock = null }
    }

    LaunchedEffect(screen) {
        if (screen == Screen.Unlock && !unlockPrompted) {
            unlockPrompted = true
            biometricLogin()
        } else if (screen != Screen.Unlock) unlockPrompted = false
    }

    fun refreshCore() {
        scope.launch { busy = true; error = ""; try { loadCore() } catch (e: Throwable) { fail(e) } finally { busy = false } }
    }

    fun logout() {
        val previousToken = ApiClient.token
        ApiClient.token = ""
        tokenStore.clear()
        clearStudentPhotoCache()
        dashboard = null; workspace = null
        pendingPassword = ""; loginPassword = ""; currentPasswordForChange = ""
        error = ""; screen = Screen.Login
        // Local logout is instant; server revocation uses only the captured prior token.
        if (previousToken.isNotBlank()) scope.launch { runCatching { ApiClient.revokeSession(previousToken) } }
    }

    // System Back should navigate inside the native app rather than unexpectedly exiting it.
    BackHandler(enabled = screen !in listOf(Screen.Login, Screen.Unlock, Screen.ChangePassword, Screen.Home)) {
        screen = when (screen) {
            Screen.Classes, Screen.Messages, Screen.More, Screen.Exams, Screen.Reports, Screen.Notifications, Screen.Attendance -> Screen.Home
            Screen.Students, Screen.Grades, Screen.Schedule, Screen.MyTeaching,
            Screen.Curriculum, Screen.Classroom, Screen.OnlineClass, Screen.AttendanceDiscipline -> Screen.Classes
            Screen.ExamCreate, Screen.QuestionBank, Screen.Grading, Screen.IndependentExamCenter -> Screen.Exams
            Screen.StudentAnalysis -> Screen.Reports
            Screen.Communications, Screen.GuardianMeetings -> Screen.More
            else -> Screen.More
        }
        messageTarget = null
        error = ""
    }

    AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { target ->
        when (target) {
            Screen.Unlock -> UnlockScreen(busy = busy, error = error, onUnlock = { unlockPrompted = true; biometricLogin() }, onUsePassword = { screen = Screen.Login })
            Screen.Login -> {
                LoginScreen(
                    username = loginIdentifier,
                    password = loginPassword,
                    onUsernameChange = { loginIdentifier = it },
                    onPasswordChange = { loginPassword = it },
                    hasBiometric = tokenStore.isBiometricEnabled() && activity.biometricAvailable(),
                    busy = busy,
                    error = if (mfaRequired) "" else error,
                    onLogin = { u, p -> login(u, p) },
                    onBiometric = { biometricLogin() }
                )
                if (mfaRequired) {
                    MfaDialog(
                        busy = busy,
                        error = error,
                        recoveryAllowed = recoveryAllowed,
                        onVerify = { code, useRecovery -> verifyMfa(code, useRecovery) },
                        onCancel = { cancelMfa() }
                    )
                }
            }
            Screen.ChangePassword -> ChangePasswordScreen(busy, error) { newPassword ->
                scope.launch {
                    busy = true; error = ""
                    try {
                        val changed = ApiClient.post("/api/staff/change-password", JSONObject().put("currentPassword", currentPasswordForChange).put("newPassword", newPassword))
                        changed.optString("token").takeIf { it.isNotBlank() }?.let { ApiClient.token = it; if (tokenStore.isBiometricEnabled()) tokenStore.save(it) }
                        currentPasswordForChange = ""
                        screen = Screen.Home
                        busy = false
                        scope.launch { loadCore() }
                    } catch (e: Throwable) { fail(e) } finally { busy = false }
                }
            }
            else -> NativeShell(
                screen = target,
                dashboard = dashboard,
                workspace = workspace,
                busy = busy || coreLoading,
                error = error,
                onNavigate = { error = ""; messageTarget = null; initialBankQuestionId = null; screen = it },
                onMessageStudent = { id, subject ->
                    messageTarget = MessageTarget(id, subject)
                    error = ""
                    screen = Screen.Messages
                },
                onOpenBankQuestion = { questionId ->
                    initialBankQuestionId = questionId
                    error = ""
                    screen = Screen.ExamCreate
                },
                messageTarget = messageTarget,
                initialBankQuestionId = initialBankQuestionId,
                onRefresh = { refreshCore() },
                onLogout = { logout() }
            )
        }
    }
    if (offerBiometric && screen == Screen.Home && ApiClient.token.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { offerBiometric = false },
            icon = { Icon(Icons.Default.Fingerprint, null, tint = Brand) },
            title = { Text("فعال‌سازی قفل اثر انگشت", fontWeight = FontWeight.Black) },
            text = { Text("فقط یک‌بار فعالش کنید. از این به بعد هنگام باز شدن مدرسه‌یار، اثر انگشت یا قفل امن دستگاه درخواست می‌شود و داخل بخش‌های برنامه تکرار نمی‌شود.") },
            confirmButton = { Button(onClick = { activity.authenticateBiometric(onSuccess = { tokenStore.enableBiometric(ApiClient.token); offerBiometric = false }, onError = { error = it }) }, colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("فعال کن") } },
            dismissButton = { TextButton(onClick = { offerBiometric = false }) { Text("فعلاً نه") } }
        )
    }
}

@Composable
private fun UnlockScreen(busy: Boolean, error: String, onUnlock: () -> Unit, onUsePassword: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(96.dp), RoundedCornerShape(28.dp), Color.White, shadowElevation = 7.dp) { Image(painterResource(R.mipmap.ic_launcher), null, Modifier.padding(12.dp)) }
        Spacer(Modifier.height(22.dp)); Text("مدرسه‌یار قفل است", fontSize = 25.sp, fontWeight = FontWeight.Black, color = BrandDark); Spacer(Modifier.height(6.dp)); Text("برای ورود اثر انگشت یا قفل امن دستگاه را تأیید کنید.", color = TextSecondary, textAlign = TextAlign.Center)
        if (error.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(22.dp)); Button(onClick=onUnlock, enabled=!busy, modifier=Modifier.fillMaxWidth().height(54.dp), colors=ButtonDefaults.buttonColors(containerColor=Brand), shape=RoundedCornerShape(16.dp)) { Icon(Icons.Default.Fingerprint,null); Spacer(Modifier.width(8.dp)); Text("باز کردن با اثر انگشت", fontWeight=FontWeight.Bold) }
        TextButton(onClick=onUsePassword) { Text("ورود با نام کاربری و رمز عبور") }
    }
}

@Composable
private fun LoginScreen(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    hasBiometric: Boolean,
    busy: Boolean,
    error: String,
    onLogin: (String, String) -> Unit,
    onBiometric: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().height(235.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFE8FAFC), Color(0xFFF6FCFD))))
            ) {
                Surface(
                    Modifier.size(96.dp).align(Alignment.Center),
                    RoundedCornerShape(28.dp),
                    Color.White,
                    shadowElevation = 8.dp
                ) { Image(painterResource(R.mipmap.ic_launcher), null, Modifier.padding(12.dp)) }
                Surface(
                    Modifier.align(Alignment.BottomCenter).offset(y = 20.dp),
                    RoundedCornerShape(20.dp), Color.White, shadowElevation = 5.dp
                ) {
                    Text("مدرسه‌یار معلم", Modifier.padding(horizontal = 24.dp, vertical = 10.dp), color = BrandDark, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(42.dp))
            Text("ورود به حساب کاربری", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 25.sp)
            Text("مدیریت کلاس، نمره، آزمون و ارتباط آموزشی", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(22.dp))
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                RoundedCornerShape(26.dp),
                CardDefaults.cardColors(Color.White),
                CardDefaults.cardElevation(3.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    OutlinedTextField(
                        username, onUsernameChange, Modifier.fillMaxWidth(),
                        label = { Text("نام کاربری") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = Brand) },
                        singleLine = true, shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, focusedLabelColor = Brand)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        password, onPasswordChange, Modifier.fillMaxWidth(),
                        label = { Text("رمز عبور") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Brand) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, focusedLabelColor = Brand)
                    )
                    if (error.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { if (username.isNotBlank() && password.isNotBlank()) onLogin(username.trim(), password) },
                        enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand)
                    ) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White) else Text("ورود", fontWeight = FontWeight.Black, fontSize = 16.sp) }
                    if (hasBiometric) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onBiometric, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand)
                        ) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("ورود با اثر انگشت", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("مدرسه‌یار • نسخه ${BuildConfig.VERSION_NAME}", color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MfaDialog(
    busy: Boolean,
    error: String,
    recoveryAllowed: Boolean,
    onVerify: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var otp by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var useRecovery by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        icon = { Icon(Icons.Default.Security, null, tint = Brand) },
        title = { Text("تأیید دومرحله‌ای", fontWeight = FontWeight.Black, color = BrandDark) },
        text = {
            Column {
                Text(
                    if (useRecovery) "کد بازیابی را وارد کنید." else "کد ۶ رقمی برنامه Authenticator را وارد کنید.",
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                if (!useRecovery) {
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { otp = it.filter(Char::isDigit).take(8) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("کد Authenticator") },
                        placeholder = { Text("000000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = recoveryCode,
                        onValueChange = { recoveryCode = it.take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("کد بازیابی") },
                        placeholder = { Text("ABCD-EFGH") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                if (recoveryAllowed) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { useRecovery = !useRecovery },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (useRecovery) "استفاده از کد Authenticator" else "استفاده از کد بازیابی")
                    }
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) { Text("انصراف") }
        },
        confirmButton = {
            Button(
                onClick = { onVerify(if (useRecovery) recoveryCode.trim() else otp.trim(), useRecovery) },
                enabled = !busy && (if (useRecovery) recoveryCode.isNotBlank() else otp.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = Brand)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("تأیید و ورود")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
private fun ChangePasswordScreen(busy: Boolean, error: String, onSubmit: (String) -> Unit) {
    var p1 by remember { mutableStateOf("") }; var p2 by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("تغییر رمز اجباری", fontSize = 28.sp, fontWeight = FontWeight.Black, color = BrandDark)
        Spacer(Modifier.height(8.dp)); Text("سرور درخواست کرده قبل از ادامه رمز عبور را تغییر دهید.", color = TextSecondary)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(p1, { p1 = it }, Modifier.fillMaxWidth(), label = { Text("رمز جدید") }, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(p2, { p2 = it }, Modifier.fillMaxWidth(), label = { Text("تکرار رمز جدید") }, visualTransformation = PasswordVisualTransformation())
        if (error.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { if (p1.length >= 8 && p1 == p2) onSubmit(p1) }, enabled = !busy && p1.length >= 8 && p1 == p2, modifier = Modifier.fillMaxWidth()) { Text("ثبت رمز و ادامه") }
    }
}

@Composable
private fun TeacherBottomBar(rootTab: Screen, onNavigate: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.Home, "خانه", Icons.Outlined.Home),
        Triple(Screen.Classes, "کلاس‌ها", Icons.Outlined.Groups),
        Triple(Screen.Exams, "آزمون‌ها", Icons.Default.AssignmentTurnedIn),
        Triple(Screen.Reports, "گزارش‌ها", Icons.Default.Insights),
        Triple(Screen.More, "بیشتر", Icons.Outlined.MoreHoriz),
    )
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, TeacherPalette.Border.copy(.65f)),
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (screen, label, icon) ->
                val selected = rootTab == screen
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(17.dp))
                        .background(if (selected) Color(0xFFDDF6F5) else Color.Transparent)
                        .clickable { onNavigate(screen) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, contentDescription = label, tint = if (selected) TeacherPalette.DeepTeal else TeacherPalette.Muted, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(label, color = if (selected) TeacherPalette.DeepTeal else TeacherPalette.Muted, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun NativeShell(
    screen: Screen,
    dashboard: JSONObject?,
    workspace: JSONObject?,
    busy: Boolean,
    error: String,
    onNavigate: (Screen) -> Unit,
    onMessageStudent: (String, String) -> Unit,
    onOpenBankQuestion: (String?) -> Unit,
    messageTarget: MessageTarget?,
    initialBankQuestionId: String?,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    val rootTab = when (screen) {
        Screen.Home, Screen.Attendance, Screen.Notifications -> Screen.Home
        Screen.Classes, Screen.Students, Screen.Grades, Screen.Schedule, Screen.MyTeaching, Screen.Curriculum, Screen.Classroom, Screen.OnlineClass, Screen.AttendanceDiscipline -> Screen.Classes
        Screen.Exams, Screen.ExamCreate, Screen.QuestionBank, Screen.Grading, Screen.IndependentExamCenter -> Screen.Exams
        Screen.Reports, Screen.StudentAnalysis -> Screen.Reports
        else -> Screen.More
    }
    Scaffold(
        containerColor = AppBackground,
        bottomBar = { TeacherBottomBar(rootTab = rootTab, onNavigate = onNavigate) }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (screen) {
                Screen.Home -> HomeScreen(dashboard, workspace, onNavigate, onRefresh)
                Screen.Classes -> ClassesScreen(workspace, dashboard, onNavigate, onMessageStudent)
                Screen.Messages -> MessagesScreen(messageTarget)
                Screen.More -> MoreScreen(onNavigate, onLogout)
                Screen.Attendance -> AttendanceScreen(dashboard, onRefresh, onNavigate)
                Screen.Students -> StudentsProScreen(workspace)
                Screen.Grades -> ProfessionalGradebookScreen(workspace)
                Screen.Exams -> ExamsScreen(workspace, onNavigate)
                Screen.Schedule -> ScheduleScreen(dashboard, onNavigate)
                Screen.Notifications -> NotificationsScreen(dashboard)
                Screen.Practice -> PracticeTeacherScreen()
                Screen.IndependentExamCenter -> IndependentExamCenterScreen()
                Screen.Library -> LibraryTeacherScreen(workspace)
                Screen.Account -> AccountScreen(onLogout)
                Screen.MyTeaching -> TeachingBooksScreen(workspace) { onNavigate(Screen.Classes) }
                Screen.Curriculum -> CurriculumScreenNative(
                    onOpenQuestionBank = { onNavigate(Screen.QuestionBank) },
                    onOpenExamStudio = { onNavigate(Screen.ExamCreate) })
                Screen.QuestionBank -> QuestionBankNativeScreen(onOpenBankQuestion)
                Screen.ExamCreate -> ExamStudioScreen(workspace, initialBankQuestionId)
                Screen.Grading -> ProfessionalGradingScreen(workspace)
                Screen.Classroom -> ClassJournalScreen(workspace)
                Screen.Reports -> ContinuousReportsNativeScreen(workspace, dashboard)
                Screen.Homework -> HomeworkCenterProScreen(workspace)
                Screen.AttendanceDiscipline -> AttendanceDisciplineInteractiveScreen()
                Screen.LearningPlanner -> LearningPlannerScreenNative(workspace)
                Screen.Communications -> CommunicationsInteractiveScreen()
                Screen.GuardianMeetings -> GuardianMeetingsNativeScreen(workspace)
                Screen.ActionCenter -> ActionCenterInteractiveScreen()
                Screen.StudentAnalysis -> StudentAnalysisNativeScreen(workspace)
                Screen.OnlineClass -> OnlineClassScreenNative(dashboard)
                else -> Unit
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if (error.isNotBlank() && screen != Screen.Login) {
                Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), color = Color(0xFFFFEEEE), shape = RoundedCornerShape(14.dp), shadowElevation = 4.dp) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = Color(0xFFA52929), fontSize = 13.sp)
                        TextButton(onClick = onRefresh) { Text("تازه‌سازی") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(dashboard: JSONObject?, workspace: JSONObject?, onNavigate: (Screen) -> Unit, onRefresh: () -> Unit) {
    val teacher = dashboard?.optJSONObject("teacher")
    val school = dashboard?.optJSONObject("school")
    val clock = dashboard?.optJSONObject("clock")
    val current = dashboard?.optJSONObject("currentClass")
    val notifications = dashboard?.optJSONArray("notifications") ?: JSONArray()
    val exams = workspace?.optJSONArray("exams") ?: JSONArray()
    val assignments = workspace?.optJSONArray("assignments") ?: JSONArray()
    val classes = workspace?.optJSONArray("classes") ?: JSONArray()
    val today = dashboard?.optJSONArray("todaySchedule")?.toObjects().orEmpty()
    val classCount = if (classes.length() > 0) classes.length() else assignments.toObjects().map { it.optString("classId") }.filter { it.isNotBlank() }.distinct().size
    val attendanceRecords = current?.optJSONObject("attendance")?.optJSONArray("records")?.toObjects().orEmpty()
    val presentCount = attendanceRecords.count { it.optString("status") == "present" }
    val attendanceValue = if (attendanceRecords.isNotEmpty()) "${(presentCount * 100) / attendanceRecords.size}٪" else "—"
    val teacherName = listOf(teacher?.optString("firstName").orEmpty(), teacher?.optString("lastName").orEmpty())
        .filter { it.isNotBlank() }.joinToString(" ").ifBlank { "استاد گرامی" }
    val dateLabel = listOf(clock?.optString("dayName").orEmpty(), clock?.optString("dateFa").orEmpty())
        .filter { it.isNotBlank() }.joinToString("، ").ifBlank { "برنامه روزانه شما" }
    val homeworkCount = workspace?.optJSONArray("homework")?.length()?.toString()
        ?: dashboard?.optInt("homeworkCount", -1)?.takeIf { it >= 0 }?.toString() ?: "—"
    val schoolName = school?.optString("name")?.takeIf { it.isNotBlank() } ?: "مدیریت کلاس‌ها و دانش‌آموزان خود را در این بخش انجام دهید."

    val features = listOf(
        MenuFeature("کلاس‌ها", Icons.Default.Groups, Screen.Classes, "مدیریت کلاس", TeacherPalette.Blue),
        MenuFeature("دانش‌آموزان", Icons.Default.People, Screen.Students, "پرونده‌ها", Color(0xFF2799D8)),
        MenuFeature("دفتر نمره", Icons.Default.Grading, Screen.Grades, "ثبت نمرات", TeacherPalette.Purple),
        MenuFeature("حضور و غیاب", Icons.Default.EventAvailable, Screen.Attendance, "ثبت حضور", TeacherPalette.Green),
        MenuFeature("مخزن سؤال", Icons.Default.Storage, Screen.QuestionBank, "بانک سؤال", TeacherPalette.Orange),
        MenuFeature("آزمون‌ساز", Icons.Default.EditNote, Screen.ExamCreate, "طراحی آزمون", TeacherPalette.Blue),
        MenuFeature("گزارش‌ها", Icons.Default.Insights, Screen.Reports, "تحلیل عملکرد", TeacherPalette.Purple),
        MenuFeature("پیام‌ها", Icons.Default.ChatBubbleOutline, Screen.Messages, "ارتباط آموزشی", TeacherPalette.Blue),
    )

    LazyColumn(
        Modifier.fillMaxSize().background(TeacherPalette.Background),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            TeacherHeroHeader(
                title = "مدرسه‌یار | اپ معلم",
                subtitle = "پنل حرفه‌ای مدیریت امور آموزشی",
                greeting = "سلام $teacherName عزیز",
                greetingSubtitle = schoolName,
                dateText = dateLabel,
                timeText = clock?.optString("time")?.takeIf { it.isNotBlank() },
                notificationCount = notifications.length(),
                onSearch = { onNavigate(Screen.Students) },
                onNotifications = { onNavigate(Screen.Notifications) },
                onRefresh = onRefresh,
            )
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TeacherMetricTile("کلاس‌ها", classCount.toString(), "کلاس فعال", Icons.Default.Groups, TeacherPalette.Blue, TeacherPalette.PaleBlue, Modifier.weight(1f))
                TeacherMetricTile("حضور و غیاب", attendanceValue, "حضور امروز", Icons.Default.EventAvailable, TeacherPalette.Green, TeacherPalette.PaleGreen, Modifier.weight(1f))
                TeacherMetricTile("آزمون‌ها", exams.length().toString(), "آزمون ثبت‌شده", Icons.Default.AssignmentTurnedIn, TeacherPalette.Purple, TeacherPalette.PalePurple, Modifier.weight(1f))
                TeacherMetricTile("تکالیف", homeworkCount, "در سامانه", Icons.Default.Layers, TeacherPalette.Orange, TeacherPalette.PaleOrange, Modifier.weight(1f))
            }
        }

        item {
            TeacherPanel(Modifier.padding(horizontal = 13.dp)) {
                TeacherSectionTitle("برنامه امروز", "مشاهده برنامه هفتگی", { onNavigate(Screen.Schedule) }, Icons.Default.CalendarMonth)
                Spacer(Modifier.height(9.dp))
                if (today.isNotEmpty()) {
                    today.take(4).forEachIndexed { index, row ->
                        Surface(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onNavigate(Screen.Classes) },
                            shape = RoundedCornerShape(14.dp), color = Color(0xFFF4F8FB),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TeacherPalette.Border.copy(.65f)),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(35.dp), RoundedCornerShape(11.dp), Color(0xFFE1F1FA)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MenuBook, null, tint = TeacherPalette.Blue, modifier = Modifier.size(19.dp)) }
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${row.optString("subject", "درس")} · ${row.optString("className", "کلاس")}", fontWeight = FontWeight.Bold, color = TeacherPalette.Text, fontSize = 11.sp)
                                    Text("زنگ ${row.optInt("periodNo", index + 1)}", color = TeacherPalette.Muted, fontSize = 9.sp)
                                }
                                Text("${row.optString("startTime")}–${row.optString("endTime")}", color = TeacherPalette.Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else if (current != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Class, null, tint = TeacherPalette.Green)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${current.optString("subject", "درس جاری")} · ${current.optString("className", "کلاس جاری")}", color = TeacherPalette.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${current.optString("startTime")}–${current.optString("endTime")}", color = TeacherPalette.Muted, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Button(
                        onClick = { onNavigate(Screen.Attendance) },
                        enabled = current.optBoolean("attendanceOpen", false),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TeacherPalette.Teal),
                    ) { Icon(Icons.Default.FactCheck, null); Spacer(Modifier.width(6.dp)); Text("ثبت حضور و غیاب") }
                } else {
                    Text("برنامه امروز از سرور دریافت نشده است.", color = TeacherPalette.Muted, fontSize = 11.sp)
                    TextButton(onClick = onRefresh) { Text("تلاش دوباره") }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp)) {
                TeacherSectionTitle("دسترسی سریع", icon = Icons.Default.DashboardCustomize)
                Spacer(Modifier.height(9.dp))
                features.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { feature ->
                            TeacherQuickTile(feature.title, feature.icon, feature.tint, { onNavigate(feature.screen) }, Modifier.weight(1f))
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier, RoundedCornerShape(17.dp), CardDefaults.cardColors(tint.copy(alpha = .075f)), CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(5.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, color = TextPrimary, maxLines = 1)
            Text(label, color = TextSecondary, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun QuickFeature(feature: MenuFeature, onClick: () -> Unit) {
    Card(
        Modifier.padding(4.dp).fillMaxWidth().height(98.dp).clickable(onClick = onClick),
        RoundedCornerShape(17.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(Modifier.size(37.dp), RoundedCornerShape(12.dp), feature.tint.copy(alpha = .11f)) {
                Box(contentAlignment = Alignment.Center) { Icon(feature.icon, null, tint = feature.tint, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(6.dp))
            Text(feature.title, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, color = TextPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun ClassesScreen(
    workspace: JSONObject?,
    dashboard: JSONObject?,
    onNavigate: (Screen) -> Unit,
    onMessageStudent: (String, String) -> Unit,
) {
    val assignments = workspace?.optJSONArray("assignments")?.toObjects().orEmpty()
    val classes = workspace?.optJSONArray("classes")?.toObjects().orEmpty()
    val students = workspace?.optJSONArray("students")?.toObjects().orEmpty()
    val classMap = classes.associateBy { it.optString("id") }
    val classIds = (assignments.map { it.optString("classId") } + classes.map { it.optString("id") }).filter { it.isNotBlank() }.distinct()
    var selectedClassId by remember(workspace?.toString()) { mutableStateOf(classIds.firstOrNull().orEmpty()) }
    var search by remember { mutableStateOf("") }
    var sortByCode by remember { mutableStateOf(false) }
    var onlyWithCode by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val selectedAssignments = assignments.filter { it.optString("classId") == selectedClassId }
    val classStudents = students.filter { it.optString("classId") == selectedClassId }
    val selectedStudents = classStudents.filter { student ->
        val name = "${student.optString("firstName")} ${student.optString("lastName")}".trim()
        val code = student.optString("studentCode", student.optString("code"))
        (!onlyWithCode || code.isNotBlank()) && (search.isBlank() || name.contains(search.trim(), true) || code.contains(search.trim(), true))
    }.let { rows -> if (sortByCode) rows.sortedBy { it.optString("studentCode", it.optString("code")) } else rows.sortedBy { "${it.optString("firstName")} ${it.optString("lastName")}" } }
    val className = classMap[selectedClassId]?.optString("name") ?: selectedAssignments.firstOrNull()?.optString("className") ?: selectedClassId.ifBlank { "کلاس‌های من" }
    val subjectNames = selectedAssignments.map { it.optString("subject") }.filter { it.isNotBlank() }.distinct().joinToString("، ")
    val teacher = dashboard?.optJSONObject("teacher")
    val teacherName = listOf(teacher?.optString("firstName").orEmpty(), teacher?.optString("lastName").orEmpty()).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "استاد گرامی" }
    val notifications = dashboard?.optJSONArray("notifications")?.length() ?: 0
    val currentRecords = dashboard?.optJSONObject("currentClass")?.optJSONObject("attendance")?.optJSONArray("records")?.toObjects().orEmpty()
    val present = currentRecords.count { it.optString("status") == "present" }
    val attendance = if (currentRecords.isNotEmpty()) "${present * 100 / currentRecords.size}٪" else "—"
    val nextLesson = selectedAssignments.firstOrNull()?.optString("subject")?.takeIf { it.isNotBlank() } ?: "ثبت نشده"

    LazyColumn(
        Modifier.fillMaxSize().background(TeacherPalette.Background),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            TeacherHeroHeader(
                title = "کلاس‌ها و دانش‌آموزان",
                subtitle = "مدیریت کلاس‌ها و دسترسی سریع به امور آموزشی",
                greeting = "سلام $teacherName عزیز",
                greetingSubtitle = "مدیریت کلاس‌ها و دانش‌آموزان خود را در این بخش انجام دهید.",
                notificationCount = notifications,
                onSearch = { onNavigate(Screen.Students) },
                onNotifications = { onNavigate(Screen.Notifications) },
            )
        }
        item {
            if (classIds.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(classIds) { id ->
                        val label = classMap[id]?.optString("name") ?: assignments.firstOrNull { it.optString("classId") == id }?.optString("className") ?: id
                        TeacherFilterChip(label, selectedClassId == id, { selectedClassId = id; search = "" }, Icons.Default.Groups)
                    }
                }
            }
        }
        item {
            TeacherPanel(Modifier.padding(horizontal = 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(className, color = TeacherPalette.Text, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(subjectNames.ifBlank { "کلاس انتخاب‌شده" }, color = TeacherPalette.DeepTeal, fontSize = 10.sp)
                    }
                    TeacherTag("${classStudents.size} دانش‌آموز", TeacherPalette.Blue, TeacherPalette.PaleBlue)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TeacherMetricTile("تعداد دانش‌آموز", classStudents.size.toString(), "در این کلاس", Icons.Default.Groups, TeacherPalette.Blue, TeacherPalette.PaleBlue, Modifier.weight(1f))
                    TeacherMetricTile("نرخ حضور", attendance, "داده زنگ جاری", Icons.Default.EventAvailable, TeacherPalette.Green, TeacherPalette.PaleGreen, Modifier.weight(1f))
                    TeacherMetricTile("درس بعدی", nextLesson, "از تخصیص تدریس", Icons.Default.CalendarMonth, TeacherPalette.Purple, TeacherPalette.PalePurple, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                TeacherSectionTitle("دسترسی سریع کلاس", icon = Icons.Default.DashboardCustomize)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeacherQuickTile("حضور و غیاب", Icons.Default.EventAvailable, TeacherPalette.Green, { onNavigate(Screen.AttendanceDiscipline) }, Modifier.weight(1f))
                    TeacherQuickTile("ثبت نمره", Icons.Default.Grading, TeacherPalette.Purple, { onNavigate(Screen.Grades) }, Modifier.weight(1f))
                    TeacherQuickTile("تکلیف و فعالیت", Icons.Default.EditNote, TeacherPalette.Blue, { onNavigate(Screen.Homework) }, Modifier.weight(1f))
                    TeacherQuickTile("گزارش عملکرد", Icons.Default.Insights, TeacherPalette.Purple, { onNavigate(Screen.Reports) }, Modifier.weight(1f))
                    TeacherQuickTile("اعلانات کلاس", Icons.Default.Campaign, TeacherPalette.Blue, { onNavigate(Screen.Communications) }, Modifier.weight(1f))
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp)) {
                TeacherSectionTitle("فهرست دانش‌آموزان (${selectedStudents.size} نفر)", icon = Icons.Default.Groups)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it }, modifier = Modifier.weight(1f),
                        singleLine = true, placeholder = { Text("جستجوی دانش‌آموز...", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = TeacherPalette.Border, focusedBorderColor = TeacherPalette.Teal)
                    )
                    Box {
                        IconButton(onClick = { filterMenu = true }, modifier = Modifier.background(Color.White, RoundedCornerShape(13.dp)).border(1.dp, TeacherPalette.Border, RoundedCornerShape(13.dp))) { Icon(Icons.Default.FilterList, "فیلتر", tint = TeacherPalette.DeepTeal) }
                        DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                            DropdownMenuItem(text = { Text("همه دانش‌آموزان") }, onClick = { onlyWithCode = false; filterMenu = false })
                            DropdownMenuItem(text = { Text("دارای کد دانش‌آموزی") }, onClick = { onlyWithCode = true; filterMenu = false })
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }, modifier = Modifier.background(Color.White, RoundedCornerShape(13.dp)).border(1.dp, TeacherPalette.Border, RoundedCornerShape(13.dp))) { Icon(Icons.Default.Sort, "مرتب‌سازی", tint = TeacherPalette.DeepTeal) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            DropdownMenuItem(text = { Text("مرتب‌سازی بر اساس نام") }, onClick = { sortByCode = false; sortMenu = false })
                            DropdownMenuItem(text = { Text("مرتب‌سازی بر اساس کد") }, onClick = { sortByCode = true; sortMenu = false })
                        }
                    }
                }
            }
        }
        items(selectedStudents, key = { it.optString("id").ifBlank { it.toString() } }) { student ->
            val studentName = "${student.optString("firstName")} ${student.optString("lastName")}".trim().ifBlank { "دانش‌آموز" }
            val studentId = student.optString("id")
            val subject = selectedAssignments.firstOrNull()?.optString("subject").orEmpty()
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp),
                RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TeacherPalette.Border.copy(.8f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeacherInitialAvatar(studentName, Modifier.size(39.dp))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(studentName, color = TeacherPalette.Text, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                        Text("کد: ${student.optString("studentCode", student.optString("code", "—"))}", color = TeacherPalette.Muted, fontSize = 9.sp, maxLines = 1)
                    }
                    if (studentId.isNotBlank()) {
                        TeacherStudentAction("پیام", Icons.Default.ChatBubbleOutline, TeacherPalette.Blue) { onMessageStudent(studentId, subject) }
                        Spacer(Modifier.width(3.dp))
                    }
                    TeacherStudentAction("حضور", Icons.Default.EventAvailable, TeacherPalette.Green) { onNavigate(Screen.AttendanceDiscipline) }
                    Spacer(Modifier.width(3.dp))
                    TeacherStudentAction("نمره", Icons.Default.Grading, TeacherPalette.Purple) { onNavigate(Screen.Grades) }
                    Spacer(Modifier.width(3.dp))
                    TeacherStudentAction("پرونده", Icons.Default.FolderOpen, TeacherPalette.Orange) { onNavigate(Screen.StudentAnalysis) }
                }
            }
        }
        if (classIds.isEmpty()) item { Text("هنوز کلاسی برای این حساب از سرور دریافت نشده است.", Modifier.padding(24.dp), color = TeacherPalette.Muted) }
        if (classIds.isNotEmpty() && selectedStudents.isEmpty()) item { Text(if (search.isBlank()) "برای این کلاس دانش‌آموزی ثبت نشده است." else "دانش‌آموزی با این جست‌وجو پیدا نشد.", Modifier.padding(24.dp), color = TeacherPalette.Muted) }
    }
}

@Composable
private fun AttendanceScreen(dashboard: JSONObject?, onRefresh: () -> Unit, onNavigate: (Screen) -> Unit) {
    val scope = rememberCoroutineScope()
    val current = dashboard?.optJSONObject("currentClass")
    val date = dashboard?.optJSONObject("clock")?.optString("date").orEmpty()
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    if (current == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("حضور و غیاب")
            EmptyState("زنگ جاری یا مجوز ثبت حضور برای این ساعت از سرور دریافت نشده است. برای گزارش و پیگیری سایر کلاس‌ها از بخش حضور و انضباط استفاده کنید.")
            Button(onClick = { onNavigate(Screen.AttendanceDiscipline) }, Modifier.fillMaxWidth().padding(16.dp)) { Text("حضور و انضباط کلاس‌ها") }
            OutlinedButton(onClick = { onNavigate(Screen.Schedule) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("مشاهده برنامه هفتگی") }
            TextButton(onClick = onRefresh, Modifier.fillMaxWidth()) { Text("تازه‌سازی وضعیت زنگ جاری") }
        }
        return
    }

    val students = current.optJSONArray("students")?.toObjects().orEmpty()
    val savedRecords = current.optJSONObject("attendance")?.optJSONArray("records")?.toObjects().orEmpty()
        .associateBy { it.optString("studentId") }
    val statuses = remember(current.toString()) {
        mutableStateMapOf<String, String>().apply {
            students.forEach { st ->
                val saved = savedRecords[st.optString("id")]?.optString("status").orEmpty()
                put(st.optString("id"), when (saved) {
                    "present" -> "present"
                    "absent_excused" -> "absent_excused"
                    "absent_unexcused" -> "absent_unexcused"
                    "absent_pending", "absent" -> "absent_pending"
                    else -> "present"
                })
            }
        }
    }
    val presentCount = statuses.values.count { it == "present" }
    val excusedCount = statuses.values.count { it == "absent_excused" }
    val unexcusedCount = statuses.values.count { it == "absent_unexcused" }
    val pendingCount = statuses.values.count { it == "absent_pending" }
    val absentCount = excusedCount + unexcusedCount + pendingCount
    val allPresent = students.isNotEmpty() && absentCount == 0

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("حضور و غیاب")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${current.optString("subject")} · ${current.optString("className")}", fontWeight = FontWeight.Black, fontSize = 17.sp, color = TextPrimary)
                        Text("${current.optString("startTime")} تا ${current.optString("endTime")}", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(Modifier.weight(1f), RoundedCornerShape(15.dp), Color(0xFFE8F8F0)) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(presentCount.toString(), color = Success, fontWeight = FontWeight.Black, fontSize = 20.sp); Text("حاضر", color = Success, fontSize = 11.sp) } }
                            Surface(Modifier.weight(1f), RoundedCornerShape(15.dp), Color(0xFFFFF5E8)) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(excusedCount.toString(), color = Color(0xFFB7791F), fontWeight = FontWeight.Black, fontSize = 20.sp); Text("موجه", color = Color(0xFFB7791F), fontSize = 11.sp) } }
                            Surface(Modifier.weight(1f), RoundedCornerShape(15.dp), Color(0xFFFFEEEE)) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(unexcusedCount.toString(), color = Color(0xFFD64545), fontWeight = FontWeight.Black, fontSize = 20.sp); Text("غیرموجه", color = Color(0xFFD64545), fontSize = 11.sp) } }
                        }
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { students.forEach { statuses[it.optString("id")] = "present" }; message = "" },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (allPresent) Color(0xFFDDF7ED) else Brand.copy(alpha = .10f), contentColor = if (allPresent) Success else BrandDark)
                        ) { Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(7.dp)); Text(if (allPresent) "همه دانش‌آموزان حاضر هستند" else "انتخاب همه = حاضر", fontWeight = FontWeight.Bold) }
                        if (absentCount > 0) { Spacer(Modifier.height(8.dp)); Text("فقط دانش‌آموزان غایب را انتخاب کنید و نوع غیبت را مشخص کنید.", color = TextSecondary, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            items(students) { st ->
                val id = st.optString("id")
                val status = statuses[id] ?: "present"
                val isPresent = status == "present"
                val isExcused = status == "absent_excused"
                val isUnexcused = status == "absent_unexcused"
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), RoundedCornerShape(17.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(40.dp).clickable { statuses[id] = if (isPresent) "absent_pending" else "present"; message = "" }, CircleShape, if (isPresent) Color(0xFFE8F8F0) else Color(0xFFFFEEEE)) {
                                Box(contentAlignment = Alignment.Center) { Icon(if (isPresent) Icons.Default.Check else Icons.Default.PersonOff, null, tint = if (isPresent) Success else Color(0xFFD64545), modifier = Modifier.size(21.dp)) }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text("${st.optString("firstName")} ${st.optString("lastName")}", fontWeight = FontWeight.Bold); Text(st.optString("studentCode"), color = TextSecondary, fontSize = 11.sp) }
                            FilledTonalButton(
                                onClick = { statuses[id] = if (isPresent) "absent_pending" else "present"; message = "" }, shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (isPresent) Color(0xFFE8F8F0) else Color(0xFFFFEEEE), contentColor = if (isPresent) Success else Color(0xFFD64545)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text(if (isPresent) "حاضر" else "غایب", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        }
                        if (!isPresent) {
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = isExcused, onClick = { statuses[id] = "absent_excused"; message = "" }, label = { Text("غایب موجه") }, modifier = Modifier.weight(1f))
                                FilterChip(selected = isUnexcused, onClick = { statuses[id] = "absent_unexcused"; message = "" }, label = { Text("غایب غیرموجه") }, modifier = Modifier.weight(1f))
                            }
                            if (status == "absent_pending") Text("نوع غیبت را مشخص کنید.", color = Warning, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            item {
                if (message.isNotBlank()) Text(message, Modifier.padding(vertical = 8.dp), color = if (message.startsWith("ثبت شد")) Success else MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        if (pendingCount > 0) { message = "برای $pendingCount دانش‌آموز غایب، موجه یا غیرموجه را مشخص کنید."; return@Button }
                        scope.launch {
                            saving = true; message = ""
                            try {
                                val records = JSONArray()
                                students.forEach { st -> records.put(JSONObject().put("studentId", st.optString("id")).put("status", statuses[st.optString("id")] ?: "present").put("note", "")) }
                                val result = ApiClient.post("/api/staff/attendance/program-save", JSONObject().put("scheduleEntryId", current.optString("id")).put("date", date).put("records", records))
                                val summary = result.optJSONObject("summary")
                                message = "ثبت شد: ${summary?.optInt("present", presentCount) ?: presentCount} حاضر · ${summary?.optInt("excused", excusedCount) ?: excusedCount} موجه · ${summary?.optInt("unexcused", unexcusedCount) ?: unexcusedCount} غیرموجه"
                                onRefresh()
                            } catch (e: Throwable) { message = e.message ?: "خطا در ثبت حضور و غیاب" }
                            finally { saving = false }
                        }
                    },
                    enabled = !saving && current.optBoolean("attendanceOpen", false) && date.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 8.dp), shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand)
                ) { Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(6.dp)); Text(if (saving) "در حال ثبت…" else "ثبت نهایی حضور و غیاب", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun GradesScreen(workspace: JSONObject?) {
    val scope = rememberCoroutineScope(); val assignments = workspace?.optJSONArray("assignments")?.toObjects().orEmpty().distinctBy { "${it.optString("classId")}|${it.optString("subject")}" }
    val classes = workspace?.optJSONArray("classes")?.toObjects().orEmpty().associateBy { it.optString("id") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }; var period by remember { mutableStateOf("semester2") }; var gradebook by remember { mutableStateOf<JSONObject?>(null) }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    fun load(a: JSONObject) { selected = a; scope.launch { loading = true; error = ""; try { val c = URLEncoder.encode(a.optString("classId"), "UTF-8"); val s = URLEncoder.encode(a.optString("subject"), "UTF-8"); gradebook = ApiClient.get("/api/staff/gradebook?classId=$c&subject=$s&academicPeriod=$period") } catch (e: Throwable) { error = e.message ?: "خطا" } finally { loading = false } } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("دفتر نمره")
        if (selected == null) LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item { Text("کلاس و درس را انتخاب کنید", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
            items(assignments) { a -> SimpleCard(Modifier.clickable { load(a) }) { Text("${a.optString("subject")} · ${classes[a.optString("classId")]?.optString("name", a.optString("classId"))}", fontWeight = FontWeight.Black); Text("ورود به دفتر نمره", color = Brand, fontSize = 12.sp) } }
        } else {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { period = "semester1"; selected?.let { load(it) } }, label = { Text("نیم‌سال اول") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (period == "semester1") Brand.copy(.12f) else Color.White))
                AssistChip(onClick = { period = "semester2"; selected?.let { load(it) } }, label = { Text("نیم‌سال دوم") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (period == "semester2") Brand.copy(.12f) else Color.White))
                TextButton(onClick = { selected = null; gradebook = null }) { Text("تغییر کلاس") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            gradebook?.let { GradebookEditor(it, selected!!, period) }
        }
    }
}

@Composable
private fun GradebookEditor(book: JSONObject, assignment: JSONObject, period: String) {
    val scope = rememberCoroutineScope(); val categories = book.optJSONObject("config")?.optJSONArray("categories")?.toObjects().orEmpty().filter { it.optString("source") != "exam" }
    val students = book.optJSONArray("students")?.toObjects().orEmpty(); val category = categories.firstOrNull()
    val scores = remember(book.toString()) { mutableStateMapOf<String, String>() }; val notes = remember(book.toString()) { mutableStateMapOf<String, String>() }; var message by remember { mutableStateOf("") }
    if (category == null) { EmptyState("برای این درس مؤلفه دستی دفتر نمره تعریف نشده است."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(students) { st ->
            val id = st.optString("id"); val current = st.optJSONObject("result")?.opt("score")?.toString()?.takeIf { it != "null" }.orEmpty()
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), RoundedCornerShape(18.dp), CardDefaults.cardColors(Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Text("${st.optString("firstName")} ${st.optString("lastName")}", fontWeight = FontWeight.Black)
                    Text("نمره مستمر محاسبه‌شده: ${current.ifBlank { "—" }}", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(scores[id].orEmpty(), { scores[id] = it }, Modifier.weight(.34f), label = { Text("از ۲۰") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(notes[id].orEmpty(), { notes[id] = it }, Modifier.weight(.66f), label = { Text("توضیح") }, singleLine = true)
                    }
                    TextButton(onClick = {
                        val score = scores[id]?.toDoubleOrNull(); if (score == null || score !in 0.0..20.0) { message = "نمره ${st.optString("firstName")} معتبر نیست."; return@TextButton }
                        scope.launch { try { ApiClient.post("/api/staff/gradebook/entry", JSONObject().put("studentId", id).put("classId", assignment.optString("classId")).put("subject", assignment.optString("subject")).put("categoryId", category.optString("id")).put("academicPeriod", period).put("score", score).put("maxScore", 20).put("note", notes[id].orEmpty())); message = "نمره ${st.optString("firstName")} ثبت شد ✓" } catch (e: Throwable) { message = e.message ?: "خطا" } }
                    }) { Text("ثبت نمره") }
                }
            }
        }
        if (message.isNotBlank()) item { Text(message, Modifier.padding(8.dp), color = if (message.contains("✓")) Success else MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ExamsScreen(workspace: JSONObject?, onNavigate: (Screen) -> Unit) {
    val exams = workspace?.optJSONArray("exams")?.toObjects().orEmpty()
    val attempts = workspace?.optJSONArray("attempts")?.toObjects().orEmpty()
    val submissions = workspace?.optJSONArray("submissions")?.toObjects().orEmpty()
    val classes = workspace?.optJSONArray("classes")?.toObjects().orEmpty().associateBy { it.optString("id") }
    var selectedId by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    val selected = exams.firstOrNull { it.optString("id") == selectedId }
    val visible = exams.filter {
        bankQuestionMatchesSearch(search, listOf(it.optString("title"), it.optString("subject"),
            classes[it.optString("classId")]?.optString("name").orEmpty()))
    }
    BackHandler(enabled = selected != null) { selectedId = "" }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (selected == null) "آزمون‌های من" else "جزئیات آزمون")
        if (selected == null) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), CardDefaults.cardColors(Color(0xFFE8F8FA))) {
                        Column(Modifier.padding(15.dp)) {
                            Text("مدیریت آزمون‌ها", fontWeight = FontWeight.Black, color = BrandDark, fontSize = 18.sp)
                            Text("فهرست آزمون‌ها، سؤال‌ها، برگه‌ها و دسترسی به طراحی آزمون", color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { onNavigate(Screen.ExamCreate) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(6.dp)); Text("ساخت آزمون از مخزن سؤال")
                            }
                        }
                    }
                }
                item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("جست‌وجو در آزمون‌ها") }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
                items(visible, key = { it.optString("id").ifBlank { it.toString() } }) { exam ->
                    val done = attempts.count { it.optString("examId") == exam.optString("id") } +
                        submissions.count { it.optString("examId") == exam.optString("id") }
                    SimpleCard(Modifier.clickable { selectedId = exam.optString("id") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(exam.optString("title", "آزمون"), Modifier.weight(1f), fontWeight = FontWeight.Black)
                            Icon(Icons.Default.ChevronLeft, contentDescription = "مشاهده جزئیات آزمون", tint = Brand)
                        }
                        Text("${exam.optString("subject")} · ${classes[exam.optString("classId")]?.optString("name", exam.optString("classId"))}", color = TextSecondary)
                        Text("${exam.optString("publicationState", exam.optString("status", "نامشخص"))} · $done پاسخ ثبت‌شده · ${exam.optJSONArray("questions")?.length() ?: 0} سؤال", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                if (visible.isEmpty()) item { EmptyState(if (exams.isEmpty()) "هنوز آزمونی ثبت نشده است." else "آزمونی با این عبارت پیدا نشد.") }
            }
        } else {
            val exam = selected
            val examId = exam.optString("id")
            val delivered = (attempts + submissions).filter { it.optString("examId") == examId }
            val questions = exam.optJSONArray("questions")?.toObjects().orEmpty()
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TextButton(onClick = { selectedId = "" }) { Icon(Icons.Default.ArrowForward, null); Text("بازگشت به آزمون‌ها") }
                    SimpleCard {
                        Text(exam.optString("title", "آزمون"), fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandDark)
                        Text("${exam.optString("subject")} · ${classes[exam.optString("classId")]?.optString("name", exam.optString("classId"))}", color = TextSecondary)
                        Text("وضعیت: ${exam.optString("publicationState", exam.optString("status", "نامشخص"))}", color = TextSecondary)
                        Text("${questions.size} سؤال · ${delivered.size} پاسخ/برگه ثبت‌شده", color = TextSecondary)
                        Text("بارم: ${exam.optDouble("targetScore", exam.optDouble("totalScore", 0.0))}", color = TextSecondary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onNavigate(Screen.Grading) }, Modifier.weight(1f)) { Text("تصحیح و نمرات") }
                        Button(onClick = { onNavigate(Screen.ExamCreate) }, Modifier.weight(1f)) { Text("آزمون جدید") }
                    }
                    Text("سؤال‌های این آزمون", fontWeight = FontWeight.Black, color = BrandDark)
                }
                items(questions) { q ->
                    SimpleCard {
                        Text(q.optString("text").ifBlank { q.optString("questionText", "سؤال") }, fontWeight = FontWeight.Bold)
                        val image = questionImageSource(q)
                        if (image.isNotBlank()) QuestionImagePreview(image)
                        val choices = q.optJSONArray("choices") ?: q.optJSONArray("options")
                        choices?.let { c -> for (i in 0 until c.length()) {
                            Text("${i + 1}) ${c.optString(i)}", fontSize = 12.sp, color = TextSecondary)
                        }}
                        Text("بارم: ${q.optDouble("score", 1.0)}", color = BrandDeep, fontSize = 12.sp)
                    }
                }
                if (questions.isEmpty()) item { EmptyState("سرور متن سؤال‌های این آزمون را در داده‌های فعلی برنگردانده است.") }
                item { Text("تغییر یا حذف آزمون موجود تنها پس از تأیید مسیر و مجوز سرور ممکن است؛ این صفحه هیچ داده‌ای را بدون اجازه تغییر نمی‌دهد.", color = TextSecondary, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun ScheduleScreen(dashboard: JSONObject?, onNavigate: (Screen) -> Unit) {
    val rows = dashboard?.optJSONArray("weekSchedule")?.toObjects().orEmpty()
    val days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("برنامه هفتگی")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            days.forEach { day ->
                val ds = rows.filter { it.optString("day") == day }
                if (ds.isNotEmpty()) {
                    item { Text(day, Modifier.padding(vertical = 8.dp), fontWeight = FontWeight.Black, color = BrandDark) }
                    items(ds) { entry ->
                        SimpleCard {
                            Text("${entry.optString("subject")} · ${entry.optString("className")}", fontWeight = FontWeight.Bold)
                            Text("${entry.optString("startTime")}–${entry.optString("endTime")}", color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onNavigate(Screen.Classroom) }) { Text("دفتر کلاسی") }
                                if (entry.optString("effectiveMode") in listOf("online", "hybrid")) {
                                    TextButton(onClick = { onNavigate(Screen.OnlineClass) }) { Text("کلاس آنلاین") }
                                }
                            }
                        }
                    }
                }
            }
            if (rows.isEmpty()) item { EmptyState("برنامه‌ای برای نمایش از سرور دریافت نشده است.") }
        }
    }
}

@Composable
private fun NotificationsScreen(dashboard: JSONObject?) {
    val rows = dashboard?.optJSONArray("notifications")?.toObjects().orEmpty()
    var openedIndex by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("اعلان‌ها")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(rows.size) { index ->
                val notice = rows[index]
                SimpleCard(Modifier.clickable { openedIndex = if (openedIndex == index) -1 else index }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsNone, null, tint = Brand)
                        Spacer(Modifier.width(8.dp))
                        Text(notice.optString("title", "اعلان"), Modifier.weight(1f), fontWeight = FontWeight.Black)
                        Icon(if (openedIndex == index) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "جزئیات اعلان")
                    }
                    if (openedIndex == index) {
                        Text(notice.optString("body", "متن بیشتری برای این اعلان ثبت نشده است."), color = TextPrimary)
                        Text(notice.optString("createdAt"), color = TextSecondary, fontSize = 11.sp)
                    } else {
                        Text(notice.optString("body"), color = TextSecondary, maxLines = 2, fontSize = 12.sp)
                    }
                }
            }
            if (rows.isEmpty()) item { EmptyState("اعلانی برای نمایش ندارید.") }
        }
    }
}

@Composable
private fun MessagesScreen(messageTarget: MessageTarget? = null) {
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var messages by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var targetWarning by remember { mutableStateOf("") }
    suspend fun reload() {
        val c = ApiClient.get("/api/academic-messages/p58/contacts")
        val t = ApiClient.get("/api/academic-messages/p58/threads")
        contacts = c.optJSONArray("contacts")?.toObjects().orEmpty()
        messages = t.optJSONArray("messages")?.toObjects().orEmpty()
    }
    LaunchedEffect(messageTarget) {
        loading = true
        try {
            reload()
            if (messageTarget != null) {
                selected = findContactForTarget(contacts, messageTarget)
                targetWarning = if (selected == null) "برای این دانش‌آموز در درس انتخاب‌شده مخاطب مجازی پیدا نشد؛ از فهرست مخاطبان انتخاب کنید." else ""
            }
        } catch (e: Throwable) { error = e.message ?: "خطا" }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (selected == null) "پیام‌ها و ارتباط آموزشی" else selected!!.optString("name", "گفتگو"))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand)
        if (error.isNotBlank()) Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        if (targetWarning.isNotBlank()) Text(targetWarning, Modifier.padding(12.dp), color = Color(0xFFB7791F))
        if (selected == null) {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item {
                    Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp), RoundedCornerShape(18.dp), Color(0xFFE8F8FA)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Forum, null, tint = Brand); Spacer(Modifier.width(8.dp)); Text("ارتباط سریع و امن با دانش‌آموزان", color = BrandDark, fontWeight = FontWeight.Bold) }
                    }
                }
                items(contacts.distinctBy { "${it.optString("studentId")}|${it.optString("subject")}" }) { c ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { selected = c }, RoundedCornerShape(19.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(44.dp), CircleShape, Brand.copy(.1f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Brand) } }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(c.optString("name", "دانش‌آموز"), fontWeight = FontWeight.Black); Text(c.optString("subject"), color = TextSecondary, fontSize = 11.sp) }
                            Surface(Modifier.size(36.dp), CircleShape, Color(0xFFE8F8FA)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ChatBubbleOutline, null, tint = Brand, modifier = Modifier.size(19.dp)) } }
                        }
                    }
                }
                if (contacts.isEmpty() && !loading) item { EmptyState("مخاطبی در محدوده تدریس شما نیست.") }
            }
        } else {
            val c = selected!!; val sid = c.optString("studentId"); val subject = c.optString("subject")
            val thread = messages.filter { it.optString("studentId") == sid && it.optString("subject") == subject }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                items(thread) { m ->
                    val mine = m.optString("fromRole") == "teacher"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
                        Surface(color = if (mine) Brand else Color.White, shape = RoundedCornerShape(17.dp), shadowElevation = 1.dp) {
                            Column(Modifier.padding(12.dp).widthIn(max = 280.dp)) { Text(m.optString("message"), color = if (mine) Color.White else TextPrimary); Text(m.optString("at"), color = if (mine) Color.White.copy(.7f) else TextSecondary, fontSize = 9.sp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it.take(2000) }, Modifier.weight(1f), placeholder = { Text("پیام…") }, maxLines = 4, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand))
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    enabled = draft.isNotBlank() && !sending,
                    onClick = {
                        scope.launch {
                            sending = true
                            error = ""
                            try {
                                ApiClient.post("/api/academic-messages/p58/send", JSONObject()
                                    .put("studentId", sid).put("subject", subject).put("message", draft.trim()))
                                draft = ""
                                reload()
                            } catch (e: Throwable) { error = e.message ?: "ارسال پیام ناموفق بود." }
                            finally { sending = false }
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brand)
                ) { Icon(Icons.Default.Send, contentDescription = "ارسال پیام") }
            }
            TextButton(onClick = { selected = null }, Modifier.fillMaxWidth()) { Text("بازگشت به مخاطبان", color = Brand) }
        }
    }
}

@Composable
private fun MoreScreen(onNavigate: (Screen) -> Unit, onLogout: () -> Unit) {
    val groups = listOf(
        "آموزش و کلاس" to listOf(
            MenuFeature("کتاب‌ها و کلاس‌های من", Icons.Default.School, Screen.MyTeaching, "تخصیص‌های تدریس و دانش‌آموزان"),
            MenuFeature("آموزش و فصل‌ها", Icons.Default.AutoStories, Screen.Curriculum, "کتاب‌ها و فصل‌های درسی"),
            MenuFeature("دانش‌آموزان من", Icons.Default.Groups, Screen.Students, "عکس، پروفایل و وضعیت آموزشی"),
            MenuFeature("دفتر کلاسی", Icons.Default.Class, Screen.Classroom, "ثبت جلسه، تدریس و تکلیف"),
            MenuFeature("برنامه هفتگی", Icons.Default.CalendarMonth, Screen.Schedule, "زنگ‌ها و برنامه تدریس"),
            MenuFeature("کلاس آنلاین", Icons.Default.VideoCall, Screen.OnlineClass, "شروع نشست زنگ آنلاین")
        ),
        "حضور و ارزیابی" to listOf(
            MenuFeature("حضور و غیاب", Icons.Default.FactCheck, Screen.Attendance, "ثبت روزانه حضور"),
            MenuFeature("حضور و انضباط", Icons.Default.AccessTime, Screen.AttendanceDiscipline, "غیبت، تأخیر و پیگیری"),
            MenuFeature("دفتر نمره", Icons.Default.AutoGraph, Screen.Grades, "ثبت و مدیریت نمرات"),
            MenuFeature("مخزن سؤال", Icons.Default.Quiz, Screen.QuestionBank, "ثبت و مدیریت سؤال‌ها"),
            MenuFeature("بانک سؤال و آزمون مستقل", Icons.Default.Psychology, Screen.IndependentExamCenter, "انتشار آزمون تمرینی و مشاهده نتایج"),
            MenuFeature("طراحی آزمون", Icons.Default.PostAdd, Screen.ExamCreate, "ساخت آزمون مستمر و پایانی"),
            MenuFeature("آزمون‌ها", Icons.Default.AssignmentTurnedIn, Screen.Exams, "آزمون‌های واقعی سرور"),
            MenuFeature("تصحیح و نمرات", Icons.Default.Grading, Screen.Grading, "برگه‌های تحویلی و وضعیت تصحیح")
        ),
        "تکلیف و محتوای آموزشی" to listOf(
            MenuFeature("مرکز تکالیف", Icons.Default.Assignment, Screen.Homework, "ساخت و انتشار تکلیف"),
            MenuFeature("بانک تمرین", Icons.Default.Layers, Screen.Practice, "تمرین و محتوای آموزشی"),
            MenuFeature("کتابخانه دیجیتال", Icons.Default.MenuBook, Screen.Library, "منابع و فایل‌های آموزشی"),
            MenuFeature("برنامه آموزشی", Icons.Default.EventNote, Screen.LearningPlanner, "رویدادها و تقویم آموزشی")
        ),
        "ارتباط با دانش‌آموز و خانواده" to listOf(
            MenuFeature("پیام مستقیم دانش‌آموزان", Icons.Default.ChatBubble, Screen.Messages, "گفت‌وگوی آموزشی مستقیم"),
            MenuFeature("ارتباطات و پیام‌ها", Icons.Default.Campaign, Screen.Communications, "صندوق پیام و اطلاعیه‌ها"),
            MenuFeature("ملاقات اولیا", Icons.Default.GroupAdd, Screen.GuardianMeetings, "دعوت و پاسخ خانواده‌ها")
        ),
        "گزارش و پیگیری" to listOf(
            MenuFeature("گزارش‌ها", Icons.Default.Insights, Screen.Reports, "گزارش مستمر دانش‌آموز"),
            MenuFeature("تحلیل دانش‌آموز", Icons.Default.Analytics, Screen.StudentAnalysis, "پرونده تحلیلی آموزشی"),
            MenuFeature("مرکز اقدام", Icons.Default.TrackChanges, Screen.ActionCenter, "کارها و پرونده‌های پیگیری"),
            MenuFeature("اعلان‌ها", Icons.Default.NotificationsNone, Screen.Notifications, "اعلان‌های حساب")
        ),
        "حساب و امنیت" to listOf(
            MenuFeature("حساب و امنیت", Icons.Default.Security, Screen.Account, "نشست‌ها و امنیت ورود")
        )
    )

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("همه امکانات")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    RoundedCornerShape(22.dp),
                    CardDefaults.cardColors(Color(0xFFE6F9F8))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(54.dp), RoundedCornerShape(17.dp), Color.White) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DashboardCustomize, null, tint = Brand, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("ابزارهای کامل معلم", fontWeight = FontWeight.Black, color = BrandDark, fontSize = 17.sp)
                            Text("همه بخش‌های پنل معلم، مرتب‌شده برای استفاده سریع", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            groups.forEach { (groupTitle, features) ->
                item {
                    Text(
                        groupTitle,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                        color = BrandDark,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
                items(features) { f ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onNavigate(f.screen) },
                        RoundedCornerShape(19.dp),
                        CardDefaults.cardColors(Color.White),
                        CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(44.dp), RoundedCornerShape(14.dp), Brand.copy(.1f)) {
                                Box(contentAlignment = Alignment.Center) { Icon(f.icon, null, tint = Brand) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.title, fontWeight = FontWeight.Black, color = TextPrimary)
                                Text(f.subtitle, color = TextSecondary, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronLeft, null, tint = Brand)
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Icon(Icons.Default.Logout, null)
                    Spacer(Modifier.width(6.dp))
                    Text("خروج از حساب")
                }
            }
        }
    }
}

@Composable
private fun SimpleApiListScreen(title: String, path: String, arrayKey: String) {
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val isLibrary = title.contains("کتابخانه")
    LaunchedEffect(path) { try { rows = ApiClient.get(path).optJSONArray(arrayKey)?.toObjects().orEmpty() } catch (e: Throwable) { error = e.message ?: "خطا" } finally { loading = false } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand)
        if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp), RoundedCornerShape(20.dp), if (isLibrary) Color(0xFFE4F6FF) else Color(0xFFE8F9F4)) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(50.dp), RoundedCornerShape(16.dp), Color.White) { Box(contentAlignment = Alignment.Center) { Icon(if (isLibrary) Icons.Default.MenuBook else Icons.Default.Layers, null, tint = if (isLibrary) Sky else Brand, modifier = Modifier.size(26.dp)) } }
                        Spacer(Modifier.width(10.dp)); Column { Text(if (isLibrary) "منابع آموزشی مدرسه" else "تمرین‌های آموزشی", fontWeight = FontWeight.Black, color = TextPrimary); Text(if (isLibrary) "دسترسی سریع به فایل‌ها و محتوا" else "تمرین‌های مرتبط با درس‌های شما", color = TextSecondary, fontSize = 11.sp) }
                    }
                }
            }
            items(rows) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), RoundedCornerShape(19.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(42.dp), RoundedCornerShape(13.dp), (if (isLibrary) Sky else Brand).copy(.10f)) { Box(contentAlignment = Alignment.Center) { Icon(if (isLibrary) Icons.Default.Description else Icons.Default.Assignment, null, tint = if (isLibrary) Sky else Brand) } }
                        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(r.optString("title", r.optString("subject", "مورد")), fontWeight = FontWeight.Black); val sub = listOf(r.optString("subject"), r.optString("teacherName"), r.optString("createdAt")).filter { it.isNotBlank() }.joinToString(" · "); if (sub.isNotBlank()) Text(sub, color = TextSecondary, fontSize = 11.sp) }; Icon(Icons.Default.ChevronLeft, null, tint = TextSecondary)
                    }
                }
            }
            if (rows.isEmpty() && !loading && error.isBlank()) item { EmptyState("موردی برای نمایش وجود ندارد.") }
        }
    }
}

@Composable
private fun AccountScreen(onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<JSONObject?>(null) }
    var sessions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var selectedSession by remember { mutableStateOf<JSONObject?>(null) }
    var changePasswordOpen by remember { mutableStateOf(false) }
    suspend fun reload() {
        loading = true; error = ""
        try {
            me = ApiClient.get("/api/staff/me").optJSONObject("user")
            sessions = ApiClient.get("/api/staff/sessions").optJSONArray("sessions")?.toObjects().orEmpty()
        } catch (e: Throwable) { error = e.message ?: "دریافت اطلاعات نشست‌ها ناموفق بود." }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { reload() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("حساب و امنیت")
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                SimpleCard {
                    Text("${me?.optString("firstName").orEmpty()} ${me?.optString("lastName").orEmpty()}", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("نقش: ${me?.optString("role", "teacher")}", color = TextSecondary)
                    OutlinedButton(onClick = { scope.launch { reload() } }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, null); Text("تازه‌سازی اطلاعات")
                    }
                }
                Text("نشست‌های حساب", Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Black)
            }
            items(sessions) { session ->
                SimpleCard(Modifier.clickable { selectedSession = session }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Devices, null, tint = Brand)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.optString("deviceLabel", session.optString("userAgent", "دستگاه")), fontWeight = FontWeight.Bold)
                            Text(session.optString("lastSeenAt", session.optString("createdAt")), color = TextSecondary, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.Info, contentDescription = "جزئیات نشست")
                    }
                }
            }
            if (sessions.isEmpty() && !loading && error.isEmpty()) item { EmptyState("نشست فعالی از سرور دریافت نشد.") }
            if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
            item {
                Text("برای جلوگیری از تغییر ناخواسته امنیت حساب، لغو نشست‌های دیگر فقط زمانی ارائه می‌شود که API و مجوز آن تأیید شده باشند.", fontSize = 11.sp, color = TextSecondary)
                OutlinedButton(onClick = { changePasswordOpen = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Icon(Icons.Default.LockReset, null); Spacer(Modifier.width(6.dp)); Text("تغییر رمز عبور")
                }
                Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))) { Text("خروج از حساب") }
            }
        }
    }
    if (changePasswordOpen) AccountChangePasswordDialog(
        onDismiss = { changePasswordOpen = false },
        onPasswordChanged = { changePasswordOpen = false; onLogout() }
    )
    selectedSession?.let { session ->
        AlertDialog(onDismissRequest = { selectedSession = null }, title = { Text("جزئیات نشست") },
            text = {
                Column {
                    Text(session.optString("deviceLabel", "دستگاه"), fontWeight = FontWeight.Bold)
                    Text("شروع: ${session.optString("createdAt", "نامشخص")}")
                    Text("آخرین فعالیت: ${session.optString("lastSeenAt", "نامشخص")}")
                }
            }, confirmButton = { TextButton(onClick = { selectedSession = null }) { Text("بستن") } })
    }
}

@Composable
private fun AccountChangePasswordDialog(onDismiss: () -> Unit, onPasswordChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("تغییر رمز عبور") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(oldPassword, { oldPassword = it }, Modifier.fillMaxWidth(),
                    label = { Text("رمز فعلی") }, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(newPassword, { newPassword = it }, Modifier.fillMaxWidth(),
                    label = { Text("رمز جدید (حداقل ۸ نویسه)") }, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(repeatPassword, { repeatPassword = it }, Modifier.fillMaxWidth(),
                    label = { Text("تکرار رمز جدید") }, visualTransformation = PasswordVisualTransformation())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Text("پس از تغییر رمز برای امنیت بیشتر دوباره وارد حساب خواهید شد.", color = TextSecondary, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(enabled = !saving && oldPassword.isNotBlank() && newPassword.length >= 8 && newPassword == repeatPassword,
                onClick = {
                    scope.launch {
                        saving = true; error = ""
                        try {
                            ApiClient.post("/api/staff/change-password", JSONObject()
                                .put("currentPassword", oldPassword).put("newPassword", newPassword))
                            oldPassword = ""; newPassword = ""; repeatPassword = ""
                            onPasswordChanged()
                        } catch (e: Throwable) { error = e.message ?: "تغییر رمز ناموفق بود." }
                        finally { saving = false }
                    }
                }) { Text(if (saving) "در حال ذخیره…" else "تغییر رمز و ورود مجدد") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable private fun ScreenHeader(title: String) {
    TeacherCompactHeader(title, "مدرسه‌یار | پنل معلم")
}
@Composable private fun SimpleCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    TeacherPanel(modifier = modifier, content = content)
}
@Composable private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    TeacherPanel(Modifier.padding(16.dp)) {
        TeacherSectionTitle(title, icon = icon)
        Spacer(Modifier.height(12.dp))
        content()
    }
}
@Composable private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary, textAlign = TextAlign.Center)
    }
}

private fun JSONArray.toObjects(): List<JSONObject> = buildList { for (i in 0 until length()) optJSONObject(i)?.let { add(it) } }
