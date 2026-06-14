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
 * On top of vsync timing, the simulation runs on a **fixed timestep** via an
 * accumulator: real elapsed time is banked and the world advances in constant
 * [TICK]-sized steps, so movement is perfectly uniform regardless of how the
 * frame intervals jitter. Whatever fraction of a tick is left over becomes the
 * interpolation factor [alpha] handed to `draw`, so sprites render *between*
 * the last two simulated positions — smooth even when the render rate and the
 * tick rate differ (e.g. 120 Hz panel, 120 Hz ticks, or a dropped frame).
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
    private var accumulator = 0f

    fun start() {
        thread.start()
        handler = Handler(thread.looper)
        handler.post {
            // Choreographer is per-thread; grab the instance for the loop thread.
            choreographer = Choreographer.getInstance()
            running = true
            lastFrameNanos = 0L
            accumulator = 0f
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
        // Real elapsed time since the last frame. Clamp so a GC pause or
        // app-switch can't bank a huge backlog of ticks.
        var dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = frameTimeNanos
        if (dt > 0.1f) dt = 0.1f

        // Fixed-timestep accumulator: advance the world in constant TICK steps.
        accumulator += dt
        var steps = 0
        while (accumulator >= TICK && steps < MAX_STEPS) {
            state.update(TICK)
            accumulator -= TICK
            steps++
        }
        // If we hit the step cap we're behind; drop the backlog rather than
        // spiralling (the next frame's dt clamp keeps things bounded).
        if (steps == MAX_STEPS && accumulator > TICK) accumulator = 0f

        // Leftover fraction of a tick → interpolation factor for rendering.
        val alpha = (accumulator / TICK).coerceIn(0f, 1f)
        render(alpha)
    }

    private fun render(alpha: Float) {
        var canvas: Canvas? = null
        try {
            canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.lockHardwareCanvas()
            } else {
                holder.lockCanvas()
            }
            if (canvas == null) return
            synchronized(holder) {
                state.draw(canvas, canvas.width, canvas.height, hudHeightPx(), alpha)
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

    companion object {
        /** Fixed simulation step: 120 Hz. Fine-grained enough for crisp
         *  collisions; rendering interpolates between ticks so it stays smooth
         *  on any refresh rate. */
        private const val TICK = 1f / 120f
        /** Max ticks to simulate per frame, so a hitch can't snowball. */
        private const val MAX_STEPS = 6
    }
}
