package com.lionstone.monkeyrapids

import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.SurfaceHolder

/**
 * Vsync-driven game loop.
 *
 * Runs on its own [HandlerThread] (so rendering stays off the UI thread) but
 * paces itself with [Choreographer] instead of `Thread.sleep`. Choreographer
 * fires `doFrame` exactly once per display refresh, so:
 *
 *  - the update timestep is the *true* vsync interval (regular, not the jittery
 *    output of an imprecise sleep), and
 *  - each rendered frame is posted in step with the panel's refresh — including
 *    high-refresh panels (e.g. 120 Hz), which the old fixed 60 fps sleep-loop
 *    couldn't match, causing uneven frame holds (judder).
 *
 * The simulation itself is unchanged: `state.update(dt)` is called with the
 * per-frame delta and `state.draw(...)` renders the current state.
 */
class GameLoop(
    private val holder: SurfaceHolder,
    private val state: GameState,
    private val hudHeightPx: () -> Float,
) : Choreographer.FrameCallback {

    private val thread = HandlerThread("MonkeyRapids-Game")
    private lateinit var handler: Handler
    private lateinit var choreographer: Choreographer

    @Volatile
    private var running = false
    private var lastFrameNanos = 0L

    fun start() {
        thread.start()
        handler = Handler(thread.looper)
        handler.post {
            // Choreographer is per-thread; grab the instance for the loop thread.
            choreographer = Choreographer.getInstance()
            running = true
            lastFrameNanos = 0L
            choreographer.postFrameCallback(this)
        }
    }

    fun stop() {
        if (this::handler.isInitialized) {
            handler.post {
                running = false
                if (this::choreographer.isInitialized) {
                    choreographer.removeFrameCallback(this)
                }
            }
        }
        thread.quitSafely()
        var retries = 5
        while (retries-- > 0) {
            try {
                thread.join(200)
                break
            } catch (_: InterruptedException) {
                // try again
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        // Schedule the next vsync first, so a slow frame doesn't compound delay.
        choreographer.postFrameCallback(this)

        // First callback only establishes the timebase.
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameTimeNanos
            return
        }
        // dt is the real vsync interval. Clamp so a GC pause or app-switch
        // can't teleport entities (matches the old loop's 50 ms cap).
        var dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = frameTimeNanos
        if (dt > 0.05f) dt = 0.05f

        state.update(dt)
        render()
    }

    private fun render() {
        var canvas: Canvas? = null
        try {
            canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.lockHardwareCanvas()
            } else {
                holder.lockCanvas()
            }
            if (canvas == null) return
            synchronized(holder) {
                state.draw(canvas, canvas.width, canvas.height, hudHeightPx())
            }
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: IllegalStateException) {
                    // Surface destroyed mid-frame; the loop stops on surfaceDestroyed.
                }
            }
        }
    }
}
