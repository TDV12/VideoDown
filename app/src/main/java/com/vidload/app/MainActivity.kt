package com.vidload.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.setSharedText(it) }
        }
    }
}

@Composable
fun MainScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("VideoLoad", style = MaterialTheme.typography.headlineMedium)
        Text(
            "TikTok, YouTube, Facebook, Douyin, Weibo, Bilibili và nhiều trang khác. " +
                "Dán link rồi bấm Tải về.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = vm.link,
            onValueChange = { vm.link = it },
            label = { Text("Dán link video") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            enabled = !vm.busy
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = vm.audioOnly,
                onCheckedChange = { vm.audioOnly = it },
                enabled = !vm.busy
            )
            Text("  Chỉ tải âm thanh (MP3)")
        }

        Button(
            onClick = { vm.startDownload() },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (vm.busy) "Đang xử lý..." else "Tải về") }

        LinearProgressIndicator(
            progress = { vm.progress },
            modifier = Modifier.fillMaxWidth()
        )

        if (vm.status.isNotEmpty()) {
            Text(vm.status, style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedButton(
            onClick = { vm.updateEngine() },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Cập nhật engine (khi tải bị lỗi)") }

        Text(
            "Video lưu trong Movies/VideoLoad, âm thanh lưu trong Music/VideoLoad. " +
                "Chỉ tải nội dung bạn có quyền sử dụng. Giữ app mở trong lúc tải.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
