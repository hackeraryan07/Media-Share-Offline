package com.example.tv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackTransportControlGlue
import org.json.JSONObject
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class PlayerActivity : FragmentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var videoUrlString: String? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    
    private var playlist: List<TvVideo>? = null
    private var currentIndex: Int = 0
    private var currentVideo: TvVideo? = null
    private var isWaitingForResume = false
    private var resumeDialog: android.app.AlertDialog? = null

    private lateinit var videoFragment: VideoSupportFragment
    private lateinit var playerGlue: CustomPlaybackControlGlue<LeanbackPlayerAdapter>

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        currentVideo = intent.getSerializableExtra("video") as? TvVideo
        playlist = intent.getSerializableExtra("playlist") as? ArrayList<TvVideo>
        currentIndex = intent.getIntExtra("currentIndex", 0)

        if (currentVideo == null) {
            finish()
            return
        }

        // Retrieve or create VideoSupportFragment
        var frag = supportFragmentManager.findFragmentById(R.id.player_fragment_container) as? VideoSupportFragment
        if (frag == null) {
            frag = VideoSupportFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.player_fragment_container, frag)
                .commitNow()
        }
        videoFragment = frag

        initializePlayer()
    }
    
    private fun addToUpNext(videoId: String) {
        val videoUrl = videoUrlString
        if (videoUrl.isNullOrEmpty() || !videoUrl.startsWith("http")) return
        
        val uri = Uri.parse(videoUrl)
        val scheme = uri.scheme ?: "http"
        val host = uri.host ?: "127.0.0.1"
        val port = uri.port
        val baseUrl = if (port != -1) "$scheme://$host:$port" else "$scheme://$host"
        
        val addUrl = "$baseUrl/playlists/quickqueue?videoId=$videoId&name=Up%20Next"
        
        val client = OkHttpClient()
        val request = Request.Builder().url(addUrl).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun getVideoById(id: String): TvVideo? {
        return playlist?.find { it.id == id } ?: if (currentVideo?.id == id) currentVideo else null
    }

    private fun initializePlayer() {
        videoUrlString = currentVideo?.url
        
        exoPlayer = ExoPlayer.Builder(this).build()
        
        // Wrap with Leanback adapter with update period (e.g. 250ms)
        val playerAdapter = LeanbackPlayerAdapter(this, exoPlayer!!, 250)
        
        // Connect to PlaybackTransportControlGlue
        playerGlue = CustomPlaybackControlGlue(this, playerAdapter)
        playerGlue.host = VideoSupportFragmentGlueHost(videoFragment)
        playerGlue.isSeekEnabled = true
        playerGlue.isControlsOverlayAutoHideEnabled = true
        playerGlue.controlListener = object : CustomPlaybackControlListener {
            override fun onSkipNext() {
                if (exoPlayer?.hasNextMediaItem() == true) {
                    exoPlayer?.seekToNextMediaItem()
                }
            }
            override fun onSkipPrevious() {
                if (exoPlayer?.hasPreviousMediaItem() == true) {
                    exoPlayer?.seekToPreviousMediaItem()
                }
            }
            override fun onRewind() {
                exoPlayer?.let { player ->
                    val newPos = player.currentPosition - 5000L
                    player.seekTo(if (newPos < 0) 0 else newPos)
                }
            }
            override fun onFastForward() {
                exoPlayer?.let { player ->
                    val newPos = player.currentPosition + 15000L
                    val duration = player.duration
                    player.seekTo(if (duration > 0 && newPos > duration) duration else newPos)
                }
            }
            override fun onSpeedSettings() {
                showSpeedSettings()
            }
        }
        
        // Set metadata on glue
        playerGlue.title = currentVideo?.title
        playerGlue.subtitle = "Streaming from Mobile Wi-Fi Server"
        
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
                            return@post
                        }
                    }
                    if (index != -1) {
                        exoPlayer?.seekToDefaultPosition(index)
                        exoPlayer?.prepare()
                    }
                }
            }
            override fun seekTo(positionMs: Long) {
                Handler(Looper.getMainLooper()).post {
                    exoPlayer?.seekTo(positionMs)
                }
            }
            override fun getState(): JSONObject {
                val state = JSONObject()
                state.put("videoId", currentVideo?.id ?: "")
                state.put("title", currentVideo?.title ?: "")
                var playing = false
                var position = 0L
                var duration = 0L
                var needsResume = false
                var resumePos = 0L
                val latch = java.util.concurrent.CountDownLatch(1)
                Handler(Looper.getMainLooper()).post {
                    playing = exoPlayer?.isPlaying ?: false
                    position = exoPlayer?.currentPosition ?: 0L
                    duration = exoPlayer?.duration ?: 0L
                    needsResume = isWaitingForResume
                    resumePos = currentVideo?.watchedPosition ?: 0L
                    latch.countDown()
                }
                try { latch.await(1, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) {}
                state.put("isPlaying", playing)
                state.put("position", position)
                state.put("duration", duration)
                state.put("needsResumeChoice", needsResume)
                state.put("resumePosition", resumePos)
                return state
            }
            override fun handleResumeChoice(choice: String) {
                Handler(Looper.getMainLooper()).post {
                    this@PlayerActivity.handleResumeChoice(choice, currentVideo?.watchedPosition ?: 0L)
                }
            }
        }
        
        val watchedPos = currentVideo!!.watchedPosition
        val totDur = currentVideo!!.totalDuration
        val isCompleted = totDur > 0L && watchedPos >= totDur - 5000L
        if (watchedPos > 1000 && !isCompleted) { // Only resume if progress is more than 1 second to avoid tiny glitches
            exoPlayer?.playWhenReady = false
            showResumeDialog(currentVideo!!)
        } else {
            if (isCompleted) {
                exoPlayer?.seekTo(0L)
            }
            exoPlayer?.playWhenReady = true
        }

        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    playerGlue.host?.setControlsOverlayAutoHideEnabled(true)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                mediaItem?.mediaId?.let { id ->
                    getVideoById(id)?.let { video ->
                        currentVideo = video
                        playerGlue.title = video.title
                        playerGlue.subtitle = "Streaming from Mobile Wi-Fi Server"
                        videoUrlString = video.url
                        
                        if ((reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || 
                             reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || 
                             reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)) {
                            
                            exoPlayer?.playWhenReady = false
                            isWaitingForResume = false
                            if (resumeDialog?.isShowing == true) {
                                resumeDialog?.dismiss()
                            }
                            
                            val watched = video.watchedPosition
                            val total = video.totalDuration
                            val completed = total > 0L && watched >= total - 5000L
                            if (watched > 1000 && !completed) {
                                showResumeDialog(video)
                            } else {
                                if (completed) {
                                    exoPlayer?.seekTo(0L)
                                }
                                exoPlayer?.playWhenReady = true
                            }
                        }
                    }
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val finalDuration = exoPlayer?.duration?.takeIf { it > 0 } ?: currentVideo?.totalDuration ?: 0L
                    currentVideo?.let { video ->
                        sendProgressUpdate(video.id, finalDuration, finalDuration)
                    }
                    if (exoPlayer?.hasNextMediaItem() == false) {
                        finish()
                    }
                }
            }
        })

        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.postDelayed(progressRunnable, 3000)
    }

    fun handleResumeChoice(choice: String, position: Long) {
        if (!isWaitingForResume) return
        isWaitingForResume = false
        if (resumeDialog?.isShowing == true) {
            resumeDialog?.dismiss()
        }
        if (choice == "continue") {
            exoPlayer?.seekTo(position)
        } else {
            exoPlayer?.seekTo(0)
        }
        exoPlayer?.playWhenReady = true
        exoPlayer?.play()
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%d:%02d", mins, secs)
    }

    private fun showResumeDialog(video: TvVideo) {
        if (resumeDialog?.isShowing == true) {
            resumeDialog?.dismiss()
        }
        isWaitingForResume = true
        val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("Resume Playback?")
        builder.setMessage("Would you like to resume \"${video.title}\" from ${formatTime(video.watchedPosition)}?")
        builder.setPositiveButton("Continue") { dialog, _ ->
            handleResumeChoice("continue", video.watchedPosition)
            dialog.dismiss()
        }
        builder.setNegativeButton("Start Over") { dialog, _ ->
            handleResumeChoice("start_over", 0L)
            dialog.dismiss()
        }
        builder.setCancelable(false)
        resumeDialog = builder.create()
        resumeDialog?.show()
    }

    private fun showSpeedSettings() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        var currentSpeed = exoPlayer?.playbackParameters?.speed ?: 1.0f
        var checkedItem = speedValues.toList().indexOf(currentSpeed)
        if (checkedItem == -1) checkedItem = 2

        val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("Playback Speed")
        builder.setSingleChoiceItems(speeds, checkedItem) { dialog, which ->
            exoPlayer?.setPlaybackSpeed(speedValues[which])
            dialog.dismiss()
        }
        builder.show()
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val prefs = getSharedPreferences("ButtonMappings", android.content.Context.MODE_PRIVATE)
        val action = prefs.getString(keyCode.toString(), null)
        
        if (action != null) {
            when (action) {
                "PLAY_PAUSE" -> {
                    exoPlayer?.let { player ->
                        if (player.isPlaying) player.pause() else player.play()
                    }
                    return true
                }
                "SKIP_REVERSE_5" -> {
                    playerGlue.controlListener?.onRewind()
                    return true
                }
                "SKIP_FORWARD_15" -> {
                    playerGlue.controlListener?.onFastForward()
                    return true
                }
                "NEXT" -> {
                    playerGlue.controlListener?.onSkipNext()
                    return true
                }
                "PREVIOUS" -> {
                    playerGlue.controlListener?.onSkipPrevious()
                    return true
                }
                "SPEED_SETTINGS" -> {
                    playerGlue.controlListener?.onSpeedSettings()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
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
        
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(updateUrl)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("PlayerActivity", "Failed updating progress: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun saveFinalProgress() {
        exoPlayer?.let { player ->
            if (player.playbackState == Player.STATE_ENDED) return
            val currentPos = player.currentPosition
            val duration = player.duration
            if (duration > 0 && currentPos >= 0 && currentPos < duration) {
                currentVideo?.let { video ->
                    sendProgressUpdate(video.id, currentPos, duration)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TvRemoteServer.playerController = null
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
