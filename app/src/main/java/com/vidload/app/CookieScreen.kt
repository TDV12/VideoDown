package com.vidload.app

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CookieScreen(
    startUrl: String,
    onSave: (base: String, cookies: String) -> Unit,
    onCancel: () -> Unit
) {
    var web by remember { mutableStateOf<WebView?>(null) }
    val host = android.net.Uri.parse(startUrl).host ?: ""
    val base = CookieStore.baseDomain(host)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Đợi trang tải xong (nếu có ô xác minh/kéo thanh trượt thì làm theo), " +
                "rồi bấm \"Lưu cookie\".",
            modifier = Modifier.padding(12.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                val cm = CookieManager.getInstance()
                cm.flush()
                val all = listOfNotNull(
                    cm.getCookie("https://$base"),
                    cm.getCookie("https://www.$base"),
                    cm.getCookie(web?.url)
                ).joinToString("; ")
                onSave(base, all)
            }) { Text("Lưu cookie") }
            OutlinedButton(onClick = onCancel) { Text("Hủy") }
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = CookieStore.UA
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl(startUrl)
                    web = this
                }
            }
        )
    }
}
