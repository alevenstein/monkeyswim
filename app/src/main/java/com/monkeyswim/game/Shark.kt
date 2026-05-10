package com.monkeyswim.game

import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Powerup-summoned shark. Tile-aligned movement like a piranha, but always
 * chases the nearest non-eaten piranha and isn't subject to fright/eaten/pen
 * modes itself. The shark hunts piranhas only — it ignores the monkey.
 */
class Shark(
    private val maze: Maze,
    spawnCol: Int,
    spawnRow: Int,
) {
    var x: Float = spawnCol + 0.5f
        private set
    var y: Float = spawnRow + 0.5f
        private set
    var direction: Direction = Direction.RIGHT
        private set

    private var animTime: Float = 0f
    val frame: Int get() = ((animTime * 6f).toInt() % 2)
    val tileCol: Int get() = x.toInt()
    val tileRow: Int get() = y.toInt()

    private val baseSpeed = 5.5f

    fun update(deltaSec: Float, piranhas: List<Piranha>) {
        animTime += deltaSec
        val target = nearestTarget(piranhas) ?: return

        val nearCenter = abs(x - (tileCol + 0.5f)) < 0.05f && abs(y - (tileRow + 0.5f)) < 0.05f
        if (nearCenter) {
            val newDir = pickDirection(target)
            if (newDir != Direction.NONE) direction = newDir
        }
        if (direction == Direction.NONE) return

        var remaining = baseSpeed * deltaSec
        while (remaining > 0f) {
            val step = remaining.coerceAtMost(0.45f)
            stepBy(step, target)
            remaining -= step
        }
    }

    private fun stepBy(distance: Float, target: Piranha) {
        var nx = x + direction.dx * distance
        var ny = y + direction.dy * distance
        val leadCol = (nx + direction.dx * 0.5f).toInt()
        val leadRow = (ny + direction.dy * 0.5f).toInt()
        if (!maze.isPiranhaWalkable(leadCol, leadRow)) {
            nx = tileCol + 0.5f
            ny = tileRow + 0.5f
            direction = pickDirection(target).takeUnless { it == Direction.NONE } ?: Direction.UP
        }
        x = nx; y = ny
        if (y < 0f) y = maze.rows - 0.001f
        else if (y >= maze.rows) y = 0.001f
    }

    private fun nearestTarget(piranhas: List<Piranha>): Piranha? {
        var best: Piranha? = null
        var bestDist = Float.MAX_VALUE
        for (p in piranhas) {
            if (p.mode == Piranha.Mode.EATEN) continue
            val dx = p.x - x
            val dy = p.y - y
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = p
            }
        }
        return best
    }

    private fun pickDirection(target: Piranha): Direction {
        val tx = target.tileCol
        val ty = target.tileRow
        var bestDir = Direction.NONE
        var bestDist = Float.MAX_VALUE
        val order = listOf(Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT)
        val reverse = direction.opposite()
        for (d in order) {
            if (d == reverse && direction != Direction.NONE) continue
            val ncol = tileCol + d.dx
            val nrow = tileRow + d.dy
            if (!maze.isPiranhaWalkable(ncol, nrow)) continue
            val ddx = (ncol - tx).toFloat()
            val ddy = (nrow - ty).toFloat()
            val dist = ddx * ddx + ddy * ddy
            if (dist < bestDist) {
                bestDist = dist
                bestDir = d
            }
        }
        if (bestDir == Direction.NONE) return reverse
        return bestDir
    }

    fun overlapsWith(piranha: Piranha, threshold: Float = 0.6f): Boolean {
        val dx = x - piranha.x
        val dy = y - piranha.y
        return sqrt(dx * dx + dy * dy) < threshold
    }

    fun draw(canvas: Canvas, cellSize: Float, originX: Float, originY: Float) {
        val cx = originX + x * cellSize
        val cy = originY + y * cellSize
        SpriteRenderer.drawShark(canvas, cx, cy, cellSize, direction, frame)
    }
}
