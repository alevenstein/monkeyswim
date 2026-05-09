package com.monkeyswim.game

import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * AI-controlled piranha. At each tile boundary picks the direction (excluding the
 * reverse) that minimises distance-squared to its current target. Has CHASE,
 * FRIGHTENED, and EATEN modes.
 */
class Piranha(
    private val maze: Maze,
    val personality: Personality,
    spawnCol: Int,
    spawnRow: Int,
) {
    enum class Personality { DIRECT, AHEAD2, AHEAD4, ROAMER }
    enum class Mode { CHASE, FRIGHTENED, EATEN }

    var x: Float = spawnCol + 0.5f
        private set
    var y: Float = spawnRow + 0.5f
        private set

    var direction: Direction = Direction.LEFT
        private set

    var mode: Mode = Mode.CHASE

    private val spawnX = spawnCol + 0.5f
    private val spawnY = spawnRow + 0.5f

    private val baseChaseSpeed = 8.2f
    private val frightSpeed = 4.5f
    private val eatenSpeed = 16f

    var speedScale: Float = 1f
    private var animTime: Float = 0f
    val frame: Int get() = ((animTime * 6f).toInt() % 2)

    fun resetToSpawn() {
        x = spawnX
        y = spawnY
        direction = Direction.LEFT
        mode = Mode.CHASE
        animTime = 0f
    }

    val tileCol: Int get() = x.toInt()
    val tileRow: Int get() = y.toInt()

    fun update(deltaSec: Float, monkey: Monkey, frightTimer: Float) {
        animTime += deltaSec

        // Sync mode with global frighten state — but if EATEN, stay EATEN until home.
        if (mode != Mode.EATEN) {
            mode = if (frightTimer > 0f) Mode.FRIGHTENED else Mode.CHASE
        } else {
            // Check if returned home (close to spawn).
            val dx = x - spawnX
            val dy = y - spawnY
            if (dx * dx + dy * dy < 0.04f) {
                mode = if (frightTimer > 0f) Mode.FRIGHTENED else Mode.CHASE
                x = spawnX; y = spawnY
            }
        }

        val speed = when (mode) {
            Mode.FRIGHTENED -> frightSpeed
            Mode.EATEN -> eatenSpeed
            Mode.CHASE -> baseChaseSpeed * speedScale
        }
        var remaining = speed * deltaSec
        while (remaining > 0f) {
            val step = remaining.coerceAtMost(0.45f)
            stepBy(step, monkey)
            remaining -= step
        }
    }

    private fun stepBy(distance: Float, monkey: Monkey) {
        // At a tile center, decide next direction.
        val nearCenter = abs(x - (tileCol + 0.5f)) < 0.05f && abs(y - (tileRow + 0.5f)) < 0.05f
        if (nearCenter) {
            val newDir = pickDirection(monkey)
            if (newDir != Direction.NONE) direction = newDir
        }

        if (direction == Direction.NONE) return

        var nx = x + direction.dx * distance
        var ny = y + direction.dy * distance

        // Wall check ahead.
        val leadCol = (nx + direction.dx * 0.5f).toInt()
        val leadRow = (ny + direction.dy * 0.5f).toInt()
        if (!maze.isPiranhaWalkable(leadCol, leadRow)) {
            nx = tileCol + 0.5f
            ny = tileRow + 0.5f
            // Force re-pick on next tick by aligning to centre.
            direction = pickDirection(monkey).takeUnless { it == Direction.NONE } ?: Direction.UP
        }

        x = nx; y = ny

        if (x < 0f) x = maze.cols - 0.001f
        else if (x >= maze.cols) x = 0.001f
    }

    /** Choose a target tile based on personality + mode. */
    private fun targetTile(monkey: Monkey): Pair<Int, Int> {
        if (mode == Mode.EATEN) return spawnX.toInt() to spawnY.toInt()
        if (mode == Mode.FRIGHTENED) {
            // Random target far away — bias to opposite of monkey.
            return (maze.cols - monkey.tileCol) to (maze.rows - monkey.tileRow)
        }
        val md = monkey.direction
        return when (personality) {
            Personality.DIRECT -> monkey.tileCol to monkey.tileRow
            Personality.AHEAD2 -> (monkey.tileCol + md.dx * 2) to (monkey.tileRow + md.dy * 2)
            Personality.AHEAD4 -> (monkey.tileCol + md.dx * 4) to (monkey.tileRow + md.dy * 4)
            Personality.ROAMER -> {
                val dx = monkey.tileCol - tileCol
                val dy = monkey.tileRow - tileRow
                if (dx * dx + dy * dy > 64) monkey.tileCol to monkey.tileRow
                else 1 to (maze.rows - 2)  // run to bottom-left corner
            }
        }
    }

    /** Pick best direction at a junction, excluding reverse and walls. */
    private fun pickDirection(monkey: Monkey): Direction {
        val (tx, ty) = targetTile(monkey)
        var bestDir = Direction.NONE
        var bestDist = Float.MAX_VALUE
        // Pac-Man tie-break order: UP, LEFT, DOWN, RIGHT
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
        if (bestDir == Direction.NONE) {
            // Dead end — allow reverse.
            return reverse
        }
        return bestDir
    }

    fun overlapsWith(monkey: Monkey, threshold: Float = 0.55f): Boolean {
        val dx = x - monkey.x
        val dy = y - monkey.y
        return sqrt(dx * dx + dy * dy) < threshold
    }

    fun markEaten() {
        mode = Mode.EATEN
    }

    fun draw(canvas: Canvas, cellSize: Float, originX: Float, originY: Float, frightTimer: Float) {
        val cx = originX + x * cellSize
        val cy = originY + y * cellSize
        val frightened = mode == Mode.FRIGHTENED
        // Blink white during last 1.5s of fright.
        val blink = frightened && frightTimer < 1.5f && (((frightTimer * 8f).toInt()) % 2 == 0)
        if (mode == Mode.EATEN) {
            // Eyes-only: tiny white dots heading home.
            SpriteRenderer.drawPiranha(canvas, cx, cy, cellSize * 0.45f, direction, frame, true, true)
        } else {
            SpriteRenderer.drawPiranha(canvas, cx, cy, cellSize, direction, frame, frightened, blink)
        }
    }
}
