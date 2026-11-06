package ir.madreseyar.teacher

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection

private const val MAX_UPLOAD_IMAGE_BYTES = 900 * 1024
private const val MAX_RENDER_IMAGE_BYTES = 3 * 1024 * 1024

/** Reads image references used by both current and legacy question-bank API payloads. */
internal fun questionImageSource(question: JSONObject): String {
    val keys = listOf(
        "imageDataUrl", "imageUrl", "image_url", "imagePath", "image_path",
        "questionImage", "question_image", "mediaUrl", "media_url", "image", "picture", "photo"
    )
    for (key in keys) {
        val value = question.opt(key) ?: continue
        val direct = value as? String
        if (!direct.isNullOrBlank()) return direct.trim()
        val nested = value as? JSONObject ?: continue
        for (nestedKey in listOf("url", "path", "src", "dataUrl", "data_url", "fileUrl")) {
            val candidate = nested.optString(nestedKey).trim()
            if (candidate.isNotBlank() && candidate != "null") return candidate
        }
    }
    return ""
}

/** Decodes inline images (including older jpg aliases) with downsampling. */
internal fun decodeSafeImageDataUrl(value: String): ImageBitmap? = runCatching {
    val comma = value.indexOf(',')
    if (comma < 0 || comma > 80 || value.length - comma > (MAX_RENDER_IMAGE_BYTES * 4 / 3 + 1024)) return@runCatching null
    val header = value.substring(0, comma).lowercase()
    if (header !in setOf(
            "data:image/jpeg;base64", "data:image/jpg;base64", "data:image/pjpeg;base64",
            "data:image/png;base64", "data:image/webp;base64", "data:image/gif;base64"
        )) return@runCatching null
    val bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT)
    decodeImageBytes(bytes)
}.getOrNull()

private fun decodeImageBytes(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty() || bytes.size > MAX_RENDER_IMAGE_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth < 1 || bounds.outHeight < 1) return null
    var sample = 1
    while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return bitmap.asImageBitmap()
}

/** Download only same-origin HTTPS media, with auth and a strict response-size limit. */
private fun loadQuestionImage(source: String): ImageBitmap? {
    val url = resolveQuestionImageUrl(BuildConfig.API_BASE_URL, source) ?: return null
    val connection = (url.openConnection() as? HttpURLConnection) ?: return null
    return try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.instanceFollowRedirects = false
        connection.useCaches = true
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8")
        connection.setRequestProperty("User-Agent", "MadreseyarTeacherAndroid/${BuildConfig.VERSION_NAME}")
        ApiClient.token.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        val status = connection.responseCode
        if (status !in 200..299) return null
        val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (contentType.isNotBlank() && !contentType.startsWith("image/")) return null
        if (connection.contentLengthLong > MAX_RENDER_IMAGE_BYTES) return null
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_RENDER_IMAGE_BYTES) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        decodeImageBytes(bytes)
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

/** Keeps image uploads within the host JSON API payload budget. */
internal fun encodeQuestionImage(context: Context, uri: Uri): String {
    val type = context.contentResolver.getType(uri)?.lowercase()
    require(type in setOf("image/jpeg", "image/png", "image/webp")) { "فرمت تصویر باید JPG، PNG یا WebP باشد." }
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_UPLOAD_IMAGE_BYTES) throw IOException("حجم تصویر باید کمتر از ۹۰۰ کیلوبایت باشد.")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: throw IOException("فایل انتخاب‌شده خوانده نشد.")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "فایل انتخاب‌شده تصویر معتبر نیست." }
    return "data:$type;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
}

@Composable
internal fun QuestionImagePreview(dataUrl: String, modifier: Modifier = Modifier) {
    val source = dataUrl.trim()
    var bitmap by remember(source) { mutableStateOf(decodeSafeImageDataUrl(source)) }
    var loading by remember(source) { mutableStateOf(bitmap == null && !source.startsWith("data:", true)) }
    var failed by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source) {
        if (bitmap == null && !source.startsWith("data:", true)) {
            loading = true
            bitmap = withContext(Dispatchers.IO) { loadQuestionImage(source) }
            failed = bitmap == null
            loading = false
        } else if (bitmap == null) {
            failed = true
        }
    }

    when {
        bitmap != null -> Image(
            bitmap = bitmap!!,
            contentDescription = "تصویر سؤال",
            modifier = modifier.fillMaxWidth().heightIn(max = 220.dp),
            contentScale = ContentScale.Fit
        )
        loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        }
        failed -> Text("تصویر بارگذاری نشد؛ اتصال تصویر مخزن را بررسی کنید.", modifier = modifier, color = MaterialTheme.colorScheme.error)
    }
}
