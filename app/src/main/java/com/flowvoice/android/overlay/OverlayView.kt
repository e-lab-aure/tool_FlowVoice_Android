package com.flowvoice.android.overlay

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.flowvoice.android.R

/**
 * Custom FrameLayout representing the floating mic button.
 * Manages visual state transitions: IDLE, RECORDING, LOADING, SUCCESS, ERROR.
 */
class OverlayView(context: Context) : FrameLayout(context) {

    enum class State { IDLE, RECORDING, LOADING, SUCCESS, ERROR }

    private val ivMic: ImageView
    private val progress: ProgressBar
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_button, this, true)
        ivMic = findViewById(R.id.ivMicButton)
        progress = findViewById(R.id.progressIndicator)
    }

    fun setState(state: State) {
        mainHandler.post {
            when (state) {
                State.IDLE -> {
                    progress.visibility = View.GONE
                    ivMic.setImageResource(R.drawable.ic_mic)
                    setButtonColor(R.color.overlay_idle)
                }
                State.RECORDING -> {
                    progress.visibility = View.GONE
                    ivMic.setImageResource(R.drawable.ic_mic_active)
                    setButtonColor(R.color.overlay_recording)
                }
                State.LOADING -> {
                    ivMic.setImageResource(R.drawable.ic_mic)
                    setButtonColor(R.color.overlay_loading)
                    progress.visibility = View.VISIBLE
                }
                State.SUCCESS -> {
                    progress.visibility = View.GONE
                    ivMic.setImageResource(R.drawable.ic_mic)
                    setButtonColor(R.color.overlay_success)
                    // Auto-revert to IDLE after 1 second
                    mainHandler.postDelayed({ setState(State.IDLE) }, 1000)
                }
                State.ERROR -> {
                    progress.visibility = View.GONE
                    ivMic.setImageResource(R.drawable.ic_mic)
                    setButtonColor(R.color.overlay_error)
                    // Auto-revert to IDLE after 2 seconds
                    mainHandler.postDelayed({ setState(State.IDLE) }, 2000)
                }
            }
        }
    }

    private fun setButtonColor(colorRes: Int) {
        val bg = ivMic.background
        if (bg is GradientDrawable) {
            bg.setColor(ContextCompat.getColor(context, colorRes))
        } else {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, colorRes))
            }
            ivMic.background = drawable
        }
    }
}
