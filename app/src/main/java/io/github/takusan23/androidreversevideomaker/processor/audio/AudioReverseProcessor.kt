package io.github.takusan23.androidreversevideomaker.processor.audio

import android.content.Context
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import io.github.takusan23.androidreversevideomaker.processor.MediaExtractorTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

object AudioReverseProcessor {

    /** 動画を後ろのフレームから取り出して、逆再生動画を作る */
    suspend fun reverseAudio(
        context: Context,
        inFileUri: Uri,
        outFile: File,
        tempFolder: File
    ) = withContext(Dispatchers.IO) {
        // 音声の生データ置き場
        val rawDataFile = tempFolder.resolve("raw_file")
        val reverseRawDataFile = tempFolder.resolve("reverse_raw_data")

        // ファイルのメタデータ
        var inputMediaFormat: MediaFormat? = null
        var outputMediaFormat: MediaFormat? = null

        // デコードする（AAC を PCM に）
        decode(
            context = context,
            inFileUri = inFileUri,
            rawDataFile = rawDataFile,
            onReceiveMediaFormat = { input, output ->
                inputMediaFormat = input
                outputMediaFormat = output
            }
        )

        // PCM を逆並びにする
        reversePcmAudioData(
            rawPcmFile = rawDataFile,
            outFile = reverseRawDataFile,
            samplingRate = outputMediaFormat!!.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            channelCount = outputMediaFormat!!.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
            // Duration は MediaCodec#outputFormat ではなく MediaExtractor から
            durationUs = inputMediaFormat!!.getLong(MediaFormat.KEY_DURATION)
        )

        // PCM を AAC にエンコードする
        encode(
            rawDataFile = reverseRawDataFile,
            outFile = outFile,
            samplingRate = outputMediaFormat!!.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            channelCount = outputMediaFormat!!.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
            bitRate = 192_000
        )

        // 要らないファイルを消す
        rawDataFile.delete()
        reverseRawDataFile.delete()
    }

    /** PCM 音声データを逆に並び替えて保存する */
    private suspend fun reversePcmAudioData(
        rawPcmFile: File,
        outFile: File,
        samplingRate: Int,
        channelCount: Int,
        durationUs: Long
    ) = withContext(Dispatchers.IO) {
        // 量子化ビット数を出す（16bit とか 8bit とか。バイトに直すので 16 bitなら 2 byte）
        val durationSec = durationUs / 1_000 / 1_000
        val bitDepth = (((rawPcmFile.length() / durationSec) / samplingRate) / channelCount).toInt()

        // 逆にしていく
        // RandomAccessFile にするか、PCM データをメモリに乗せるかのどっちかだと思う。
        rawPcmFile.readBytes()
        RandomAccessFile(rawPcmFile, "r").use { randomAccessFile ->
            outFile.outputStream().use { outputStream ->
                var nextReadPosition = rawPcmFile.length()
                // 量子化ビット数 * チャンネル数 ごとに取り出す
                val audioData = ByteArray(bitDepth * channelCount)
                while (isActive) {
                    // データを逆から読み出す
                    // 現在位置を調整してバイト配列に入れる
                    randomAccessFile.seek(nextReadPosition)
                    randomAccessFile.read(audioData)
                    // 次取り出す位置
                    nextReadPosition -= audioData.size
                    // 書き込む
                    outputStream.write(audioData)
                    // もう次がない場合は
                    if (nextReadPosition < 0) {
                        break
                    }
                }
            }
        }
    }

    /** PCM を AAC にエンコードする */
    private suspend fun encode(
        rawDataFile: File,
        outFile: File,
        samplingRate: Int,
        channelCount: Int,
        bitRate: Int
    ) = withContext(Dispatchers.Default) {
        val audioEncoder = AudioEncoder().apply {
            prepareEncoder(
                sampleRate = samplingRate,
                channelCount = channelCount,
                bitRate = bitRate
            )
        }
        // MediaMuxer
        var index = -1
        val mediaMuxer = MediaMuxer(outFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // エンコードする
        rawDataFile.inputStream().use { inputStream ->
            audioEncoder.startAudioEncode(
                onRecordInput = { bytes ->
                    // データをエンコーダーに渡す
                    inputStream.read(bytes)
                },
                onOutputFormatAvailable = { mediaFormat ->
                    // トラックを追加
                    index = mediaMuxer.addTrack(mediaFormat)
                    mediaMuxer.start()
                },
                onOutputBufferAvailable = { byteBuffer, bufferInfo ->
                    // 書き込む
                    mediaMuxer.writeSampleData(index, byteBuffer, bufferInfo)
                }
            )
        }
        mediaMuxer.stop()
        mediaMuxer.release()
    }

    /** AAC をデコードする（PCM */
    private suspend fun decode(
        context: Context,
        inFileUri: Uri,
        rawDataFile: File,
        onReceiveMediaFormat: (input: MediaFormat, output: MediaFormat) -> Unit
    ) {
        // Extractor から取り出す
        val (mediaExtractor, mediaFormat) = MediaExtractorTool.createMediaExtractor(context, inFileUri, MediaExtractorTool.Track.AUDIO)
        // デコーダーにメタデータを渡す
        val audioDecoder = AudioDecoder().apply {
            prepareDecoder(mediaFormat)
        }
        // ファイルに書き込む準備
        rawDataFile.outputStream().use { outputStream ->
            // デコードする
            audioDecoder.startAudioDecode(
                onOutputFormat = { outputMediaFormat ->
                    onReceiveMediaFormat(mediaFormat, outputMediaFormat)
                },
                readSampleData = { byteBuffer ->
                    // データを進める
                    val size = mediaExtractor.readSampleData(byteBuffer, 0)
                    mediaExtractor.advance()
                    size to mediaExtractor.sampleTime
                },
                onOutputBufferAvailable = { bytes ->
                    // データを書き込む
                    outputStream.write(bytes)
                }
            )
        }
        mediaExtractor.release()
    }
}