package io.github.takusan23.androidreversevideomaker.processor.video

import android.content.Context
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoReverseProcessor {

    /** 動画を後ろのフレームから取り出して、逆再生動画を作る */
    suspend fun reverseVideoFrame(
        context: Context,
        inFileUri: Uri,
        outFile: File
    ) = withContext(Dispatchers.IO) {
        // メタデータを取り出す
        val inputVideoMediaMetadataRetriever = MediaMetadataRetriever().apply { setDataSource(context, inFileUri) }
        val bitRate = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 3_000_000
        val (videoHeight, videoWidth) = inputVideoMediaMetadataRetriever.extractVideoSize()
        val frameRate = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toIntOrNull() ?: 30
        val durationMs = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt()!!

        // Canvas で毎フレーム書く
        val paint = Paint()
        CanvasVideoProcessor.start(
            outFile = outFile,
            bitRate = bitRate,
            frameRate = frameRate,
            outputVideoWidth = videoWidth,
            outputVideoHeight = videoHeight,
            onCanvasDrawRequest = { currentPositionMs ->
                // ここが Canvas なので、好きなように書く
                // 逆再生したときの、動画のフレームを取り出して、Canvas に書く。
                // getFrameAtTime はマイクロ秒なので注意
                val reverseCurrentPositionMs = durationMs - currentPositionMs
                val bitmap = inputVideoMediaMetadataRetriever.getFrameAtTime(reverseCurrentPositionMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bitmap != null) {
                    drawBitmap(bitmap, 0f, 0f, paint)
                }
                currentPositionMs <= durationMs
            }
        )
    }

    /**
     * MediaMetadataRetriever で動画の縦横を取得する
     *
     * @return Height / Width の Pair
     */
    private fun MediaMetadataRetriever.extractVideoSize(): Pair<Int, Int> {
        // Android のメディア系（ Retriever だけでなく、MediaExtractor お前もだぞ）
        // 縦動画の場合、縦と横が入れ替わるワナが存在する
        // ROTATION を見る必要あり
        val videoWidth = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
        val videoHeight = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
        val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        return when (rotation) {
            // 縦だけ入れ替わるので
            90, 270 -> Pair(videoWidth, videoHeight)
            else -> Pair(videoHeight, videoWidth)
        }
    }
}