package com.monkeyswim.game

import android.graphics.Canvas

/**
 * Player-controlled monkey. Tile-aligned movement with a queued-direction system:
 * the player can request a turn and it commits the next time the monkey is on a
 * tile boundary aligned with that direction.
 */
class Monkey(
    private val maze: Maze,
    spawnCol: Int,
    spawnRow: Int,
) {
    /** Continuous position in tile units (col, row). 0..cols x 0..rows. */
    var x: Float = spawnCol + 0.5f
        private set
    var y: Float = spawnRow + 0.5f
        private set

    var direction: Direction = Direction.NONE
        private set
    var queuedDirection: Direction = Direction.NONE
    private val initialDirection = Direction.NONE

    /** Tiles per second. Pac-Man moves about 11 tiles/sec at full speed. */
    private val baseSpeed = 6.75f
    var speedScale: Float = 1f

    /** Animation frame timer (cycles 0..1). */
    private var animTime: Float = 0f
    val frame: Int get() = ((animTime * 8f).toInt() % 2)

    fun resetTo(col: Int, row: Int) {
        x = col + 0.5f
        y = row + 0.5f
        direction = initialDirection
        queuedDirection = Direction.NONE
        animTime = 0f
    }

    val tileCol: Int get() = x.toInt()
    val tileRow: Int get() = y.toInt()

    fun update(deltaSec: Float) {
        animTime += deltaSec
        val speed = baseSpeed * speedScale
        var remaining = speed * deltaSec
        // Substep so we never skip past a tile center.
        while (remaining > 0f) {
            val step = remaining.coerceAtMost(0.45f)
            stepBy(step)
            remaining -= step
        }
    }

    private fun stepBy(distance: Float) {
        // Try to apply queued direction at tile centers if it's now valid.
        if (queuedDirection != Direction.NONE && queuedDirection != direction) {
            if (canTurnTo(queuedDirection)) {
                snapToLane(queuedDirection)
                direction = queuedDirection
                queuedDirection = Direction.NONE
            }
        }

        if (direction == Direction.NONE) return

        // Compute proposed new position.
        var nx = x + direction.dx * distance
        var ny = y + direction.dy * distance

        // Tile we'd be heading into.
        val ahead = aheadTile(nx, ny, direction)
        if (!maze.isMonkeyWalkable(ahead.first, ahead.second)) {
            // Stop at center of current tile.
            nx = tileCol + 0.5f
            ny = tileRow + 0.5f
            direction = Direction.NONE
        }

        x = nx
        y = ny

        // Tunnel wrap (top/bottom edges of the maze).
        if (y < 0f) y = maze.rows - 0.001f
        else if (y >= maze.rows) y = 0.001f
    }

    /** True if turning to dir from current tile is legal (next tile is walkable). */
    private fun canTurnTo(dir: Direction): Boolean {
        // For an axis-perpendicular turn, the monkey must be near the center of the
        // current lane on the perpendicular axis.
        val centerCol = tileCol + 0.5f
        val centerRow = tileRow + 0.5f
        when (dir) {
            Direction.LEFT, Direction.RIGHT -> if (kotlin.math.abs(y - centerRow) > 0.20f) return false
            Direction.UP, Direction.DOWN -> if (kotlin.math.abs(x - centerCol) > 0.20f) return false
            Direction.NONE -> return false
        }
        val targetCol = tileCol + dir.dx
        val targetRow = tileRow + dir.dy
        return maze.isMonkeyWalkable(targetCol, targetRow)
    }

    /** Snap onto the centerline of the lane orthogonal to [dir]. */
    private fun snapToLane(dir: Direction) {
        if (dir.isHorizontal) y = tileRow + 0.5f
        if (dir.isVertical) x = tileCol + 0.5f
    }

    private fun aheadTile(nx: Float, ny: Float, dir: Direction): Pair<Int, Int> {
        // Next tile in the direction of travel based on the leading edge of the entity.
        val leadingX = nx + dir.dx * 0.5f
        val leadingY = ny + dir.dy * 0.5f
        return leadingX.toInt() to leadingY.toInt()
    }

    fun requestDirection(dir: Direction) {
        if (dir == Direction.NONE) return
        if (dir == direction) {
            queuedDirection = Direction.NONE
            return
        }
        // Allow immediate reverse without snap.
        if (dir == direction.opposite() && direction != Direction.NONE) {
            direction = dir
            queuedDirection = Direction.NONE
            return
        }
        queuedDirection = dir
    }

    fun draw(canvas: Canvas, cellSize: Float, originX: Float, originY: Float) {
        val cx = originX + x * cellSize
        val cy = originY + y * cellSize
        SpriteRenderer.drawMonkey(canvas, cx, cy, cellSize, direction, frame)
    }
}
