package com.vidload.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    var link by mutableStateOf("")
    var audioOnly by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)
    var cookieUrl by mutableStateOf<String?>(null)

    /** Lấy link từ đoạn text chia sẻ (TikTok/Facebook hay kèm chữ phía trước). */
    fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', '"')

    fun setSharedText(text: String) {
        link = extractUrl(text) ?: text
    }

    fun openCookie() {
        val host = extractUrl(link)?.let { Uri.parse(it).host }
        if (host == null) {
            status = "Hãy dán link của trang cần lấy cookie trước (ví dụ link Douyin)."
            return
        }
        cookieUrl = "https://www.${CookieStore.baseDomain(host)}"
    }

    fun saveCookies(base: String, cookies: String) {
        val n = CookieStore.save(app, base, cookies, CookieStore.UA)
        cookieUrl = null
        status = if (n > 0) {
            "Đã lưu $n cookie cho $base. Bấm \"Tải về\" để thử lại."
        } else {
            "Chưa lấy được cookie nào. Hãy đợi trang tải xong rồi thử lại."
        }
    }

    fun startDownload() {
        if (busy) return
        val url = extractUrl(link)
        if (url == null) {
            status = "Link không hợp lệ. Hãy dán link bắt đầu bằng http hoặc https."
            return
        }
        busy = true
        progress = 0f
        status = "Đang chuẩn bị... (lần đầu có thể mất vài giây)"
        viewModelScope.launch {
            runCatching {
                Downloader.download(app, url, audioOnly) { p, line ->
                    progress = p.coerceIn(0f, 100f) / 100f
                    status = line.trim().take(150)
                }
            }.onSuccess {
                progress = 1f
                status = "Xong! Đã lưu tại: $it"
            }.onFailure {
                val msg = it.message ?: ""
                val hint = if (msg.contains("cookie", ignoreCase = true) ||
                    msg.contains("login", ignoreCase = true) ||
                    msg.contains("403")
                ) {
                    "Trang này cần cookie: bấm \"Lấy cookie\" bên dưới, đợi trang tải xong, " +
                        "bấm \"Lưu cookie\" rồi tải lại."
                } else {
                    "Thử bấm \"Cập nhật engine\" rồi tải lại."
                }
                status = "Lỗi: $msg\n\n$hint"
            }
            busy = false
        }
    }

    fun updateEngine() {
        if (busy) return
        busy = true
        status = "Đang cập nhật engine..."
        viewModelScope.launch {
            runCatching { Engine.update(app) }
                .onSuccess { status = it }
                .onFailure { status = "Cập nhật lỗi: ${it.message}" }
            busy = false
        }
    }
}
