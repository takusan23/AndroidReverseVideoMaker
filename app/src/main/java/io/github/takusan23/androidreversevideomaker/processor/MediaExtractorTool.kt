package io.github.takusan23.androidreversevideomaker.processor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File

object MediaExtractorTool {

    enum class Track(val mimeTypePrefix: String) {
        VIDEO("video/"),
        AUDIO("audio/")
    }

    /**
     * [MediaExtractor]を作る
     *
     * @return [MediaExtractor]と選択したトラックの[MediaFormat]
     */
    fun createMediaExtractor(
        context: Context,
        uri: Uri,
        track: Track
    ): Pair<MediaExtractor, MediaFormat> {
        val mediaExtractor = MediaExtractor().apply {
            // read で FileDescriptor を開く
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                setDataSource(it.fileDescriptor)
            }
        }
        val (index, mediaFormat) = mediaExtractor.getTrackMediaFormat(track)
        mediaExtractor.selectTrack(index)
        // Extractor / MediaFormat を返す
        return mediaExtractor to mediaFormat
    }

    /**
     * [MediaExtractor]を作る
     *
     * @return [MediaExtractor]と選択したトラックの[MediaFormat]
     */
    fun createMediaExtractor(
        file: File,
        track: Track
    ): Pair<MediaExtractor, MediaFormat> {
        val mediaExtractor = MediaExtractor().apply {
            setDataSource(file.path)
        }
        val (index, mediaFormat) = mediaExtractor.getTrackMediaFormat(track)
        mediaExtractor.selectTrack(index)
        // Extractor / MediaFormat を返す
        return mediaExtractor to mediaFormat
    }

    private fun MediaExtractor.getTrackMediaFormat(track: Track): Pair<Int, MediaFormat> {
        // トラックを選択する（映像・音声どっち？）
        val trackIndex = (0 until this.trackCount)
            .map { this.getTrackFormat(it) }
            .indexOfFirst { it.getString(MediaFormat.KEY_MIME)?.startsWith(track.mimeTypePrefix) == true }
        val mediaFormat = this.getTrackFormat(trackIndex)
        // 位置と MediaFormat
        return trackIndex to mediaFormat
    }

}