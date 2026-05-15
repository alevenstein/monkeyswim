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

    /** Most recent direction the player explicitly swiped — independent of
     *  whether it committed via the queued-direction system. Used by the
     *  current-tile speed logic to tell "actively swimming WITH the current"
     *  (player swiped in cd → +50%) from "passively drifting" (override
     *  forced direction to cd → 0.5x). Reset on respawn. */
    private var lastRequestedDirection: Direction = Direction.NONE

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
        lastRequestedDirection = Direction.NONE
        animTime = 0f
    }

    val tileCol: Int get() = x.toInt()
    val tileRow: Int get() = y.toInt()

    fun update(deltaSec: Float) {
        animTime += deltaSec
        val speed = baseSpeed * speedScale * currentMultiplier()
        var remaining = speed * deltaSec
        // Substep so we never skip past a tile center.
        while (remaining > 0f) {
            val step = remaining.coerceAtMost(0.45f)
            stepBy(step)
            remaining -= step
        }
    }

    /** Current-tile speed modifier. Three cases, all keyed off the tile's
     *  current direction `cd`:
     *   • Actively swimming WITH the flow (last player swipe was `cd`) → 1.5×.
     *   • Actively swimming AGAINST the flow (direction == `cd.opposite()`,
     *     which only happens via a deliberate swipe) → 0.5×.
     *   • Anything else — perpendicular swipe forced-overridden to `cd`, or
     *     drifting with the flow without an active matching swipe — is
     *     "passive drift" at 0.5×. The player has to manually swipe in the
     *     current's direction to get the speed boost.
     *  Off-current tiles return 1× regardless. */
    private fun currentMultiplier(): Float {
        val cd = maze.currentDirAt(tileCol, tileRow) ?: return 1f
        return when {
            direction == cd && lastRequestedDirection == cd -> 1.5f
            direction == cd.opposite() -> 0.5f
            else -> 0.5f
        }
    }

    private fun stepBy(distance: Float) {
        // Lily-pad lock: while the monkey is actively sliding on a lily pad
        // (direction != NONE), queued turns don't commit — slippery. BUT if
        // the monkey has wall-stopped on a lily pad and is now stationary,
        // we DO let the swipe commit; otherwise the player would be stranded
        // (the lily pad blocks turning, but they have to start moving from
        // somewhere). Queued input is preserved either way so a perpendicular
        // swipe takes effect at the first valid junction after slide-off.
        val onLilyPad = maze.isLilyPad(tileCol, tileRow)
        val canCommitQueued = !onLilyPad || direction == Direction.NONE

        if (canCommitQueued && queuedDirection != Direction.NONE && queuedDirection != direction) {
            if (canTurnTo(queuedDirection)) {
                snapToLane(queuedDirection)
                direction = queuedDirection
                queuedDirection = Direction.NONE
            }
        }

        // Current push: while on a current tile, the flow pushes the monkey
        // in its direction — BUT only when there's no real choice. If the
        // monkey's perpendicular direction leads to a walkable side corridor
        // the player gets to exit the current there. If it leads to a wall
        // (or the monkey has no direction at all), the current takes over.
        // Moving with or against the flow always overrides the push (handled
        // by the != cd && != cd.opposite() check).
        val cd = maze.currentDirAt(tileCol, tileRow)
        if (cd != null && direction != cd && direction != cd.opposite()) {
            val targetCol = tileCol + direction.dx
            val targetRow = tileRow + direction.dy
            val perpendicularBlocked = direction == Direction.NONE ||
                !maze.isMonkeyWalkable(targetCol, targetRow)
            if (perpendicularBlocked) {
                direction = cd
                snapToLane(cd)
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
        // Record the player's intent regardless of whether the turn actually
        // commits — currentMultiplier() reads this to decide whether the
        // player is actively swimming WITH a current (1.5×) or being passively
        // drifted by it (0.5×).
        lastRequestedDirection = dir
        if (dir == direction) {
            queuedDirection = Direction.NONE
            return
        }
        // Allow immediate reverse without snap — unless we're on a lily pad,
        // in which case the slippery rule overrides and the swipe just
        // queues for after slide-off.
        if (dir == direction.opposite() && direction != Direction.NONE &&
            !maze.isLilyPad(tileCol, tileRow)
        ) {
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
