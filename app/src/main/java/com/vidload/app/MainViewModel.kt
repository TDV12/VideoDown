package com.vidload.app

import android.app.Application
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

    /** Lấy link từ đoạn text chia sẻ (TikTok/Facebook hay kèm chữ phía trước). */
    fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', '"')

    fun setSharedText(text: String) {
        link = extractUrl(text) ?: text
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
                status = "Lỗi: ${it.message}\n\nThử bấm \"Cập nhật engine\" rồi tải lại."
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
