package com.monkeyswim.game

import android.graphics.Canvas
import kotlin.math.abs

/**
 * Slow patrolling predator. Unlike piranhas, a crocodile doesn't chase — it
 * marches back and forth along a fixed axis (horizontal or vertical), bouncing
 * off walls. Touch from a crocodile costs the monkey a life regardless of
 * fright mode; power pellets don't frighten crocodiles.
 *
 * The patrol axis is inferred from the spawn cell's walkable neighbours at
 * construction time: if both LEFT and RIGHT neighbours are walkable, axis =
 * horizontal; otherwise if both UP and DOWN are walkable, axis = vertical;
 * otherwise the croc just sits still (shouldn't happen in a valid layout).
 */
class Crocodile(
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

    private val spawnX = spawnCol + 0.5f
    private val spawnY = spawnRow + 0.5f

    /** Half of the piranha base speed — crocs are deliberately slow so the
     *  player can time their crossing. */
    private val baseSpeed = 2.5f
    var speedScale: Float = 1f

    private var animTime: Float = 0f
    val frame: Int get() = ((animTime * 4f).toInt() % 2)

    init {
        // Pick patrol axis from spawn cell neighbours. Prefer horizontal.
        val horiz = maze.isPiranhaWalkable(spawnCol - 1, spawnRow) &&
            maze.isPiranhaWalkable(spawnCol + 1, spawnRow)
        val vert = maze.isPiranhaWalkable(spawnCol, spawnRow - 1) &&
            maze.isPiranhaWalkable(spawnCol, spawnRow + 1)
        direction = when {
            horiz -> Direction.RIGHT
            vert -> Direction.DOWN
            else -> Direction.NONE
        }
    }

    fun resetToSpawn() {
        x = spawnX
        y = spawnY
        animTime = 0f
        // Direction preserved across resets — the croc just keeps doing its thing.
    }

    val tileCol: Int get() = x.toInt()
    val tileRow: Int get() = y.toInt()

    fun update(deltaSec: Float) {
        animTime += deltaSec
        if (direction == Direction.NONE) return
        val speed = baseSpeed * speedScale
        var remaining = speed * deltaSec
        while (remaining > 0f) {
            val step = remaining.coerceAtMost(0.45f)
            stepBy(step)
            remaining -= step
        }
    }

    private fun stepBy(distance: Float) {
        var nx = x + direction.dx * distance
        var ny = y + direction.dy * distance

        val leadCol = (nx + direction.dx * 0.5f).toInt()
        val leadRow = (ny + direction.dy * 0.5f).toInt()
        if (!maze.isPiranhaWalkable(leadCol, leadRow)) {
            // Snap to centre and reverse.
            nx = tileCol + 0.5f
            ny = tileRow + 0.5f
            direction = direction.opposite()
        }

        x = nx
        y = ny
    }

    /** True if the crocodile and the monkey share a tile — used by GameState
     *  to trigger a life loss. */
    fun overlapsWith(monkey: Monkey): Boolean {
        val dx = abs(x - monkey.x)
        val dy = abs(y - monkey.y)
        return dx < 0.6f && dy < 0.6f
    }

    fun draw(canvas: Canvas, cellSize: Float, originX: Float, originY: Float) {
        val cx = originX + x * cellSize
        val cy = originY + y * cellSize
        SpriteRenderer.drawCrocodile(canvas, cx, cy, cellSize, direction, frame)
    }
}
