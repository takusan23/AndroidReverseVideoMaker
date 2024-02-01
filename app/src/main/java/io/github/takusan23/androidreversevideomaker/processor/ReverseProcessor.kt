package io.github.takusan23.androidreversevideomaker.processor

import android.content.Context
import android.net.Uri
import io.github.takusan23.androidreversevideomaker.processor.audio.AudioReverseProcessor
import io.github.takusan23.androidreversevideomaker.processor.video.VideoReverseProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ReverseProcessor {

    /** 音声と映像を逆にする */
    suspend fun start(
        context: Context,
        inFileUri: Uri
    ) = withContext(Dispatchers.Default) {
        // 一時ファイル置き場
        // 音声と映像は別々に処理するので、一旦アプリが使えるストレージに保存する
        val tempFolder = context.getExternalFilesDir(null)?.resolve("temp")?.apply { mkdir() }!!
        val reverseVideoFile = tempFolder.resolve("temp_video_reverse.mp4")
        val reverseAudioFile = tempFolder.resolve("temp_audio_reverse.mp4")
        val resultFile = tempFolder.resolve("android_reverse_video_${System.currentTimeMillis()}.mp4")

        try {
            // 音声と映像を逆にする
            // 並列処理なので、両方終わるまで joinAll で待つ。
            listOf(
                launch {
                    // 音声を逆にする処理
                    // AAC をデコードする
                    AudioReverseProcessor.reverseAudio(
                        context = context,
                        inFileUri = inFileUri,
                        outFile = reverseAudioFile,
                        tempFolder = tempFolder
                    )
                },
                launch {
                    // 映像を逆にする処理
                    VideoReverseProcessor.reverseVideoFrame(
                        context = context,
                        inFileUri = inFileUri,
                        outFile = reverseVideoFile
                    )
                }
            ).joinAll()

            // 音声トラックと映像トラックを合わせる
            MediaMuxerTool.mixAvTrack(
                audioTrackFile = reverseAudioFile,
                videoTrackFile = reverseVideoFile,
                resultFile = resultFile
            )

            // 保存する
            MediaStoreTool.saveToVideoFolder(
                context = context,
                file = resultFile
            )
        } finally {
            // 要らないファイルを消す
            tempFolder.deleteRecursively()
        }
    }
}