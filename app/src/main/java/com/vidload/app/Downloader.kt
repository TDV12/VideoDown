package com.vidload.app

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Downloader {

    /** Trả về đường dẫn (để hiển thị) của file đã lưu. */
    suspend fun download(
        ctx: Context,
        url: String,
        audioOnly: Boolean,
        onProgress: (percent: Float, line: String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        Engine.ensureInit(ctx)

        val tmp = File(ctx.cacheDir, "dl/${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val req = YoutubeDLRequest(url)
            req.addOption("-o", "${tmp.absolutePath}/%(title).80s.%(ext)s")
            req.addOption("--no-playlist")
            req.addOption("--no-mtime")
            CookieStore.applyTo(ctx, url, req)
            if (audioOnly) {
                req.addOption("-f", "ba/b")
                req.addOption("-x")
                req.addOption("--audio-format", "mp3")
            } else {
                // Ưu tiên H.264 + AAC để mọi máy đều phát được, nếu không có thì lấy bản tốt nhất
                req.addOption(
                    "-f",
                    "bv*[vcodec^=avc1]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b"
                )
                req.addOption("--merge-output-format", "mp4")
            }

            YoutubeDL.getInstance().execute(req, null) { progress, _, line ->
                onProgress(progress, line)
            }

            val out = tmp.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
                ?: error("Không tìm thấy file sau khi tải")

            saveToGallery(ctx, out)
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Chép file vào Movies/VideoLoad (video) hoặc Music/VideoLoad (âm thanh) qua MediaStore. */
    private fun saveToGallery(ctx: Context, file: File): String {
        val ext = file.extension.lowercase()
        val isAudio = ext in setOf("mp3", "m4a", "aac", "opus", "ogg", "wav")
        val mime = when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }
        val collection = if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val baseDir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val relPath = "$baseDir/VideoLoad"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(collection, values) ?: error("Không tạo được file trong bộ nhớ")
        resolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { it.copyTo(output) }
        } ?: error("Không ghi được file")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "$relPath/${file.name}"
    }
}
