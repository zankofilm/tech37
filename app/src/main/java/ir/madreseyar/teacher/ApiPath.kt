package ir.madreseyar.teacher

import java.net.URL

/** Guards the credential-bearing API client from absolute, cross-origin or traversing URLs. */
internal object ApiPath {
    fun resolve(base: String, path: String): URL {
        require(path.startsWith("/api/") && !path.contains('\\') && !path.contains('#') &&
            !path.contains("://") && path.substringBefore('?').split('/').none { it == "." || it == ".." }) {
            "مسیر درخواست API معتبر نیست."
        }
        val root = URL(base.trimEnd('/') + "/")
        require(root.protocol.equals("https", true) && root.userInfo == null && root.query == null && root.ref == null) {
            "آدرس امن سرور معتبر نیست."
        }
        val url = URL(base.trimEnd('/') + path)
        require(url.protocol == root.protocol && url.host.equals(root.host, true) && url.port == root.port &&
            url.path.startsWith(root.path)) { "آدرس درخواست خارج از سرور مدرسه است." }
        return url
    }
}

