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
    initialLives: Int = 5,
    var listener: Listener? = null,
) {
    enum class Phase { AWAITING_START, READY, PLAYING, LIFE_LOST, LEVEL_COMPLETE, ALL_LEVELS_COMPLETE, GAME_OVER, PAUSED }

    enum class Powerup { LIGHTNING, BLACK_HOLE, SHARK, SLOW_PIRANHAS, EXTRA_LIFE }

    interface Listener {
        fun onScoreChanged(score: Int) {}
        fun onLivesChanged(lives: Int) {}
        fun onLevelChanged(level: Int) {}
        fun onGameOver() {}
        fun onAllLevelsComplete() {}
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

    /**
     * False during the first run-through (levels 1..LEVEL_COUNT) — piranha speed
     * stays at 1.0x and there are always 4 piranhas. After clearing every level
     * the player chooses Restart (resets this) or Continue (sets this to true).
     * In challenge mode the level counter resets to 1 but `piranhaSpeedScaleForLevel`
     * applies the per-level ramp and `piranhaCountForLevel` adds one piranha at
     * every level divisible by 5.
     */
    private var challengeMode: Boolean = false

    /**
     * Banner text shown alongside "Ready!" at the start of a level. Set when
     * loadLevel detects a change in challenge speed/piranha count (or on entry
     * to challenge mode); consumed by the READY-phase banner draw + cleared on
     * the next loadLevel. Null means no extra banner (first run-through, or a
     * challenge level where nothing changed vs. the previous one).
     */
    private var levelIntroBanner: String? = null

    // Track last level's speed/count so loadLevel can compute the delta-vs-previous
    // banner without recomputing the entire history.
    private var lastSpeedScale: Float = 1.0f
    private var lastPiranhaCount: Int = 4

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
    private var heartTimer: Float = 0f

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
    private var monkey: Monkey = Monkey(maze, maze.monkeySpawn.first, maze.monkeySpawn.second)
    private var piranhas: List<Piranha> = createPiranhas(maze, level)

    init {
        emitAll()
    }

    @Synchronized
    fun reset(initialLives: Int = 5) {
        level = 1
        score = 0
        lives = initialLives
        challengeMode = false
        lastSpeedScale = 1.0f
        lastPiranhaCount = 4
        levelIntroBanner = null
        loadLevel(1)
        phase = Phase.READY
        phaseTimer = 2.0f
        emitAll()
    }

    /**
     * "Continue" action from the all-levels-complete overlay: drop into
     * challenge mode at level 1 with score + lives preserved. The READY banner
     * for L1 announces the mode itself; subsequent levels announce only the
     * speed/piranha-count deltas computed by loadLevel.
     */
    @Synchronized
    fun acceptChallenge() {
        challengeMode = true
        level = 1
        // Reset the delta baselines so loadLevel's first comparison treats the
        // jump from first-run-end (1.0x, 4) into challenge-L1 (also 1.0x, 4) as
        // a "no change" — we override that with the explicit "Challenge!" intro
        // below instead of a misleading "Speed Up".
        lastSpeedScale = piranhaSpeedScaleForLevel(1)
        lastPiranhaCount = piranhaCountForLevel(1)
        loadLevel(1)
        levelIntroBanner = "Challenge!"
        phase = Phase.READY
        phaseTimer = 2.0f
        listener?.onLevelChanged(level)
    }

    @Synchronized
    fun snapshot(): SaveGame.Snapshot = SaveGame.Snapshot(
        level = level,
        score = score,
        lives = lives,
        phase = phase,
        difficultyMultiplier = difficultyMultiplier,
        mazeLayout = maze.snapshotLayout(),
        fruitMap = maze.powerPelletFruits.toMap(),
        challengeMode = challengeMode,
    )

    /**
     * Restore from a previously saved snapshot. The current maze pellet state is
     * preserved so the player resumes where they left off; entities respawn.
     * - GAME_OVER snapshots stay in GAME_OVER so the overlay (watch-ad / restart)
     *   can be presented exactly as the player last saw it.
     * - LEVEL_COMPLETE snapshots advance to the next level on restore (the saved
     *   pellet grid is irrelevant since a new level is loaded fresh).
     * - All other phases (PLAYING/PAUSED/READY/LIFE_LOST) resume at READY with
     *   pellet progress intact.
     */
    @Synchronized
    fun restore(snapshot: SaveGame.Snapshot) {
        level = snapshot.level
        score = snapshot.score
        lives = snapshot.lives
        difficultyMultiplier = snapshot.difficultyMultiplier
        challengeMode = snapshot.challengeMode
        // Restoring mid-game shouldn't fire a delta banner — reset baselines to
        // the current level so loadLevel computes "no change."
        lastSpeedScale = piranhaSpeedScaleForLevel(level)
        lastPiranhaCount = piranhaCountForLevel(level)
        levelIntroBanner = null
        when (snapshot.phase) {
            Phase.LEVEL_COMPLETE -> {
                // Mid-completion: advance to the next level on restore, OR
                // surface the all-levels-complete overlay if the player had
                // just cleared the final first-run level. In the overlay case
                // we still need to rehydrate the maze from the snapshot so
                // the background behind the overlay shows the cleared level
                // instead of a default level-1 layout.
                if (!challengeMode && level >= Levels.LEVEL_COUNT) {
                    maze = Maze(snapshot.mazeLayout, snapshot.fruitMap)
                    monkey = Monkey(maze, maze.monkeySpawn.first, maze.monkeySpawn.second)
                    piranhas = createPiranhas(maze, level)
                    clearPowerups()
                    applyEntitySpeeds()
                    frightTimer = 0f
                    phase = Phase.ALL_LEVELS_COMPLETE
                    emitAll()
                    listener?.onAllLevelsComplete()
                } else {
                    level++
                    loadLevel(level)
                    phase = Phase.READY
                    phaseTimer = 2.0f
                    emitAll()
                }
            }
            Phase.ALL_LEVELS_COMPLETE -> {
                // Saved while the overlay was up — keep showing it.
                maze = Maze(snapshot.mazeLayout, snapshot.fruitMap)
                monkey = Monkey(maze, maze.monkeySpawn.first, maze.monkeySpawn.second)
                piranhas = createPiranhas(maze, level)
                clearPowerups()
                applyEntitySpeeds()
                frightTimer = 0f
                phase = Phase.ALL_LEVELS_COMPLETE
                emitAll()
                listener?.onAllLevelsComplete()
            }
            else -> {
                maze = Maze(snapshot.mazeLayout, snapshot.fruitMap)
                monkey = Monkey(maze, maze.monkeySpawn.first, maze.monkeySpawn.second)
                piranhas = createPiranhas(maze, level)
                clearPowerups()
                applyEntitySpeeds()
                frightTimer = 0f
                if (snapshot.phase == Phase.GAME_OVER) {
                    phase = Phase.GAME_OVER
                    emitAll()
                    listener?.onGameOver()
                } else {
                    phase = Phase.READY
                    phaseTimer = 2.0f
                    emitAll()
                }
            }
        }
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
                    // The challenge-mode level-intro banner only applies to
                    // its own READY window — clear it so a later READY (after
                    // life loss, etc.) shows the plain "Ready!" banner.
                    levelIntroBanner = null
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
                    // First run-through ends at Levels.LEVEL_COUNT — stop and
                    // present the Restart / Continue choice. In challenge mode
                    // the player has already opted in; keep cycling forever
                    // and let the per-level banner announce ramp changes.
                    if (!challengeMode && level >= Levels.LEVEL_COUNT) {
                        phase = Phase.ALL_LEVELS_COMPLETE
                        listener?.onAllLevelsComplete()
                    } else {
                        level++
                        loadLevel(level)
                        phase = Phase.READY
                        phaseTimer = 2.0f
                        listener?.onLevelChanged(level)
                    }
                }
            }
            Phase.AWAITING_START, Phase.GAME_OVER, Phase.PAUSED, Phase.ALL_LEVELS_COMPLETE -> {
                // No-op until the player chooses an action (resume, restart, continue).
            }
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
        if (heartTimer > 0f) heartTimer -= dt
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
            Powerup.EXTRA_LIFE -> {
                lives++
                listener?.onLivesChanged(lives)
                heartTimer = HEART_VISIBLE_DURATION
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
        heartTimer = 0f
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
        monkey.resetTo(maze.monkeySpawn.first, maze.monkeySpawn.second)
        for (p in piranhas) p.resetToSpawn()
    }

    private fun loadLevel(lvl: Int) {
        maze = Maze(Levels.layoutForLevel(lvl))
        monkey = Monkey(maze, maze.monkeySpawn.first, maze.monkeySpawn.second)
        piranhas = createPiranhas(maze, lvl)
        clearPowerups()
        applyEntitySpeeds()
        frightTimer = 0f

        // Recompute the level-intro banner from the speed/count delta. Only
        // applies in challenge mode — first run-through has no per-level
        // changes to announce. The check `levelIntroBanner == null` lets
        // `acceptChallenge` overwrite our result with "Challenge!" *after*
        // this method returns; we just produce the natural delta here.
        val newSpeed = piranhaSpeedScaleForLevel(lvl)
        val newCount = piranhaCountForLevel(lvl)
        if (challengeMode && levelIntroBanner == null) {
            val speedUp = newSpeed > lastSpeedScale + 0.001f
            val moreFish = newCount > lastPiranhaCount
            levelIntroBanner = when {
                speedUp && moreFish -> "Speed Up · +1 Piranha"
                speedUp -> "Speed Up"
                moreFish -> "+1 Piranha"
                else -> null
            }
        }
        lastSpeedScale = newSpeed
        lastPiranhaCount = newCount
    }

    /** Piranhas-per-level: always 4 during the first run-through; in challenge
     * mode an extra piranha is added at every level divisible by 5 (L5 = 5,
     * L10 = 6, L15 = 7, …). */
    private fun piranhaCountForLevel(lvl: Int): Int =
        if (!challengeMode) 4 else 4 + (lvl / 5)

    /** Per-level piranha speed scale. First run-through is a flat 1.0x; in
     * challenge mode the existing `Levels.piranhaSpeedScale` ramp applies. */
    private fun piranhaSpeedScaleForLevel(lvl: Int): Float =
        if (!challengeMode) 1.0f else Levels.piranhaSpeedScale(lvl)

    /**
     * Debug-only level jump: load the requested level mid-game while preserving
     * score and lives. Resets to READY so the new layout has a moment to draw
     * before play resumes. Wired up to a debug-build-only level selector in the
     * HUD; safe to call at any time.
     */
    @Synchronized
    fun jumpToLevel(targetLevel: Int) {
        if (targetLevel < 1) return
        level = targetLevel
        loadLevel(targetLevel)
        phase = Phase.READY
        phaseTimer = 2.0f
        listener?.onLevelChanged(level)
    }

    /** Debug hook: trigger the power-pellet fright effect without eating one. */
    @Synchronized
    fun debugActivatePowerPellet() {
        frightTimer = 6.5f
        frightChainBonus = 200
    }

    private fun createPiranhas(m: Maze, lvl: Int): List<Piranha> {
        val scale = piranhaSpeedScaleForLevel(lvl)
        val count = piranhaCountForLevel(lvl)
        val personalities = listOf(
            Piranha.Personality.DIRECT,
            Piranha.Personality.AHEAD2,
            Piranha.Personality.AHEAD4,
            Piranha.Personality.ROAMER,
        )
        // Staggered release: instant, then every 4 seconds. Extends naturally
        // beyond the original 4 piranhas so each new fish enters after the
        // previous one is already loose.
        val spawnTiles = m.piranhaSpawnTiles
        return (0 until count).map { i ->
            val (c, r) = spawnTiles[i % spawnTiles.size]
            Piranha(
                m,
                personalities[i % personalities.size],
                c,
                r,
                i * 4f,
            ).also {
                it.speedScale = scale * difficultyMultiplier
            }
        }
    }

    private fun applyEntitySpeeds() {
        monkey.speedScale = difficultyMultiplier
        val pscale = piranhaSpeedScaleForLevel(level)
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

        // Brief heart visual when the extra-life powerup activates — same
        // pop-and-fade pattern as the turtle, mirroring Brick Basher's
        // pink-powerup heart.
        if (heartTimer > 0f) {
            val hx = viewWidth / 2f
            val hy = hudHeightPx + playableHeight / 2f
            SpriteRenderer.drawHeart(
                canvas, hx, hy, cellSize * 1.8f, heartTimer, HEART_VISIBLE_DURATION,
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
            Phase.READY -> {
                drawBanner(canvas, "Ready!", cxScreen, cyScreen, cellSize * 1.6f)
                // In challenge mode, announce speed-up / +piranha just above
                // the "Ready!" so the player sees both before play begins.
                levelIntroBanner?.let {
                    drawBanner(canvas, it, cxScreen, cyScreen - cellSize * 1.4f, cellSize * 0.9f)
                }
            }
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
        private const val BANANA_ACTIVE_DURATION = 24f     // how long an uneaten banana stays (s)
        private const val LIGHTNING_FLASH_DURATION = 0.6f
        private const val BLACK_HOLE_DURATION = 15f
        private const val SHARK_DURATION = 15f
        private const val SLOW_DURATION = 10f
        private const val TURTLE_VISIBLE_DURATION = 1.5f
        private const val HEART_VISIBLE_DURATION = 1.5f
    }
}
