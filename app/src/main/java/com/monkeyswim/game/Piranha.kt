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
    private val releaseDelay: Float = 0f,
) {
    enum class Personality { DIRECT, AHEAD2, AHEAD4, ROAMER }
    enum class Mode { CHASE, FRIGHTENED, EATEN, LEAVING_PEN }

    var x: Float = spawnCol + 0.5f
        private set
    var y: Float = spawnRow + 0.5f
        private set

    var direction: Direction = Direction.LEFT
        private set

    var mode: Mode = Mode.LEAVING_PEN

    private val spawnX = spawnCol + 0.5f
    private val spawnY = spawnRow + 0.5f

    /**
     * BFS shortest-path flow field from this piranha's spawn tile. Maps every
     * reachable walkable tile to the direction it should step to get one tile
     * closer to spawn along the shortest path. Used only by EATEN-mode return:
     * the previous greedy distance-minimization could oscillate or take grand-
     * tour routes when the maze topology didn't align with Manhattan distance,
     * which became visible once the shark + black-hole powerups started killing
     * piranhas in arbitrary positions.
     */
    private val homeFlow: Map<Pair<Int, Int>, Direction> = computeHomeFlow(spawnCol, spawnRow)

    private fun computeHomeFlow(spawnCol: Int, spawnRow: Int): Map<Pair<Int, Int>, Direction> {
        val flow = HashMap<Pair<Int, Int>, Direction>()
        val target = spawnCol to spawnRow
        val visited = HashSet<Pair<Int, Int>>().apply { add(target) }
        val queue = ArrayDeque<Pair<Int, Int>>().apply { add(target) }
        val dirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (d in dirs) {
                val nx = current.first + d.dx
                val ny = current.second + d.dy
                if (nx < 0 || nx >= maze.cols || ny < 0 || ny >= maze.rows) continue
                val tile = nx to ny
                if (tile in visited) continue
                if (!maze.isPiranhaWalkable(nx, ny)) continue
                // The neighbor's optimal next-direction is the OPPOSITE of how
                // we got here — stepping that way moves it toward `current`,
                // which is one tile closer to the BFS root (the spawn).
                flow[tile] = d.opposite()
                visited.add(tile)
                queue.addLast(tile)
            }
        }
        return flow
    }

    private val baseChaseSpeed = 6.15f
    private val frightSpeed = 3.375f
    private val eatenSpeed = 12f

    var speedScale: Float = 1f
    private var animTime: Float = 0f
    private var releaseTimer: Float = releaseDelay
    val frame: Int get() = ((animTime * 6f).toInt() % 2)

    fun resetToSpawn() {
        x = spawnX
        y = spawnY
        direction = Direction.LEFT
        mode = Mode.LEAVING_PEN
        releaseTimer = releaseDelay
        animTime = 0f
    }

    val tileCol: Int get() = x.toInt()
    val tileRow: Int get() = y.toInt()

    fun update(deltaSec: Float, monkey: Monkey, frightTimer: Float) {
        animTime += deltaSec

        // Wait inside the pen until the staggered-release timer expires.
        if (releaseTimer > 0f) {
            releaseTimer -= deltaSec
            return
        }

        // Mode sync. LEAVING_PEN sticks until the piranha clears the pen tiles;
        // EATEN sticks until the piranha is back at its spawn, then re-enters
        // LEAVING_PEN so it has to swim out through the door again.
        when (mode) {
            Mode.LEAVING_PEN -> {
                if (!maze.isPenTile(tileCol, tileRow)) {
                    mode = if (frightTimer > 0f) Mode.FRIGHTENED else Mode.CHASE
                }
            }
            Mode.EATEN -> {
                val dx = x - spawnX
                val dy = y - spawnY
                if (dx * dx + dy * dy < 0.04f) {
                    x = spawnX; y = spawnY
                    mode = Mode.LEAVING_PEN
                }
            }
            else -> {
                mode = if (frightTimer > 0f) Mode.FRIGHTENED else Mode.CHASE
            }
        }

        val speed = when (mode) {
            Mode.FRIGHTENED -> frightSpeed
            Mode.EATEN -> eatenSpeed
            Mode.LEAVING_PEN, Mode.CHASE -> baseChaseSpeed * speedScale
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
        if (mode == Mode.LEAVING_PEN) return maze.penExitTile
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
        // EATEN piranhas follow the precomputed BFS shortest-path flow back to
        // their spawn — no greedy / no-reverse oscillation. Falls through to
        // the greedy logic below if the flow somehow doesn't have an entry
        // (e.g. piranha is on the spawn tile itself, in which case the
        // EATEN-mode arrival check in update() handles the snap-and-flip).
        if (mode == Mode.EATEN) {
            homeFlow[tileCol to tileRow]?.let { return it }
        }
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
