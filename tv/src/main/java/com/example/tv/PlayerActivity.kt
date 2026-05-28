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

class PlayerActivity : FragmentActivity() {

    private lateinit var playerView: PlayerView
    private var exoPlayer: ExoPlayer? = null
    private lateinit var metaOverlay: View
    private lateinit var titleText: TextView
    private val hideHandler = Handler(Looper.getMainLooper())
    private var videoUrlString: String? = null
    private val progressHandler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    val currentPos = player.currentPosition
                    val duration = player.duration
                    if (duration > 0 && currentPos >= 0) {
                        val video = intent.getSerializableExtra("video") as? TvVideo
                        if (video != null) {
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

        val video = intent.getSerializableExtra("video") as? TvVideo
        if (video == null) {
            finish()
            return
        }

        titleText.text = video.title

        initializePlayer(video)
    }

    private fun initializePlayer(video: TvVideo) {
        videoUrlString = video.url
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer
        
        val mediaItem = MediaItem.fromUri(Uri.parse(video.url))
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        
        if (video.watchedPosition > 1000) { // Only resume if progress is more than 1 second to avoid tiny glitches
            exoPlayer?.playWhenReady = false
            showResumeDialog(video)
        } else {
            exoPlayer?.playWhenReady = true
        }

        scheduleMetadataHide()

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // Reset progress when fully played to the end
                    sendProgressUpdate(video.id, 0L, 0L)
                    finish()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    scheduleMetadataHide()
                }
            }
        })

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
            if (duration > 0 && currentPos >= 0) {
                val video = intent.getSerializableExtra("video") as? TvVideo
                if (video != null) {
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
        val video = intent.getSerializableExtra("video") as? TvVideo
        if (video == null || video.watchedPosition <= 1000 || (exoPlayer?.currentPosition ?: 0L) > 0L) {
            exoPlayer?.play()
        }
    }
}
