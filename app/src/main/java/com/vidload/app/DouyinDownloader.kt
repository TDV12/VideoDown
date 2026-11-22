package com.vidload.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tải Douyin trực tiếp (không qua yt-dlp):
 *  1) Đọc trang chia sẻ iesdouyin.com (video + bài đăng ảnh)
 *  2) downloadDirect: tải từ link video do WebView bắt được (xem CaptureScreen)
 */
object DouyinDownloader {
    private const val MOBILE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

    private val idRegex = Regex("/(video|note|slides)/(\\d+)")
    private val modalRegex = Regex("modal_id=(\\d+)")

    fun isDouyin(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host.endsWith("douyin.com")
    }

    /** Trả về địa chỉ trang video trên web để WebView mở và bắt link video. */
    suspend fun videoPageUrl(url: String): String = withContext(Dispatchers.IO) {
        val (type, id) = extractIdType(url)
            ?: extractIdType(resolveFinalUrl(url))
            ?: error("Không nhận ra ID video trong link Douyin")
        if (type != "video") error("Đây là bài đăng ảnh, cách này chỉ dùng cho video")
        "https://www.douyin.com/video/$id"
    }

    /** Tải từ link video đã bắt được bằng WebView. */
    suspend fun downloadDirect(
        ctx: Context,
        videoUrl: String,
        cookies: String,
        onProgress: (Float, String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val u = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
        val cookie = if (cookies.isEmpty()) null else cookies
        val tmp = File(ctx.cacheDir, "dy/${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val f = File(tmp, "video.bin")
            fetchToFile(u, f, ua = CookieStore.UA, cookie = cookie) { p ->
                onProgress(p, "Đang tải video Douyin... ${p.toInt()}%")
            }
            if (f.length() < 10_000) error("File tải về quá nhỏ, có thể bị chặn")
            Downloader.saveToGallery(ctx, f, "douyin_${System.currentTimeMillis()}.mp4")
        } finally {
            tmp.deleteRecursively()
        }
    }

    suspend fun download(
        ctx: Context,
        url: String,
        onProgress: (Float, String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        onProgress(0f, "Đang đọc thông tin Douyin...")

        var finalUrl: String? = null
        var idType = extractIdType(url)
        if (idType == null) {
            finalUrl = resolveFinalUrl(url)
            idType = extractIdType(finalUrl)
        }
        val (type, id) = idType ?: error("Không nhận ra ID video trong link Douyin")

        // Thử trang chia sẻ đầy đủ tham số (share_sign...) trước, rồi mới thử trang trơn
        val candidates = mutableListOf<String>()
        if (finalUrl != null && finalUrl.contains("iesdouyin.com")) candidates.add(finalUrl)
        candidates.add("https://www.iesdouyin.com/share/$type/$id/")

        var found: JSONObject? = null
        val errors = mutableListOf<String>()
        for (c in candidates) {
            try {
                found = findItem(parseRouterData(fetchText(c)))
                break
            } catch (e: Exception) {
                errors.add(e.message ?: e.toString())
            }
        }
        val item = found ?: error(errors.joinToString("\n"))
        val title = safeName(item.optString("desc"), id)

        val tmp = File(ctx.cacheDir, "dy/${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val images = item.optJSONArray("images")
            if (images != null && images.length() > 0) {
                downloadImages(ctx, images, title, tmp, onProgress)
            } else {
                downloadVideo(ctx, item, title, tmp, onProgress)
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    // ---------- Phân tích link & trang ----------

    private fun extractIdType(url: String): Pair<String, String>? {
        idRegex.find(url)?.let { return it.groupValues[1] to it.groupValues[2] }
        modalRegex.find(url)?.let { return "video" to it.groupValues[1] }
        return null
    }

    private fun resolveFinalUrl(url: String): String {
        val conn = connect(url, allowError = true)
        val finalUrl = conn.url.toString()
        conn.disconnect()
        return finalUrl
    }

    private fun fetchText(url: String): String {
        val conn = connect(url)
        return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            .also { conn.disconnect() }
    }

    private fun parseRouterData(html: String): JSONObject {
        val marker = "window._ROUTER_DATA = "
        val i = html.indexOf(marker)
        if (i < 0) {
            error(
                "Trang chia sẻ không có dữ liệu (độ dài ${html.length}). " +
                    "Douyin có thể đã đổi cấu trúc hoặc chặn yêu cầu"
            )
        }
        val start = i + marker.length
        val end = html.indexOf("</script>", start).let { if (it < 0) html.length else it }
        val raw = html.substring(start, end).trim().trimEnd(';')
        return JSONObject(raw)
    }

    private fun findItem(root: JSONObject): JSONObject {
        val loader = root.optJSONObject("loaderData") ?: error("Dữ liệu trang không hợp lệ")
        val keys = loader.keys()
        val allKeys = mutableListOf<String>()
        var diag = ""
        while (keys.hasNext()) {
            val k = keys.next()
            allKeys.add(k)
            val page = loader.optJSONObject(k) ?: continue
            val res = page.optJSONObject("videoInfoRes") ?: continue
            val list = res.optJSONArray("item_list")
            if (list != null && list.length() > 0) return list.getJSONObject(0)
            diag += " [videoInfoRes: ${res.toString().take(250)}]"
        }
        error("Không có dữ liệu video. loaderData=$allKeys$diag")
    }

    // ---------- Tải video / ảnh ----------

    private fun downloadVideo(
        ctx: Context,
        item: JSONObject,
        title: String,
        tmp: File,
        onProgress: (Float, String) -> Unit
    ): String {
        val raw = item.optJSONObject("video")
            ?.optJSONObject("play_addr")
            ?.optJSONArray("url_list")
            ?.optString(0)
        if (raw.isNullOrEmpty()) error("Không tìm thấy link video trong dữ liệu Douyin")

        // playwm = có watermark, play = không watermark
        val base = raw.replace("/playwm/", "/play/")
        val candidates = listOf(base.replace("ratio=720p", "ratio=1080p"), base).distinct()

        var lastErr: Exception? = null
        for (c in candidates) {
            try {
                val f = File(tmp, "video.bin")
                fetchToFile(c, f) { p -> onProgress(p, "Đang tải video Douyin... ${p.toInt()}%") }
                if (f.length() < 10_000) error("File tải về quá nhỏ, có thể bị chặn")
                return Downloader.saveToGallery(ctx, f, "$title.mp4")
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: error("Không tải được video")
    }

    private fun downloadImages(
        ctx: Context,
        images: JSONArray,
        title: String,
        tmp: File,
        onProgress: (Float, String) -> Unit
    ): String {
        val n = images.length()
        var saved = 0
        for (i in 0 until n) {
            val u = images.getJSONObject(i).optJSONArray("url_list")?.optString(0)
            if (u.isNullOrEmpty()) continue
            val f = File(tmp, "img$i.bin")
            val ct = fetchToFile(u, f) { }
            val ext = when {
                ct.contains("webp") -> "webp"
                ct.contains("png") -> "png"
                ct.contains("heic") -> "heic"
                else -> "jpg"
            }
            Downloader.saveToGallery(ctx, f, "${title}_${"%02d".format(i + 1)}.$ext")
            saved++
            onProgress((i + 1) * 100f / n, "Đã tải ảnh ${i + 1}/$n")
        }
        if (saved == 0) error("Không tải được ảnh nào")
        return "Pictures/VideoLoad ($saved ảnh)"
    }

    /** Tải 1 URL về file, trả về Content-Type. */
    private fun fetchToFile(
        url: String,
        dest: File,
        ua: String = MOBILE_UA,
        cookie: String? = null,
        onPercent: (Float) -> Unit
    ): String {
        val conn = connect(url, referer = "https://www.douyin.com/", ua = ua, cookie = cookie)
        val ct = conn.contentType ?: ""
        if (ct.startsWith("text/") || ct.contains("json")) {
            conn.disconnect()
            error("Server trả về dữ liệu không phải file ($ct)")
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0) {
                        val p = (read * 100 / total).toInt()
                        if (p != last) {
                            last = p
                            onPercent(p.toFloat())
                        }
                    }
                }
            }
        }
        conn.disconnect()
        return ct
    }

    // ---------- Mạng ----------

    /** Kết nối, tự theo dõi chuyển hướng (kể cả http -> https). */
    private fun connect(
        url: String,
        referer: String? = null,
        allowError: Boolean = false,
        ua: String = MOBILE_UA,
        cookie: String? = null
    ): HttpURLConnection {
        var current = url
        for (hop in 0 until 10) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", ua)
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            if (referer != null) conn.setRequestProperty("Referer", referer)
            if (cookie != null) conn.setRequestProperty("Cookie", cookie)

            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrEmpty()) error("Chuyển hướng không hợp lệ (HTTP $code)")
                var next = URL(URL(current), loc).toString()
                if (next.startsWith("http://")) next = "https://" + next.removePrefix("http://")
                current = next
                continue
            }
            if (code >= 400 && !allowError) {
                conn.disconnect()
                error("HTTP $code từ ${URL(current).host}")
            }
            return conn
        }
        error("Quá nhiều lần chuyển hướng")
    }

    private fun safeName(desc: String, id: String): String {
        var s = desc.replace(Regex("[\\\\/:*?\"<>|#\\r\\n\\t]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)
        if (s.isNotEmpty() && s.last().isHighSurrogate()) s = s.dropLast(1)
        s = s.trim()
        return if (s.isEmpty()) id else "$s-$id"
    }
}
