package com.vidload.app

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicReference

private const val FIND_VIDEO_JS = """
(function(){
  var out = [];
  document.querySelectorAll('video').forEach(function(v){
    if (v.currentSrc) out.push(v.currentSrc);
    if (v.src) out.push(v.src);
    v.querySelectorAll('source').forEach(function(s){ if (s.src) out.push(s.src); });
  });
  for (var i = 0; i < out.length; i++) {
    if (out[i].indexOf('http') === 0) return out[i];
  }
  return '';
})()
"""

/** Mở trang video Douyin trong WebView, tự bắt link video thật rồi trả về để tải. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptureScreen(
    startUrl: String,
    onCaptured: (videoUrl: String, cookies: String) -> Unit,
    onCancel: () -> Unit
) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var done by remember { mutableStateOf(false) }
    val sniffed = remember { AtomicReference<String?>(null) }

    fun finish(videoUrl: String) {
        if (done) return
        done = true
        val cm = CookieManager.getInstance()
        cm.flush()
        val cookies = listOfNotNull(
            cm.getCookie(videoUrl),
            cm.getCookie("https://www.douyin.com")
        ).joinToString("; ")
        onCaptured(videoUrl, cookies)
    }

    LaunchedEffect(web) {
        val w = web ?: return@LaunchedEffect
        while (!done) {
            delay(1000)
            val s = sniffed.get()
            if (s != null) {
                finish(s)
                break
            }
            w.evaluateJavascript(FIND_VIDEO_JS) { r ->
                val u = runCatching { JSONTokener(r).nextValue() as? String }.getOrNull()
                if (!u.isNullOrEmpty() && u.startsWith("http")) finish(u)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            web?.apply {
                loadUrl("about:blank")
                onPause()
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Đang mở video Douyin. Khi video bắt đầu chạy, app sẽ tự bắt link và tải về. " +
                "Nếu có cửa sổ đăng nhập thì tắt nó đi hoặc bấm nút phát video.",
            modifier = Modifier.padding(12.dp)
        )
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) { Text("Hủy") }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = CookieStore.UA
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val u = request?.url?.toString()
                            if (u != null && u.contains("douyinvod.com") &&
                                u.contains("mime_type=video")
                            ) {
                                sniffed.set(u)
                            }
                            return null
                        }
                    }
                    loadUrl(startUrl)
                    web = this
                }
            }
        )
    }
}
