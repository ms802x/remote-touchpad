package com.remotetouchpad.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Mirrors a laptop precision touchpad:
 *  - one finger: drag to move, tap = left click, hold-still-and-press = right click
 *  - two fingers: drag = scroll, quick tap = right click
 * Zoom is a dedicated button elsewhere, not a trackpad gesture.
 */
class TrackpadView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var onMove: ((dx: Float, dy: Float) -> Unit)? = null
    var onLeftClick: (() -> Unit)? = null
    var onRightClick: (() -> Unit)? = null
    var onScroll: ((dy: Float) -> Unit)? = null

    private val sensitivity = 1.6f
    private val moveSlop = 14f
    private val longPressMillis = 420L

    // Single-finger state
    private var lastX = 0f
    private var lastY = 0f
    private var totalMovement = 0f
    private var longPressFired = false

    // Two-finger state
    private var twoFingerActive = false
    private var lastMidY = 0f
    private var twoFingerMaxDelta = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (totalMovement < moveSlop) {
            longPressFired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onRightClick?.invoke()
        }
    }

    private val hintPaint = Paint().apply {
        color = Color.argb(70, 255, 255, 255)
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawText("Drag to move  ·  tap to click  ·  hold for right-click", width / 2f, height / 2f - 20f, hintPaint)
        canvas.drawText("Two fingers: drag to scroll  ·  tap to right-click", width / 2f, height / 2f + 20f, hintPaint)
    }

    private fun midY(event: MotionEvent): Float = (event.getY(0) + event.getY(1)) / 2f

    private fun startTwoFingerGesture(event: MotionEvent) {
        twoFingerActive = true
        twoFingerMaxDelta = 0f
        lastMidY = midY(event)
        longPressFired = true // suppress any pending single-finger click
        handler.removeCallbacks(longPressRunnable)
    }

    private fun updateTwoFingerGesture(event: MotionEvent) {
        val currentMidY = midY(event)
        val dy = currentMidY - lastMidY
        twoFingerMaxDelta = maxOf(twoFingerMaxDelta, abs(currentMidY - lastMidY))
        if (dy != 0f) onScroll?.invoke(dy)
        lastMidY = currentMidY
    }

    private fun endTwoFingerGesture() {
        if (twoFingerActive && twoFingerMaxDelta < moveSlop) {
            onRightClick?.invoke()
        }
        twoFingerActive = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                totalMovement = 0f
                longPressFired = false
                twoFingerActive = false
                handler.postDelayed(longPressRunnable, longPressMillis)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    startTwoFingerGesture(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (twoFingerActive && event.pointerCount >= 2) {
                    updateTwoFingerGesture(event)
                } else if (!twoFingerActive) {
                    val dx = (event.x - lastX) * sensitivity
                    val dy = (event.y - lastY) * sensitivity
                    totalMovement += abs(event.x - lastX) + abs(event.y - lastY)
                    lastX = event.x
                    lastY = event.y
                    if (totalMovement >= moveSlop) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (dx != 0f || dy != 0f) onMove?.invoke(dx, dy)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (twoFingerActive) {
                    endTwoFingerGesture()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (twoFingerActive) {
                    endTwoFingerGesture()
                } else if (!longPressFired && totalMovement < moveSlop) {
                    onLeftClick?.invoke()
                }
            }
        }
        return true
    }
}
