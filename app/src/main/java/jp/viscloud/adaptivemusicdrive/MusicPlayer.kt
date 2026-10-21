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

    init {
        player.repeatMode = Player.REPEAT_MODE_ONE
    }

    fun playForBpm(bpm: Int) {

        // 同じBPMなら曲を切り替えない
        if (currentBpm == bpm && player.isPlaying) {
            return
        }

        val resourceId =
            when (bpm) {
                60 -> R.raw.bpm60
                80 -> R.raw.bpm60
                120 -> R.raw.bpm80
                else -> return
            }

        val uri =
            "android.resource://${context.packageName}/$resourceId"

        val mediaItem =
            MediaItem.fromUri(uri)

        player.stop()
        player.clearMediaItems()

        player.setMediaItem(mediaItem)

        player.prepare()
        player.play()

        currentBpm = bpm
    }

    fun stop() {
        player.stop()
        currentBpm = null
    }

    fun release() {
        player.release()
    }
}