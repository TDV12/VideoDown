package com.vidload.app

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Khởi tạo yt-dlp + FFmpeg (chỉ làm 1 lần, chạy nền để không treo giao diện). */
object Engine {
    private val mutex = Mutex()
    @Volatile
    private var ready = false

    suspend fun ensureInit(ctx: Context) {
        if (ready) return
        mutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                val app = ctx.applicationContext
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready = true
            }
        }
    }

    /** Cập nhật yt-dlp lên bản mới nhất (cần thiết khi các trang web đổi cách phát video). */
    suspend fun update(ctx: Context): String {
        ensureInit(ctx)
        return withContext(Dispatchers.IO) {
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel._STABLE)
            "Cập nhật xong: $status"
        }
    }
}
