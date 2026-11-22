package com.vidload.app

import android.content.Context
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/** Lưu cookie theo từng trang (douyin.com, weibo.com...) và đưa cho yt-dlp khi tải. */
object CookieStore {
    // Giả lập trình duyệt máy tính để các trang trả về bản web đầy đủ
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val aliases = mapOf(
        "youtu.be" to "youtube.com",
        "fb.watch" to "facebook.com",
        "fb.com" to "facebook.com",
        "b23.tv" to "bilibili.com",
        "t.cn" to "weibo.com",
        "iesdouyin.com" to "douyin.com"
    )

    fun baseDomain(host: String): String {
        val parts = host.lowercase().split('.')
        val base = if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host.lowercase()
        return aliases[base] ?: base
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "cookies").apply { mkdirs() }
    private fun cookieFile(ctx: Context, base: String) = File(dir(ctx), "$base.txt")
    private fun uaFile(ctx: Context, base: String) = File(dir(ctx), "$base.ua")

    /** Lưu chuỗi cookie "a=b; c=d" thành file định dạng Netscape. Trả về số cookie đã lưu. */
    fun save(ctx: Context, base: String, cookieHeader: String, ua: String): Int {
        val map = LinkedHashMap<String, String>()
        cookieHeader.split(';').forEach { part ->
            val p = part.trim()
            val i = p.indexOf('=')
            if (i > 0) map[p.substring(0, i)] = p.substring(i + 1)
        }
        if (map.isEmpty()) return 0
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        for ((k, v) in map) {
            sb.append(".$base\tTRUE\t/\tFALSE\t2147483647\t$k\t$v\n")
        }
        cookieFile(ctx, base).writeText(sb.toString())
        uaFile(ctx, base).writeText(ua)
        return map.size
    }

    /** Nếu đã có cookie của trang tương ứng với link thì đưa vào yt-dlp. */
    fun applyTo(ctx: Context, url: String, req: YoutubeDLRequest) {
        val host = Uri.parse(url).host ?: return
        val base = baseDomain(host)
        val cf = cookieFile(ctx, base)
        if (!cf.exists()) return
        req.addOption("--cookies", cf.absolutePath)
        val uf = uaFile(ctx, base)
        if (uf.exists()) req.addOption("--user-agent", uf.readText().trim())
    }
}
