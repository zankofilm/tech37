package ir.madreseyar.teacher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.io.InputStreamReader
import java.net.HttpURLConnection

class ApiException(
    val status: Int,
    override val message: String,
    val code: String = "",
    val payload: JSONObject? = null,
) : Exception(message)

object ApiClient {
    private const val MAX_DOWNLOAD_BYTES = 32 * 1024 * 1024
    @Volatile var token: String = ""

    data class StateEnvelope(val updatedAt: String, val db: JSONObject)
    data class BinaryResponse(val bytes: ByteArray, val contentType: String?, val contentDisposition: String?)

    suspend fun get(path: String, auth: Boolean = true): JSONObject = request(path, "GET", null, auth)
    suspend fun post(path: String, body: JSONObject = JSONObject(), auth: Boolean = true): JSONObject = request(path, "POST", body, auth)
    suspend fun patch(path: String, body: JSONObject = JSONObject(), auth: Boolean = true): JSONObject = request(path, "PATCH", body, auth)
    suspend fun delete(path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject = request(path, "DELETE", body, auth)

    /** Revoke a captured token without restoring a logged-out session to the global client. */
    suspend fun revokeSession(previousToken: String) {
        if (previousToken.isNotBlank()) request("/api/staff/logout", "POST", JSONObject(), auth = false, explicitToken = previousToken)
    }

    suspend fun getState(): StateEnvelope {
        val raw = get("/api/state")
        val db = raw.optJSONObject("db") ?: throw IllegalStateException("اطلاعات معلم از سرور دریافت نشد.")
        return StateEnvelope(raw.optString("updatedAt"), db)
    }

    suspend fun saveState(snapshot: StateEnvelope): StateEnvelope {
        val raw = post(
            "/api/state",
            JSONObject().put("baseUpdatedAt", snapshot.updatedAt).put("db", snapshot.db)
        )
        val db = raw.optJSONObject("db") ?: throw IllegalStateException("پاسخ ذخیره سامانه ناقص است.")
        return StateEnvelope(raw.optString("updatedAt"), db)
    }

    suspend fun download(path: String, auth: Boolean = true): BinaryResponse = withContext(Dispatchers.IO) {
        try {
            val conn = open(path, "GET", auth).apply {
                readTimeout = 20_000
                setRequestProperty("Accept", "*/*")
            }
            try {
                val status = conn.responseCode
                if (status !in 200..299) throw apiError(status, conn)
                if (conn.contentLengthLong > MAX_DOWNLOAD_BYTES) {
                    throw IOException("حجم فایل دریافتی بیش از ۳۲ مگابایت است.")
                }
                val bytes = conn.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val block = ByteArray(8192)
                    while (true) {
                        val read = input.read(block)
                        if (read < 0) break
                        if (output.size() + read > MAX_DOWNLOAD_BYTES) {
                            throw IOException("حجم فایل دریافتی بیش از ۳۲ مگابایت است.")
                        }
                        output.write(block, 0, read)
                    }
                    output.toByteArray()
                }
                BinaryResponse(bytes, conn.contentType, conn.getHeaderField("Content-Disposition"))
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            throw friendlyNetworkError(e)
        }
    }

    private fun open(path: String, method: String, auth: Boolean, explicitToken: String? = null): HttpURLConnection {
        val url = ApiPath.resolve(BuildConfig.API_BASE_URL, path)
        return (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false // Never forward a Bearer token to a redirect target.
            requestMethod = method
            connectTimeout = 6_000
            readTimeout = 12_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MadreseyarTeacherAndroid/${BuildConfig.VERSION_NAME}")
            val credential = explicitToken ?: if (auth) token else ""
            if (credential.isNotBlank()) setRequestProperty("Authorization", "Bearer $credential")
        }
    }

    private suspend fun request(
        path: String,
        method: String,
        body: JSONObject?,
        auth: Boolean,
        explicitToken: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            val conn = open(path, method, auth, explicitToken).apply {
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.let { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
                }.orEmpty()
                val json = runCatching { if (text.isBlank()) JSONObject() else JSONObject(text) }
                    .getOrElse { JSONObject().put("error", text.ifBlank { "HTTP $status" }) }
                if (status !in 200..299) {
                    var message = json.optString("error", "خطای $status")
                    if (message == "مسیر امنیت کارکنان پیدا نشد.") {
                        message = "این مسیر در نسخه فعلی هاست وجود ندارد."
                    }
                    throw ApiException(status, message, json.optString("code"), json)
                }
                json
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            throw friendlyNetworkError(e)
        }
    }

    private fun friendlyNetworkError(e: IOException): IOException = when (e) {
        is SocketTimeoutException -> IOException("مهلت اتصال به سرور تمام شد. اینترنت را بررسی و دوباره تلاش کنید.", e)
        is UnknownHostException -> IOException("اتصال به سرور مدرسه برقرار نشد. اینترنت یا آدرس سرور را بررسی کنید.", e)
        is SSLException -> IOException("اتصال امن به سرور برقرار نشد. گواهی HTTPS سرور را بررسی کنید.", e)
        else -> e
    }

    private fun apiError(status: Int, conn: HttpURLConnection): ApiException {
        val text = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(text) }.getOrElse { JSONObject().put("error", text.ifBlank { "HTTP $status" }) }
        return ApiException(status, json.optString("error", "خطای $status"), json.optString("code"), json)
    }
}
