package io.github.takusan23.androidreversevideomaker

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.takusan23.androidreversevideomaker.processor.ReverseProcessor
import io.github.takusan23.androidreversevideomaker.ui.theme.AndroidReverseVideoMakerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidReverseVideoMakerTheme {
                HomeScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoUri = remember { mutableStateOf<Uri?>(null) }
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> videoUri.value = uri }
    )

    // 処理中か処理終わりか
    val statusText = remember { mutableStateOf("待機中") }

    fun start() {
        val uri = videoUri.value ?: return
        scope.launch {
            // 逆再生動画を作る処理
            statusText.value = "処理中"
            ReverseProcessor.start(
                context = context,
                inFileUri = uri
            )
            statusText.value = "処理終わり"
            println("終わったよ")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.app_name)) })
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {

            Button(onClick = {
                videoPicker.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly))
            }) {
                Text(text = "動画を選ぶ")
            }

            if (videoUri.value != null) {
                Text(text = videoUri.value.toString())
                Button(onClick = { start() }) {
                    Text(text = "処理を開始する")
                }
            }

            Text(text = statusText.value)
        }
    }
}