package com.example.tv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import org.json.JSONObject

class PlayerActivity : FragmentActivity() {

    private lateinit var playerView: PlayerView
    private var exoPlayer: ExoPlayer? = null
    private lateinit var metaOverlay: View
    private lateinit var titleText: TextView
    private val hideHandler = Handler(Looper.getMainLooper())
    private var videoUrlString: String? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    
    private var playlist: List<TvVideo>? = null
    private var currentIndex: Int = 0
    private var currentVideo: TvVideo? = null

    private val progressRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    val currentPos = player.currentPosition
                    val duration = player.duration
                    if (duration > 0 && currentPos >= 0) {
                        currentVideo?.let { video ->
                            sendProgressUpdate(video.id, currentPos, duration)
                        }
                    }
                }
            }
            progressHandler.postDelayed(this, 3000)
        }
    }

    private val hideRunnable = Runnable {
        metaOverlay.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.video_view)
        metaOverlay = findViewById(R.id.player_meta_overlay)
        titleText = findViewById(R.id.player_video_title)

        currentVideo = intent.getSerializableExtra("video") as? TvVideo
        playlist = intent.getSerializableExtra("playlist") as? ArrayList<TvVideo>
        currentIndex = intent.getIntExtra("currentIndex", 0)

        if (currentVideo == null) {
            finish()
            return
        }

        initializePlayer()
    }

    private fun getVideoById(id: String): TvVideo? {
        return playlist?.find { it.id == id } ?: if (currentVideo?.id == id) currentVideo else null
    }

    private fun initializePlayer() {
        videoUrlString = currentVideo?.url
        titleText.text = currentVideo?.title
        
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer
        
        val items = mutableListOf<MediaItem>()
        if (playlist != null && playlist!!.isNotEmpty()) {
            playlist!!.forEach { video ->
                items.add(MediaItem.Builder()
                    .setUri(Uri.parse(video.url))
                    .setMediaId(video.id)
                    .build())
            }
            exoPlayer?.setMediaItems(items, currentIndex, 0L)
        } else {
            currentVideo?.let {
                exoPlayer?.setMediaItem(MediaItem.Builder()
                    .setUri(Uri.parse(it.url))
                    .setMediaId(it.id)
                    .build())
            }
        }
        
        exoPlayer?.prepare()
        
        TvRemoteServer.playerController = object : TvRemoteServer.PlayerController {
            override fun play() { Handler(Looper.getMainLooper()).post { exoPlayer?.play() } }
            override fun pause() { Handler(Looper.getMainLooper()).post { exoPlayer?.pause() } }
            override fun next() { Handler(Looper.getMainLooper()).post { if (exoPlayer?.hasNextMediaItem() == true) exoPlayer?.seekToNextMediaItem() } }
            override fun prev() { Handler(Looper.getMainLooper()).post { if (exoPlayer?.hasPreviousMediaItem() == true) exoPlayer?.seekToPreviousMediaItem() } }
            override fun playVideo(id: String) {
                Handler(Looper.getMainLooper()).post {
                    var index = playlist?.indexOfFirst { it.id == id } ?: -1
                    if (index == -1) {
                        // Video not in current filtered Leanback playlist. Update playlist to full TvDataStore
                        playlist = ArrayList(TvDataStore.playlist)
                        val items = mutableListOf<MediaItem>()
                        playlist!!.forEach { video ->
                            items.add(MediaItem.Builder()
                                .setUri(Uri.parse(video.url))
                                .setMediaId(video.id)
                                .build())
                        }
                        index = playlist?.indexOfFirst { it.id == id } ?: -1
                        if (index != -1) {
                            exoPlayer?.setMediaItems(items, index, 0L)
                            exoPlayer?.prepare()
                            exoPlayer?.playWhenReady = true
                            return@post
                        }
                    }
                    if (index != -1) {
                        exoPlayer?.seekToDefaultPosition(index)
                        exoPlayer?.playWhenReady = true
                    }
                }
            }
            override fun getState(): JSONObject {
                val state = JSONObject()
                state.put("isPlaying", exoPlayer?.isPlaying ?: false)
                state.put("videoId", currentVideo?.id ?: "")
                state.put("title", currentVideo?.title ?: "")
                return state
            }
        }
        
        if (currentVideo!!.watchedPosition > 1000) { // Only resume if progress is more than 1 second to avoid tiny glitches
            exoPlayer?.playWhenReady = false
            showResumeDialog(currentVideo!!)
        } else {
            exoPlayer?.playWhenReady = true
        }

        scheduleMetadataHide()

        exoPlayer?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                mediaItem?.mediaId?.let { id ->
                    getVideoById(id)?.let { video ->
                        currentVideo = video
                        titleText.text = video.title
                        videoUrlString = video.url
                        showMetadataTemp()
                    }
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // Reset progress when fully played to the end
                    currentVideo?.let { video ->
                        sendProgressUpdate(video.id, 0L, 0L)
                    }
                    if (exoPlayer?.hasNextMediaItem() == false) {
                        finish()
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    scheduleMetadataHide()
                }
            }
        })

        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.postDelayed(progressRunnable, 3000)
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%d:%02d", mins, secs)
    }

    private fun showResumeDialog(video: TvVideo) {
        val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("Resume Playback?")
        builder.setMessage("Would you like to resume \"${video.title}\" from ${formatTime(video.watchedPosition)}?")
        builder.setPositiveButton("Continue") { dialog, _ ->
            exoPlayer?.seekTo(video.watchedPosition)
            exoPlayer?.playWhenReady = true
            exoPlayer?.play()
            dialog.dismiss()
        }
        builder.setNegativeButton("Start Over") { dialog, _ ->
            exoPlayer?.seekTo(0)
            exoPlayer?.playWhenReady = true
            exoPlayer?.play()
            dialog.dismiss()
        }
        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()
    }

    private fun sendProgressUpdate(videoId: String, position: Long, duration: Long) {
        val videoUrl = videoUrlString
        if (videoUrl.isNullOrEmpty() || !videoUrl.startsWith("http")) return
        
        val uri = Uri.parse(videoUrl)
        val scheme = uri.scheme ?: "http"
        val host = uri.host ?: "127.0.0.1"
        val port = uri.port
        val baseUrl = if (port != -1) "$scheme://$host:$port" else "$scheme://$host"
        
        val updateUrl = "$baseUrl/update_progress?id=$videoId&position=$position&duration=$duration"
        
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url(updateUrl)
            .build()
            
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                android.util.Log.e("PlayerActivity", "Failed updating progress: ${e.message}")
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }

    private fun saveFinalProgress() {
        exoPlayer?.let { player ->
            val currentPos = player.currentPosition
            val duration = player.duration
            if (duration > 0 && currentPos >= 0 && currentPos < duration) {
                currentVideo?.let { video ->
                    sendProgressUpdate(video.id, currentPos, duration)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        showMetadataTemp()
        return super.onKeyDown(keyCode, event)
    }

    private fun showMetadataTemp() {
        metaOverlay.visibility = View.VISIBLE
        scheduleMetadataHide()
    }

    private fun scheduleMetadataHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3500)
    }

    override fun onDestroy() {
        super.onDestroy()
        TvRemoteServer.playerController = null
        hideHandler.removeCallbacks(hideRunnable)
        progressHandler.removeCallbacks(progressRunnable)
        saveFinalProgress()
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
        saveFinalProgress()
    }

    override fun onResume() {
        super.onResume()
        currentVideo?.let { video ->
            if (video.watchedPosition <= 1000 || (exoPlayer?.currentPosition ?: 0L) > 0L) {
                exoPlayer?.play()
            }
        }
    }
}
