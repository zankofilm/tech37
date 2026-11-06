package ir.madreseyar.teacher

import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Resolve old /student/media/... references under the app's cPanel /app/ installation. */
internal fun resolveQuestionImageUrl(base: String, source: String): URL? = runCatching {
    val root = URL(base.trimEnd('/') + "/")
    require(root.protocol.equals("https", true) && root.userInfo == null && root.query == null && root.ref == null)
    val raw = source.trim()
    require(raw.isNotBlank() && !raw.startsWith("data:", true) && !raw.contains('\\') && !raw.contains('#'))

    val supplied = if (raw.startsWith("https://", true) || raw.startsWith("http://", true)) URL(raw) else null
    if (supplied != null) {
        require(supplied.protocol.equals("https", true) && supplied.host.equals(root.host, true) && supplied.port == root.port)
    }
    val rawPath = supplied?.path ?: raw.substringBefore('?')
    val query = supplied?.query ?: raw.substringAfter('?', "").ifBlank { null }
    val decodedPath = URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name())
    require(rawPath.isNotBlank() && decodedPath.split('/').none { it == "." || it == ".." })

    val basePath = root.path.trimEnd('/')
    val normalizedPath = when {
        basePath.isNotEmpty() && (rawPath == basePath || rawPath.startsWith("$basePath/")) -> rawPath
        rawPath.startsWith("/") -> basePath + rawPath
        else -> "$basePath/$rawPath"
    }.ifBlank { "/" }
    val resolved = URL(root.protocol, root.host, root.port, normalizedPath + (query?.let { "?$it" } ?: ""))
    require(resolved.protocol.equals("https", true) && resolved.host.equals(root.host, true) && resolved.port == root.port)
    require(basePath.isEmpty() || resolved.path == basePath || resolved.path.startsWith("$basePath/"))
    resolved
}.getOrNull()
