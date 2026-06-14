package com.lionstone.monkeyrapids

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    private var thread: GameThread? = null
    var hudHeightPx: Float = 0f

    private val state: GameState = GameState()

    /** Listener for non-draw game events (HUD updates, game over). */
    var listener: GameState.Listener?
        get() = state.listener
        set(value) {
            state.listener = value
            // Re-emit current values so a freshly attached listener sees them.
            value?.onScoreChanged(state.score)
            value?.onLivesChanged(state.lives)
            value?.onLevelChanged(state.level)
            value?.onBaitChargesChanged(state.baitCharges)
        }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    fun gameState(): GameState = state

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread = GameThread(holder, state) { hudHeightPx }.also {
            it.running = true
            it.start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val t = thread ?: return
        t.running = false
        var retries = 5
        while (retries-- > 0) {
            try {
                t.join(200)
                break
            } catch (_: InterruptedException) {
                // try again
            }
        }
        thread = null
    }

    // ---------- Touch input ----------

    private var downX = 0f
    private var downY = 0f
    private var consumedSwipe = false
    private val swipeThresholdPx: Float = resources.displayMetrics.density * 24

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                consumedSwipe = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!consumedSwipe) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) >= swipeThresholdPx || abs(dy) >= swipeThresholdPx) {
                        commitSwipe(dx, dy)
                        consumedSwipe = true
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!consumedSwipe) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) >= swipeThresholdPx / 2 || abs(dy) >= swipeThresholdPx / 2) {
                        commitSwipe(dx, dy)
                    } else {
                        // Tap (negligible displacement) — toggle pause/resume.
                        // togglePause is a no-op outside PLAYING/PAUSED, so taps
                        // during READY / GAME_OVER / etc. won't do anything.
                        state.togglePause()
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun commitSwipe(dx: Float, dy: Float) {
        val dir = if (abs(dx) >= abs(dy)) {
            if (dx > 0) Direction.RIGHT else Direction.LEFT
        } else {
            if (dy > 0) Direction.DOWN else Direction.UP
        }
        state.requestDirection(dir)
    }
}
