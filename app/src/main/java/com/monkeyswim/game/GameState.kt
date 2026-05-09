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
    enum class Phase { READY, PLAYING, LIFE_LOST, LEVEL_COMPLETE, GAME_OVER, PAUSED }

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
    var phase: Phase = Phase.READY
        private set

    private var phaseTimer: Float = 2.0f
    private var animTime: Float = 0f
    private var frightTimer: Float = 0f
    private var frightChainBonus: Int = 200

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
            Phase.GAME_OVER, Phase.PAUSED -> { /* no-op until resumed */ }
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

        monkey.update(dt)
        for (p in piranhas) p.update(dt, monkey, frightTimer)

        // Pellet collection.
        maze.consumePelletAt(monkey.tileCol, monkey.tileRow) { kind ->
            score += if (kind == Tile.POWER_PELLET) 50 else 10
            if (kind == Tile.POWER_PELLET) {
                frightTimer = 6.5f
                frightChainBonus = 200
            }
            listener?.onScoreChanged(score)
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

    private fun onLifeLost() {
        lives--
        listener?.onLivesChanged(lives)
        phase = Phase.LIFE_LOST
        phaseTimer = 1.5f
        frightTimer = 0f
    }

    private fun respawnEntities() {
        monkey.resetTo(Levels.MONKEY_SPAWN.first, Levels.MONKEY_SPAWN.second)
        for (p in piranhas) p.resetToSpawn()
    }

    private fun loadLevel(lvl: Int) {
        maze = Maze(Levels.layoutForLevel(lvl))
        monkey = Monkey(maze, Levels.MONKEY_SPAWN.first, Levels.MONKEY_SPAWN.second)
        piranhas = createPiranhas(maze, lvl)
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
        return Levels.PIRANHA_SPAWNS.mapIndexed { i, (c, r) ->
            Piranha(m, personalities[i % personalities.size], c, r).also {
                it.speedScale = scale
            }
        }
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
}
