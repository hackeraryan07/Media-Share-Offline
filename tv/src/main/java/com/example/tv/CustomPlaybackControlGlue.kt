package com.example.tv

import android.content.Context
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.media.PlayerAdapter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow

interface CustomPlaybackControlListener {
    fun onSkipNext()
    fun onSkipPrevious()
    fun onSpeedSettings()
    fun onRewind()
    fun onFastForward()
}

class CustomPlaybackControlGlue<T : PlayerAdapter>(
    context: Context,
    adapter: T
) : PlaybackTransportControlGlue<T>(context, adapter) {

    private val skipPreviousAction = PlaybackControlsRow.SkipPreviousAction(context)
    private val skipNextAction = PlaybackControlsRow.SkipNextAction(context)
    private val rewindAction = PlaybackControlsRow.RewindAction(context)
    private val fastForwardAction = PlaybackControlsRow.FastForwardAction(context)
    private val speedAction = PlaybackControlsRow.MoreActions(context) // re-using more action for speed

    var controlListener: CustomPlaybackControlListener? = null

    init {
        // Default leanback icons will be used.
    }

    override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
        super.onCreatePrimaryActions(adapter)
        
        // Let's clear the adapter and add them in the correct order:
        // skip Previous, rewind, play/pause, fast forward, skip Next
        
        val playPause = adapter.get(0)
        adapter.clear()
        
        adapter.add(skipPreviousAction)
        adapter.add(rewindAction)
        adapter.add(playPause)
        adapter.add(fastForwardAction)
        adapter.add(skipNextAction)
    }

    override fun onCreateSecondaryActions(adapter: ArrayObjectAdapter) {
        super.onCreateSecondaryActions(adapter)
        adapter.add(speedAction)
    }

    override fun onKey(v: android.view.View?, keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (event?.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
            val focused = v?.findFocus()
            if (focused != null && v is android.view.ViewGroup) {
                val nextFocus = android.view.FocusFinder.getInstance().findNextFocus(v, focused, android.view.View.FOCUS_UP)
                if (nextFocus == null || nextFocus == focused) {
                    host?.hideControlsOverlay(true)
                    return true
                }
            }
        }
        return super.onKey(v, keyCode, event)
    }

    override fun onActionClicked(action: Action?) {
        when (action) {
            skipPreviousAction -> controlListener?.onSkipPrevious()
            skipNextAction -> controlListener?.onSkipNext()
            rewindAction -> controlListener?.onRewind()
            fastForwardAction -> controlListener?.onFastForward()
            speedAction -> controlListener?.onSpeedSettings()
            else -> super.onActionClicked(action)
        }
    }
}
