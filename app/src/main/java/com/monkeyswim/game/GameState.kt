package com.monkeyswim.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Owns all live game state and runs update + draw on each frame.
 * Methods are synchronized so the game thread and UI thread can call them safely.
 */
class GameState(
    initialLives: Int = 3,
    var listener: Listener? = null,
) {
    enum class Phase { AWAITING_START, READY, PLAYING, LIFE_LOST, LEVEL_COMPLETE, GAME_OVER, PAUSED }

    enum class Powerup { LIGHTNING, BLACK_HOLE, SHARK, SLOW_PIRANHAS }

    interface Listener {
        fun onScoreChanged(score: Int) {}
        fun onLivesChanged(lives: Int) {}
        fun onLevelChanged(level: Int) {}
        fun onGameOver() {}
    }

    var level: Int = 1
        private set
    var score: Int = 0
        private set
    var lives: Int = initialLives
        private set
    var phase: Phase = Phase.AWAITING_START
        private set

    private var phaseTimer: Float = 2.0f
    private var animTime: Float = 0f
    private var frightTimer: Float = 0f
    private var frightChainBonus: Int = 200

    // Powerup state. Banana spawns periodically; collecting it triggers a
    // random Powerup. The four effects each have their own state + timer.
    private var banana: Pair<Int, Int>? = null
    private var bananaTimer: Float = nextBananaInitialDelay()
    private var lightningFlashTimer: Float = 0f
    private var blackHole: Pair<Int, Int>? = null
    private var blackHoleTimer: Float = 0f
    private var shark: Shark? = null
    private var sharkTimer: Float = 0f
    private var slowPiranhaTimer: Float = 0f
    private var turtleTimer: Float = 0f

    /**
     * Global speed multiplier from the difficulty selection on the splash screen.
     * Setter applies to the existing monkey + piranhas immediately so a difficulty
     * picked at startup takes effect before the game leaves the READY phase.
     */
    var difficultyMultiplier: Float = 1f
        @Synchronized set(value) {
            field = value
            applyEntitySpeeds()
        }

    private var maze: Maze = Maze(Levels.layoutForLevel(1))
    private var monkey: Monkey = Monkey(maze, Levels.MONKEY_SPAWN.first, Levels.MONKEY_SPAWN.second)
    private var piranhas: List<Piranha> = createPiranhas(maze, level)

    init {
        emitAll()
    }

    @Synchronized
    fun reset(initialLives: Int = 3) {
        level = 1
        score = 0
        lives = initialLives
        loadLevel(1)
        phase = Phase.READY
        phaseTimer = 2.0f
        emitAll()
    }

    @Synchronized
    fun grantBonusLives(extra: Int) {
        lives += extra
        listener?.onLivesChanged(lives)
        if (phase == Phase.GAME_OVER) {
            // Continue mid-level: keep the maze + score, reset entity positions.
            respawnEntities()
            frightTimer = 0f
            phase = Phase.READY
            phaseTimer = 2.0f
        }
    }

    @Synchronized
    fun setPaused(paused: Boolean) {
        phase = when {
            paused && phase == Phase.PLAYING -> Phase.PAUSED
            !paused && phase == Phase.PAUSED -> Phase.PLAYING
            else -> phase
        }
    }

    /** Toggle pause from a screen tap — only flips between PLAYING and PAUSED. */
    @Synchronized
    fun togglePause() {
        setPaused(phase == Phase.PLAYING)
    }

    /**
     * Leave the splash-screen waiting state and start the game flow normally.
     * No-op outside of `AWAITING_START` so a stray call (e.g. from a leaked
     * click handler) can't reset a game already in progress.
     */
    @Synchronized
    fun startGame() {
        if (phase == Phase.AWAITING_START) {
            phase = Phase.READY
            phaseTimer = 2.0f
        }
    }

    @Synchronized
    fun requestDirection(dir: Direction) {
        if (phase == Phase.PLAYING) monkey.requestDirection(dir)
    }

    @Synchronized
    fun update(dt: Float) {
        animTime += dt
        when (phase) {
            Phase.READY -> {
                phaseTimer -= dt
                if (phaseTimer <= 0f) {
                    phase = Phase.PLAYING
                }
            }
            Phase.PLAYING -> updatePlaying(dt)
            Phase.LIFE_LOST -> {
                phaseTimer -= dt
                if (phaseTimer <= 0f) {
                    if (lives <= 0) {
                        phase = Phase.GAME_OVER
                        listener?.onGameOver()
                    } else {
                        respawnEntities()
                        phase = Phase.READY
                        phaseTimer = 2.0f
                    }
                }
            }
            Phase.LEVEL_COMPLETE -> {
                phaseTimer -= dt
                if (phaseTimer <= 0f) {
                    level++
                    loadLevel(level)
                    phase = Phase.READY
                    phaseTimer = 2.0f
                    listener?.onLevelChanged(level)
                }
            }
            Phase.AWAITING_START, Phase.GAME_OVER, Phase.PAUSED -> { /* no-op until resumed */ }
        }
    }

    private fun updatePlaying(dt: Float) {
        if (frightTimer > 0f) {
            frightTimer -= dt
            if (frightTimer <= 0f) {
                frightTimer = 0f
                frightChainBonus = 200
            }
        }

        // Powerup effect timers.
        if (lightningFlashTimer > 0f) lightningFlashTimer -= dt
        if (turtleTimer > 0f) turtleTimer -= dt
        if (blackHoleTimer > 0f) {
            blackHoleTimer -= dt
            if (blackHoleTimer <= 0f) blackHole = null
        }
        if (sharkTimer > 0f) {
            sharkTimer -= dt
            if (sharkTimer <= 0f) shark = null
        }
        if (slowPiranhaTimer > 0f) {
            slowPiranhaTimer -= dt
            if (slowPiranhaTimer <= 0f) applyEntitySpeeds()  // restore full speed
        }

        // Banana spawn / expiry — single timer flips between "spawn delay" and
        // "active duration" depending on whether a banana is on the map. Spawn
        // delays are randomized within a range so the appearance cadence isn't
        // a metronome the player can plan around.
        bananaTimer -= dt
        if (bananaTimer <= 0f) {
            if (banana == null) {
                banana = pickRandomFreeTile()
                bananaTimer = BANANA_ACTIVE_DURATION
            } else {
                banana = null
                bananaTimer = nextBananaRespawnDelay()
            }
        }

        monkey.update(dt)
        for (p in piranhas) p.update(dt, monkey, frightTimer)
        shark?.update(dt, piranhas)

        // Pellet collection.
        maze.consumePelletAt(monkey.tileCol, monkey.tileRow) { kind ->
            score += if (kind == Tile.POWER_PELLET) 50 else 10
            if (kind == Tile.POWER_PELLET) {
                frightTimer = 6.5f
                frightChainBonus = 200
            }
            listener?.onScoreChanged(score)
        }

        // Banana collection — triggers a random powerup.
        val ban = banana
        if (ban != null && ban.first == monkey.tileCol && ban.second == monkey.tileRow) {
            banana = null
            bananaTimer = nextBananaRespawnDelay()
            activatePowerup(Powerup.values().random())
        }

        // Black hole kills anything standing on its tile.
        blackHole?.let { (bhC, bhR) ->
            if (bhC == monkey.tileCol && bhR == monkey.tileRow) {
                onLifeLost()
                return
            }
            for (p in piranhas) {
                if (p.mode == Piranha.Mode.EATEN) continue
                if (p.tileCol == bhC && p.tileRow == bhR) p.markEaten()
            }
        }

        // Shark kill check — overlap with any non-eaten piranha sends it home.
        shark?.let { s ->
            for (p in piranhas) {
                if (p.mode == Piranha.Mode.EATEN) continue
                if (s.overlapsWith(p)) p.markEaten()
            }
        }

        // Bottom gateway -> level complete.
        if (maze.isGatewayTile(monkey.tileCol, monkey.tileRow) && maze.gatewayUnlocked) {
            phase = Phase.LEVEL_COMPLETE
            phaseTimer = 1.5f
            return
        }

        // Piranha collision.
        for (p in piranhas) {
            if (p.mode == Piranha.Mode.EATEN) continue
            if (p.overlapsWith(monkey)) {
                if (p.mode == Piranha.Mode.FRIGHTENED) {
                    p.markEaten()
                    score += frightChainBonus
                    frightChainBonus *= 2
                    listener?.onScoreChanged(score)
                } else {
                    onLifeLost()
                    return
                }
            }
        }
    }

    private fun activatePowerup(type: Powerup) {
        when (type) {
            Powerup.LIGHTNING -> {
                lightningFlashTimer = LIGHTNING_FLASH_DURATION
                // Snap every piranha back to its spawn corner with the same
                // staggered release schedule as level start (releaseDelay 0,
                // 4, 8, 12 s) — they're confined inside the pen and trickle
                // back out one at a time.
                for (p in piranhas) p.resetToSpawn()
            }
            Powerup.BLACK_HOLE -> {
                pickRandomFreeTile()?.let {
                    blackHole = it
                    blackHoleTimer = BLACK_HOLE_DURATION
                }
            }
            Powerup.SHARK -> {
                pickRandomFreeTile()?.let { (c, r) ->
                    shark = Shark(maze, c, r)
                    sharkTimer = SHARK_DURATION
                }
            }
            Powerup.SLOW_PIRANHAS -> {
                slowPiranhaTimer = SLOW_DURATION
                turtleTimer = TURTLE_VISIBLE_DURATION
                applyEntitySpeeds()
            }
        }
    }

    /**
     * Random walkable tile (PATH/PELLET) not currently occupied by the monkey
     * or any piranha. Used to drop bananas, black holes, and the shark spawn.
     *
     * Requires the candidate cell to have at least two monkey-walkable
     * neighbors so we never drop spawns on 1-tile dead-end stubs (the most
     * notable in the current layout is (7, 7) — the pen-exit corridor cell,
     * a PATH tile whose only monkey-walkable neighbor is (7, 6) above it.
     * Bananas sitting there look unreachable even though the monkey can in
     * principle commit to the col-7 detour to grab them).
     */
    private fun pickRandomFreeTile(): Pair<Int, Int>? {
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until maze.rows) {
            for (c in 0 until maze.cols) {
                val t = maze.tileAt(c, r)
                if (t != Tile.PATH && t != Tile.PELLET) continue
                if (c == monkey.tileCol && r == monkey.tileRow) continue
                if (piranhas.any { it.tileCol == c && it.tileRow == r }) continue
                val walkableNeighbors = (
                    (if (maze.isMonkeyWalkable(c, r - 1)) 1 else 0) +
                    (if (maze.isMonkeyWalkable(c, r + 1)) 1 else 0) +
                    (if (maze.isMonkeyWalkable(c - 1, r)) 1 else 0) +
                    (if (maze.isMonkeyWalkable(c + 1, r)) 1 else 0)
                )
                if (walkableNeighbors < 2) continue
                candidates += c to r
            }
        }
        return if (candidates.isEmpty()) null else candidates.random()
    }

    /** Reset powerup state — called on life loss, level transition, full reset. */
    private fun clearPowerups() {
        banana = null
        bananaTimer = nextBananaInitialDelay()
        lightningFlashTimer = 0f
        blackHole = null
        blackHoleTimer = 0f
        shark = null
        sharkTimer = 0f
        slowPiranhaTimer = 0f
        turtleTimer = 0f
    }

    private fun onLifeLost() {
        lives--
        listener?.onLivesChanged(lives)
        phase = Phase.LIFE_LOST
        phaseTimer = 1.5f
        frightTimer = 0f
        clearPowerups()
        applyEntitySpeeds()  // restore base speeds in case slow effect was active
    }

    private fun respawnEntities() {
        monkey.resetTo(Levels.MONKEY_SPAWN.first, Levels.MONKEY_SPAWN.second)
        for (p in piranhas) p.resetToSpawn()
    }

    private fun loadLevel(lvl: Int) {
        maze = Maze(Levels.layoutForLevel(lvl))
        monkey = Monkey(maze, Levels.MONKEY_SPAWN.first, Levels.MONKEY_SPAWN.second)
        piranhas = createPiranhas(maze, lvl)
        clearPowerups()
        applyEntitySpeeds()
        frightTimer = 0f
    }

    private fun createPiranhas(m: Maze, lvl: Int): List<Piranha> {
        val scale = Levels.piranhaSpeedScale(lvl)
        val personalities = listOf(
            Piranha.Personality.DIRECT,
            Piranha.Personality.AHEAD2,
            Piranha.Personality.AHEAD4,
            Piranha.Personality.ROAMER,
        )
        // Staggered release: Blinky out instantly, then 4s, 8s, 12s.
        val releaseDelays = listOf(0f, 4f, 8f, 12f)
        return Levels.PIRANHA_SPAWNS.mapIndexed { i, (c, r) ->
            Piranha(
                m,
                personalities[i % personalities.size],
                c,
                r,
                releaseDelays[i % releaseDelays.size],
            ).also {
                it.speedScale = scale * difficultyMultiplier
            }
        }
    }

    private fun applyEntitySpeeds() {
        monkey.speedScale = difficultyMultiplier
        val pscale = Levels.piranhaSpeedScale(level)
        // Slow-piranhas powerup halves the piranha speed; the monkey's speed is
        // unaffected (per the spec).
        val slowMult = if (slowPiranhaTimer > 0f) 0.5f else 1f
        for (p in piranhas) p.speedScale = pscale * difficultyMultiplier * slowMult
    }

    private fun emitAll() {
        listener?.onScoreChanged(score)
        listener?.onLivesChanged(lives)
        listener?.onLevelChanged(level)
    }

    // ---------- Drawing ----------

    private val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD230")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bannerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    @Synchronized
    fun draw(canvas: Canvas, viewWidth: Int, viewHeight: Int, hudHeightPx: Float) {
        val playableHeight = viewHeight - hudHeightPx
        val cellSize = kotlin.math.min(
            viewWidth.toFloat() / maze.cols,
            playableHeight / maze.rows
        )
        val originX = (viewWidth - cellSize * maze.cols) / 2f
        val originY = hudHeightPx + (playableHeight - cellSize * maze.rows) / 2f

        canvas.drawColor(Color.parseColor("#06324C"))
        maze.draw(canvas, cellSize, originX, originY, animTime)

        // Banana sits above the maze, below the entities. Drawn larger than
        // the fruit power pellets so it reads as the "powerup" tile.
        banana?.let { (col, row) ->
            val cx = originX + (col + 0.5f) * cellSize
            val cy = originY + (row + 0.5f) * cellSize
            val pulse = 0.55f + 0.05f * kotlin.math.sin(animTime * 4f)
            FruitRenderer.drawBanana(canvas, cx, cy, cellSize * pulse)
        }

        // Black hole — same z-layer as banana so the monkey can step over it.
        blackHole?.let { (col, row) ->
            val cx = originX + (col + 0.5f) * cellSize
            val cy = originY + (row + 0.5f) * cellSize
            SpriteRenderer.drawBlackHole(canvas, cx, cy, cellSize, animTime)
        }

        if (phase != Phase.LIFE_LOST) {
            monkey.draw(canvas, cellSize, originX, originY)
        } else {
            // Death animation: shrink the monkey.
            val t = (1.5f - phaseTimer.coerceAtLeast(0f)) / 1.5f
            val shrunk = (1f - t).coerceIn(0f, 1f) * cellSize
            if (shrunk > 1f) {
                val cx = originX + monkey.x * cellSize
                val cy = originY + monkey.y * cellSize
                SpriteRenderer.drawMonkey(canvas, cx, cy, shrunk, monkey.direction, monkey.frame)
            }
        }

        if (phase != Phase.LIFE_LOST && phase != Phase.LEVEL_COMPLETE) {
            for (p in piranhas) p.draw(canvas, cellSize, originX, originY, frightTimer)
        }

        // Shark draws on top of piranhas (it's the predator chasing them).
        if (phase != Phase.LIFE_LOST && phase != Phase.LEVEL_COMPLETE) {
            shark?.draw(canvas, cellSize, originX, originY)
        }

        // Brief turtle visual when the slow-piranhas powerup activates —
        // pops in centre-screen and fades out over 1.5s, matching Brick
        // Basher's white-powerup turtle.
        if (turtleTimer > 0f) {
            val tx = viewWidth / 2f
            val ty = hudHeightPx + playableHeight / 2f
            SpriteRenderer.drawTurtle(
                canvas, tx, ty, cellSize * 1.8f, turtleTimer, TURTLE_VISIBLE_DURATION,
            )
        }

        // Lightning flash — full-screen white wash that fades over the duration.
        if (lightningFlashTimer > 0f) {
            val alpha = (lightningFlashTimer / LIGHTNING_FLASH_DURATION).coerceIn(0f, 1f)
            SpriteRenderer.drawLightningOverlay(
                canvas, viewWidth.toFloat(), viewHeight.toFloat(), alpha,
            )
        }

        // Banner text overlays.
        val cxScreen = viewWidth / 2f
        val cyScreen = hudHeightPx + playableHeight / 2f
        when (phase) {
            Phase.READY -> drawBanner(canvas, "Ready!", cxScreen, cyScreen, cellSize * 1.6f)
            Phase.LEVEL_COMPLETE -> drawBanner(canvas, "Level $level cleared!", cxScreen, cyScreen, cellSize * 1.2f)
            Phase.PAUSED -> drawBanner(canvas, "Paused", cxScreen, cyScreen, cellSize * 1.6f)
            else -> {}
        }
    }

    private fun drawBanner(canvas: Canvas, text: String, x: Float, y: Float, size: Float) {
        bannerPaint.textSize = size
        bannerShadow.textSize = size
        canvas.drawText(text, x + 4f, y + 4f, bannerShadow)
        canvas.drawText(text, x, y, bannerPaint)
    }

    private fun nextBananaInitialDelay(): Float =
        BANANA_INITIAL_MIN + (BANANA_INITIAL_MAX - BANANA_INITIAL_MIN) * kotlin.random.Random.nextFloat()

    private fun nextBananaRespawnDelay(): Float =
        BANANA_RESPAWN_MIN + (BANANA_RESPAWN_MAX - BANANA_RESPAWN_MIN) * kotlin.random.Random.nextFloat()

    companion object {
        // Banana spawn cadence is randomized within these ranges so the player
        // can't memorize the schedule. Initial range is shorter than respawn so
        // the first banana arrives sooner than later ones.
        private const val BANANA_INITIAL_MIN = 10f         // first banana spawn min (s)
        private const val BANANA_INITIAL_MAX = 20f         // first banana spawn max (s)
        private const val BANANA_RESPAWN_MIN = 18f         // delay between bananas min (s)
        private const val BANANA_RESPAWN_MAX = 40f         // delay between bananas max (s)
        private const val BANANA_ACTIVE_DURATION = 12f     // how long an uneaten banana stays (s)
        private const val LIGHTNING_FLASH_DURATION = 0.6f
        private const val BLACK_HOLE_DURATION = 15f
        private const val SHARK_DURATION = 15f
        private const val SLOW_DURATION = 10f
        private const val TURTLE_VISIBLE_DURATION = 1.5f
    }
}
