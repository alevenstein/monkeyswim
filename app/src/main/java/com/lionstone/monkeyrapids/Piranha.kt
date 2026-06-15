package com.lionstone.monkeyrapids

import android.graphics.Canvas
import android.graphics.Color
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

    /** Per-personality body tint so the four piranhas are visually distinct
     *  (they keep the shared red belly + teeth, so they still read as a school
     *  of piranhas). Frightened mode overrides this with the scared blue. */
    val bodyColor: Int = when (personality) {
        Personality.DIRECT -> Color.parseColor("#C0392B") // red
        Personality.AHEAD2 -> Color.parseColor("#2E86C1") // blue
        Personality.AHEAD4 -> Color.parseColor("#27AE60") // green
        Personality.ROAMER -> Color.parseColor("#E67E22") // orange
    }

    var x: Float = spawnCol + 0.5f
        private set
    var y: Float = spawnRow + 0.5f
        private set

    // Start-of-tick position for render interpolation (see renderX/renderY).
    private var prevX: Float = x
    private var prevY: Float = y

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
        prevX = x
        prevY = y
        direction = Direction.LEFT
        mode = Mode.LEAVING_PEN
        releaseTimer = releaseDelay
        animTime = 0f
    }

    val tileCol: Int get() = x.toInt()
    val tileRow: Int get() = y.toInt()

    /** Interpolated render coordinates between the last two ticks. */
    fun renderX(alpha: Float): Float = renderLerp(prevX, x, alpha)
    fun renderY(alpha: Float): Float = renderLerp(prevY, y, alpha)

    /** Per-frame bait position (or null). GameState sets this before update().
     *  When non-null AND the piranha is in CHASE AND the bait is within
     *  BAIT_RANGE_SQ tiles², targetTile picks the bait instead of the monkey. */
    var currentBait: Pair<Int, Int>? = null

    /** Per-frame black-hole position (or null). GameState sets this before
     *  update() while a black hole is active. When non-null it overrides the
     *  CHASE/FRIGHTENED target so the piranha is dragged toward the hole from
     *  across the maze (at a reduced speed) until it's consumed on arrival. */
    var blackHolePull: Pair<Int, Int>? = null

    fun update(deltaSec: Float, monkey: Monkey, frightTimer: Float) {
        prevX = x
        prevY = y
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
                    prevX = x; prevY = y
                    // Regroup in the pen before re-emerging, rather than turning
                    // straight back around the moment it gets home.
                    mode = Mode.LEAVING_PEN
                    releaseTimer = REENTRY_DELAY
                    return
                }
            }
            else -> {
                // CHASE / FRIGHTENED. Safety net: if a piranha somehow ends up
                // standing on a pen tile (lightning reset, future teleport
                // powerups, edge cases), flip back to LEAVING_PEN so it heads
                // out the door instead of orbiting the monkey from inside the
                // pen. Clearing direction bypasses the reverse-exclusion in
                // pickDirection so the next pick can step straight toward the
                // exit even from the door tile itself.
                if (maze.isPenTile(tileCol, tileRow)) {
                    mode = Mode.LEAVING_PEN
                    direction = Direction.NONE
                } else {
                    mode = if (frightTimer > 0f) Mode.FRIGHTENED else Mode.CHASE
                }
            }
        }

        val baseSpeed = when (mode) {
            Mode.FRIGHTENED -> frightSpeed
            Mode.EATEN -> eatenSpeed
            Mode.LEAVING_PEN, Mode.CHASE -> baseChaseSpeed * speedScale
        }
        // Currents modify chase/frightened movement the same way they affect
        // the monkey (+50% with, -50% against). EATEN piranhas are immune so
        // their BFS return-home path isn't disturbed.
        // Black hole drag: a piranha being pulled toward an active black hole
        // crawls along at a fraction of its normal pace so the pull reads as a
        // slow gravitational drag rather than an ordinary chase.
        val pullScale =
            if (blackHolePull != null && (mode == Mode.CHASE || mode == Mode.FRIGHTENED))
                BLACK_HOLE_DRAG_SCALE
            else 1f
        val speed = (if (mode == Mode.EATEN) baseSpeed else baseSpeed * currentMultiplier()) * pullScale
        var remaining = speed * deltaSec
        while (remaining > 0f) {
            val step = remaining.coerceAtMost(0.45f)
            stepBy(step, monkey)
            remaining -= step
        }
    }

    private fun stepBy(distance: Float, monkey: Monkey) {
        // At a tile center, decide next direction — but not while sliding on
        // a lily pad. EATEN piranhas are exempt from the lock (their BFS
        // return path is what keeps them on track to spawn).
        val nearCenter = abs(x - (tileCol + 0.5f)) < 0.05f && abs(y - (tileRow + 0.5f)) < 0.05f
        val onLilyPad = mode != Mode.EATEN && maze.isLilyPad(tileCol, tileRow)
        if (nearCenter && !onLilyPad) {
            val newDir = pickDirection(monkey)
            if (newDir != Direction.NONE) direction = newDir
        }

        // Current push: same rule as the monkey — flow pushes the piranha in
        // its direction only when the perpendicular path is blocked (or the
        // piranha has no direction). A perpendicular AI pick into a walkable
        // side path is honoured. EATEN piranhas are exempt: their BFS flow
        // field back to spawn shouldn't be disrupted by surface currents.
        if (mode != Mode.EATEN) {
            val cd = maze.currentDirAt(tileCol, tileRow)
            if (cd != null && direction != cd && direction != cd.opposite()) {
                val targetCol = tileCol + direction.dx
                val targetRow = tileRow + direction.dy
                val perpendicularBlocked = direction == Direction.NONE ||
                    !maze.isPiranhaWalkable(targetCol, targetRow)
                if (perpendicularBlocked) {
                    direction = cd
                }
            }
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

        // Tunnel wrap (top/bottom edges of the maze).
        if (y < 0f) y = maze.rows - 0.001f
        else if (y >= maze.rows) y = 0.001f
    }

    /** Choose a target tile based on personality + mode. */
    private fun targetTile(monkey: Monkey): Pair<Int, Int> {
        if (mode == Mode.EATEN) return spawnX.toInt() to spawnY.toInt()
        if (mode == Mode.LEAVING_PEN) return maze.penExitTile
        // Black hole gravity overrides the normal chase/flee target: a piranha
        // out in the maze homes in on the hole and gets consumed on arrival.
        blackHolePull?.let { return it }
        if (mode == Mode.FRIGHTENED) {
            // Random target far away — bias to opposite of monkey.
            return (maze.cols - monkey.tileCol) to (maze.rows - monkey.tileRow)
        }
        // CHASE mode: if a bait is on the board, re-target it. The piranha
        // doesn't home perfectly (greedy junctions still apply), but it'll
        // generally detour. The lure reaches across the whole maze — an 8-tile
        // radius was small enough that on the 22×15 layout the scattered
        // piranhas were usually out of range, so dropping bait appeared to do
        // nothing. Once bait is consumed (GameState clears it on contact) the
        // piranha falls back to the personality-driven monkey target.
        val cb = currentBait
        if (cb != null) {
            val dx = cb.first - tileCol
            val dy = cb.second - tileRow
            if (dx * dx + dy * dy <= BAIT_RANGE_SQ) return cb
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
        // CHASE/FRIGHTENED escape from pruned tiles: if we're standing in a
        // dead-end pocket (typically the pen-exit funnel, immediately after
        // LEAVING_PEN transitions us to CHASE on the cell above the door),
        // ignore the monkey target and follow the precomputed escape
        // direction toward the live skeleton. Bypasses the reverse-exclusion
        // because the escape is by definition outward from where we came.
        if (mode == Mode.CHASE || mode == Mode.FRIGHTENED) {
            maze.chaseEscapeAt(tileCol, tileRow)?.let { return it }
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
            // CHASE/FRIGHTENED guards (LEAVING_PEN and EATEN unaffected —
            // they need the pen / use the precomputed home-flow):
            //   * Hard block: never step into a pen tile (re-entry
            //     forbidden — fixes the L2 in-pen trap where the greedy
            //     heuristic walks a loose piranha through the door and
            //     traps it in an interior cycle).
            //   * Soft block: don't dive from the live skeleton INTO a
            //     pruned dead-end pocket (fixes the L3 funnel where the
            //     greedy heuristic descends (7,9)→(7,10) and oscillates).
            //     Allowed in reverse: a piranha already on a pruned tile
            //     (just transitioned from LEAVING_PEN on the pen-exit cell)
            //     can step onto another pruned tile on its way out — that
            //     case is normally handled by `chaseEscapeAt` short-circuit
            //     above, but the soft-block check kept here is a safety
            //     net in case the escape direction is null.
            if (mode == Mode.CHASE || mode == Mode.FRIGHTENED) {
                if (maze.isPenTile(ncol, nrow)) continue
                if (!maze.isLiveChaseTile(ncol, nrow) &&
                    maze.isLiveChaseTile(tileCol, tileRow)) continue
            }
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

    fun draw(canvas: Canvas, cellSize: Float, originX: Float, originY: Float, frightTimer: Float, alpha: Float) {
        val cx = originX + renderX(alpha) * cellSize
        val cy = originY + renderY(alpha) * cellSize
        val frightened = mode == Mode.FRIGHTENED
        // Blink white during last 1.5s of fright.
        val blink = frightened && frightTimer < 1.5f && (((frightTimer * 8f).toInt()) % 2 == 0)
        if (mode == Mode.EATEN) {
            // Eyes-only: tiny white dots heading home.
            SpriteRenderer.drawPiranha(canvas, cx, cy, cellSize * 0.45f, direction, frame, true, true)
        } else {
            SpriteRenderer.drawPiranha(canvas, cx, cy, cellSize, direction, frame, frightened, blink, bodyColor)
        }
    }

    /** Current-tile speed modifier — matches Monkey's. */
    private fun currentMultiplier(): Float {
        val cd = maze.currentDirAt(tileCol, tileRow) ?: return 1f
        return when {
            direction == Direction.NONE -> 1f
            direction == cd -> 1.5f
            direction == cd.opposite() -> 0.5f
            else -> 1f
        }
    }

    companion object {
        // Bait-detection range, in squared tile distance. Set well beyond the
        // maze diagonal (22²+15² ≈ 709) so every CHASE piranha is lured, no
        // matter where it is — bait is a deliberate "pull them all off me"
        // tool, not a short-range nudge.
        private const val BAIT_RANGE_SQ = 900

        // Speed multiplier applied while a piranha is being dragged toward an
        // active black hole. Kept a touch below 1 so it still reads as a pull
        // rather than an ordinary chase, but high enough that piranhas across
        // the maze are visibly reeled in over the hole's lifetime.
        private const val BLACK_HOLE_DRAG_SCALE = 0.85f

        // After an EATEN piranha makes it home, it waits this long in the pen
        // before swimming back out — so it doesn't pop straight back into the
        // chase the instant it returns. (Lightning/level resets use the
        // original staggered release delays instead, via resetToSpawn.)
        private const val REENTRY_DELAY = 3f
    }
}
