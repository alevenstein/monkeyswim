package com.monkeyswim.game

import android.graphics.Canvas
import android.os.Build
import android.view.SurfaceHolder

/**
 * Game loop running off the UI thread. Maintains a fixed-timestep update with
 * variable-rate rendering. Stops cleanly when [running] is set false.
 */
class GameThread(
    private val holder: SurfaceHolder,
    private val state: GameState,
    private val hudHeightPx: () -> Float,
) : Thread("MonkeySwim-Game") {

    @Volatile
    var running: Boolean = false

    private val targetFps = 60
    private val frameNanos: Long = 1_000_000_000L / targetFps

    override fun run() {
        var lastTime = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastTime).coerceAtMost(50_000_000L)) / 1_000_000_000f
            lastTime = now

            state.update(dt)

            var canvas: Canvas? = null
            try {
                val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                canvas = locked ?: continue
                synchronized(holder) {
                    state.draw(canvas, canvas.width, canvas.height, hudHeightPx())
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: IllegalStateException) {
                        // Surface destroyed mid-frame; will exit loop next iteration.
                    }
                }
            }

            // Pace the loop.
            val frameTime = System.nanoTime() - now
            val sleepNs = frameNanos - frameTime
            if (sleepNs > 0) {
                try {
                    sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }
}
