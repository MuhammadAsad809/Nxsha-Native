package com.nxsha.nativeapp.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

class PlayerGestureDetector(
    private val activity: Activity,
    private val listener: GestureListener
) : View.OnTouchListener {

    interface GestureListener {
        fun onDoubleTapSeek(isForward: Boolean)
        fun onSingleTap()
        fun onSpeedBoost(active: Boolean)
        fun onBrightnessChanged(percent: Int)
        fun onVolumeChanged(percent: Int)
    }

    private val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private var isHoldingSpeed = false
    private var isDragging = false
    private var dragSide = Side.NONE // LEFT: Brightness, RIGHT: Volume
    private var startY = 0f

    private enum class Side { NONE, LEFT, RIGHT }

    private val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            listener.onSingleTap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val screenWidth = activity.resources.displayMetrics.widthPixels
            val isRightSide = e.x > (screenWidth / 2)
            listener.onDoubleTapSeek(isRightSide)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // Reverse-engineered GesturesJsx5: 2x Speed Hold
            if (!isDragging) {
                isHoldingSpeed = true
                listener.onSpeedBoost(true)
            }
        }
    })

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                isDragging = false
                dragSide = if (event.x < screenWidth / 2) Side.LEFT else Side.RIGHT
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY = startY - event.y
                if (abs(deltaY) > 40 && !isHoldingSpeed) {
                    isDragging = true
                    val changePercent = (deltaY / screenHeight)

                    if (dragSide == Side.LEFT) {
                        // Left side vertical swipe: Brightness
                        adjustBrightness(changePercent)
                    } else if (dragSide == Side.RIGHT) {
                        // Right side vertical swipe: Volume
                        adjustVolume(changePercent)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isHoldingSpeed) {
                    isHoldingSpeed = false
                    listener.onSpeedBoost(false)
                }
                isDragging = false
                dragSide = Side.NONE
            }
        }
        return true
    }

    private fun adjustBrightness(delta: Float) {
        val lp = activity.window.attributes
        var current = lp.screenBrightness
        if (current < 0) current = 0.5f // default auto fallback
        val target = (current + delta * 0.1f).coerceIn(0.01f, 1.0f)
        lp.screenBrightness = target
        activity.window.attributes = lp
        listener.onBrightnessChanged((target * 100).toInt())
    }

    private fun adjustVolume(delta: Float) {
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = (delta * maxVolume * 0.2f).toInt()
        val target = (currentVol + step).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        val percent = ((target.toFloat() / maxVolume) * 100).toInt()
        listener.onVolumeChanged(percent)
    }
}
