package jp.viscloud.adaptivemusicdrive

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MusicPlayer(
    private val context: Context
) {

    private val player =
        ExoPlayer.Builder(context).build()

    private var currentBpm: Int? = null
    private var currentFile: String? = null

    init {
        player.repeatMode = Player.REPEAT_MODE_ONE
    }

    fun playForBpm(bpm: Int) {

        // すでに同じBPMを再生中なら何もしない
        if (currentBpm == bpm && player.isPlaying) {
            return
        }

        val directory = when (bpm) {
            60 -> "bpm60"
            80 -> "bpm80"
            120 -> "bpm120"
            else -> return
        }

        val files =
            context.assets.list(directory)
                ?.filter {
                    it.endsWith(".mp3", ignoreCase = true)
                }
                ?: emptyList()

        if (files.isEmpty()) {
            return
        }

        /*
         * ひとまず先頭の曲を使用。
         * 後でランダム選択にも変更可能。
         */
        val filename = files.first()

        val assetPath =
            "$directory/$filename"

        val uri =
            "asset:///$assetPath"

        player.stop()
        player.clearMediaItems()

        val mediaItem =
            MediaItem.fromUri(uri)

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        currentBpm = bpm
        currentFile = filename
    }

    fun stop() {
        player.stop()

        currentBpm = null
        currentFile = null
    }

    fun release() {
        player.release()
    }

    fun getCurrentFile(): String? {
        return currentFile
    }
}