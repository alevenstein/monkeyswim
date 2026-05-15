package com.monkeyswim.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Holds runtime maze state for a single level. Pellet/power-pellet tiles mutate to PATH
 * as the monkey collects them. Tracks remaining pellets and the bottom gateway lock.
 */
class Maze(
    layout: List<String>,
    fruitOverrides: Map<Pair<Int, Int>, FruitRenderer.FruitType>? = null,
) {

    val cols: Int = layout.first().length
    val rows: Int = layout.size

    private val tiles: Array<Array<Tile>> = Array(rows) { r ->
        Array(cols) { c -> Tile.fromChar(layout[r][c]) }
    }

    var pelletsRemaining: Int = 0
        private set

    val totalPellets: Int

    val gatewayUnlocked: Boolean
        get() = pelletsRemaining == 0

    // Cached gateway tile coords for level-transition detection.
    val gatewayTiles: List<Pair<Int, Int>>

    // Tunnel exit rows at the tunnel columns (used when an entity walks off the
    // top or bottom of the maze). The wrap is vertical and per-column: stepping
    // past the top T tile in column c re-enters at the bottom T tile in the
    // same column c, and vice versa. Multiple adjacent T cells form a wider
    // tunnel mouth.
    val tunnelCols: Set<Int>
    private val tunnelTopRow: Int
    private val tunnelBottomRow: Int

    // Corridor tile directly above the middle of the pen door. Piranhas in
    // LEAVING_PEN mode home in on this tile to escape the pen.
    val penExitTile: Pair<Int, Int>

    // Cell coordinates of the monkey's spawn (the unique 'M' tile in the layout)
    // and the four piranha spawn cells (the 4 corners of the '=' pen-interior
    // bounding box — the spec mandates exactly four piranhas per level). Both
    // are derived from the layout itself so each level can carry its own pen
    // dimensions without changing the count.
    val monkeySpawn: Pair<Int, Int>
    val piranhaSpawnTiles: List<Pair<Int, Int>>

    /** Optional crocodile spawn cell — null if this level doesn't have a 'K'.
     *  GameState spawns a Crocodile entity here at level start; the croc
     *  patrols along the axis defined by its walkable neighbours. */
    val crocodileSpawn: Pair<Int, Int>?

    // Each power pellet picks a random fruit at maze construction so that the
    // sprite stays stable for the whole level — players see the same fruit
    // every frame until they eat it.
    private val powerPelletFruit: Map<Pair<Int, Int>, FruitRenderer.FruitType>

    init {
        var pellets = 0
        val gw = mutableListOf<Pair<Int, Int>>()
        val penDoors = mutableListOf<Pair<Int, Int>>()
        val ppFruits = mutableMapOf<Pair<Int, Int>, FruitRenderer.FruitType>()
        val tunnelColsSet = mutableSetOf<Int>()
        val tunnelRowsSet = mutableSetOf<Int>()
        var monkeySpawnFound: Pair<Int, Int>? = null
        var crocodileSpawnFound: Pair<Int, Int>? = null
        val piranhaSpawns = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                when (tiles[r][c]) {
                    Tile.PELLET -> pellets++
                    Tile.POWER_PELLET -> {
                        pellets++
                        ppFruits[c to r] = fruitOverrides?.get(c to r)
                            ?: FruitRenderer.FruitType.values().random()
                    }
                    Tile.BOTTOM_GATEWAY -> gw += c to r
                    Tile.PEN_DOOR -> penDoors += c to r
                    Tile.PEN_INTERIOR -> piranhaSpawns += c to r
                    Tile.TUNNEL -> {
                        tunnelColsSet += c
                        tunnelRowsSet += r
                    }
                    Tile.MONKEY_SPAWN -> {
                        require(monkeySpawnFound == null) {
                            "Layout has more than one 'M' (monkey-spawn) tile"
                        }
                        monkeySpawnFound = c to r
                    }
                    Tile.CROCODILE_SPAWN -> {
                        require(crocodileSpawnFound == null) {
                            "Layout has more than one 'K' (crocodile-spawn) tile"
                        }
                        crocodileSpawnFound = c to r
                    }
                    else -> {}
                }
            }
        }
        crocodileSpawn = crocodileSpawnFound
        totalPellets = pellets
        pelletsRemaining = pellets
        gatewayTiles = gw
        tunnelCols = tunnelColsSet
        tunnelTopRow = tunnelRowsSet.minOrNull() ?: -1
        tunnelBottomRow = tunnelRowsSet.maxOrNull() ?: -1
        powerPelletFruit = ppFruits
        monkeySpawn = requireNotNull(monkeySpawnFound) {
            "Layout is missing an 'M' (monkey-spawn) tile"
        }
        // Pin spawns to the 4 corners of the pen-interior bounding box. For the
        // current 3x2 pens this gives a symmetric corner layout (top-left,
        // top-right, bottom-left, bottom-right) that aligns 1:1 with the four
        // piranha personalities + staggered release delays in createPiranhas.
        require(piranhaSpawns.isNotEmpty()) { "Layout is missing '=' pen-interior tiles" }
        val minC = piranhaSpawns.minOf { it.first }
        val maxC = piranhaSpawns.maxOf { it.first }
        val minR = piranhaSpawns.minOf { it.second }
        val maxR = piranhaSpawns.maxOf { it.second }
        piranhaSpawnTiles = listOf(
            minC to minR,
            maxC to minR,
            minC to maxR,
            maxC to maxR,
        )

        penExitTile = if (penDoors.isNotEmpty()) {
            val doorRow = penDoors.minOf { it.second }
            val midDoors = penDoors.filter { it.second == doorRow }.sortedBy { it.first }
            midDoors[midDoors.size / 2].first to (doorRow - 1)
        } else {
            -1 to -1
        }
    }

    fun isPenTile(col: Int, row: Int): Boolean {
        val t = tileAt(col, row)
        return t == Tile.PEN_DOOR || t == Tile.PEN_INTERIOR
    }

    fun tileAt(col: Int, row: Int): Tile {
        if (row !in 0 until rows || col !in 0 until cols) return Tile.WALL
        return tiles[row][col]
    }

    /** True while the tide cycle is in its "high" phase, exposing TIDE tiles
     *  as walkable. Toggled by GameState (the tide cycle is a global timer,
     *  not part of the Maze itself). */
    var tideHigh: Boolean = true

    /** Whether this level contains any TIDE tiles — used to decide whether to
     *  show the tide phase indicator in the HUD. Cached once at construction. */
    val hasTideTiles: Boolean = (0 until rows).any { r ->
        (0 until cols).any { c -> tiles[r][c] == Tile.TIDE }
    }

    fun isMonkeyWalkable(col: Int, row: Int): Boolean {
        // Off-grid on a tunnel column is walkable — the wrap logic will pull
        // the entity to the opposite side. Without this, leading-edge tile
        // probes block bottom-to-top wrap before the position-wrap can fire
        // (Float.toInt truncates toward zero, so the equivalent top-to-bottom
        // case slips through).
        if (col in tunnelCols && (row < 0 || row >= rows)) return true
        val t = tileAt(col, row)
        return when (t) {
            Tile.PATH, Tile.PELLET, Tile.POWER_PELLET, Tile.TUNNEL, Tile.MONKEY_SPAWN,
            Tile.CURRENT_UP, Tile.CURRENT_DOWN, Tile.CURRENT_LEFT, Tile.CURRENT_RIGHT,
            Tile.LILY_PAD, Tile.CROCODILE_SPAWN -> true
            Tile.TIDE -> tideHigh
            Tile.BOTTOM_GATEWAY -> gatewayUnlocked
            else -> false
        }
    }

    fun isPiranhaWalkable(col: Int, row: Int): Boolean {
        if (col in tunnelCols && (row < 0 || row >= rows)) return true
        val t = tileAt(col, row)
        return when (t) {
            Tile.PATH, Tile.PELLET, Tile.POWER_PELLET, Tile.TUNNEL,
            Tile.PEN_DOOR, Tile.PEN_INTERIOR, Tile.MONKEY_SPAWN,
            Tile.CURRENT_UP, Tile.CURRENT_DOWN, Tile.CURRENT_LEFT, Tile.CURRENT_RIGHT,
            Tile.LILY_PAD, Tile.CROCODILE_SPAWN -> true
            Tile.TIDE -> tideHigh
            else -> false
        }
    }

    /** Direction the current at (col, row) pushes entities, or null if not a
     *  current tile. */
    fun currentDirAt(col: Int, row: Int): Direction? =
        tileAt(col, row).currentDirection

    /** True if the tile at (col, row) is a slippery lily pad — entities on
     *  these can't change direction. */
    fun isLilyPad(col: Int, row: Int): Boolean = tileAt(col, row) == Tile.LILY_PAD

    fun isTunnelTile(col: Int, row: Int): Boolean = tileAt(col, row) == Tile.TUNNEL

    /** Returns the wrap target if (col,row) is a tunnel exit; otherwise null. */
    fun tunnelWrap(col: Int, row: Int): Pair<Int, Int>? {
        if (col !in tunnelCols) return null
        return when {
            row < tunnelTopRow -> col to tunnelBottomRow
            row > tunnelBottomRow -> col to tunnelTopRow
            else -> null
        }
    }

    /** Returns true if a pellet was eaten and reports its kind via [onCollect]. */
    fun consumePelletAt(col: Int, row: Int, onCollect: (Tile) -> Unit): Boolean {
        if (row !in 0 until rows || col !in 0 until cols) return false
        val t = tiles[row][col]
        if (t == Tile.PELLET || t == Tile.POWER_PELLET) {
            tiles[row][col] = Tile.PATH
            pelletsRemaining--
            onCollect(t)
            return true
        }
        return false
    }

    fun isGatewayTile(col: Int, row: Int): Boolean =
        tileAt(col, row) == Tile.BOTTOM_GATEWAY

    val powerPelletFruits: Map<Pair<Int, Int>, FruitRenderer.FruitType>
        get() = powerPelletFruit

    fun snapshotLayout(): List<String> = (0 until rows).map { r ->
        buildString(cols) {
            for (c in 0 until cols) append(tiles[r][c].char)
        }
    }

    /** True if the cell is a wall for rendering — out-of-bounds counts as wall so outer banks stay flush. */
    private fun isWallForRender(col: Int, row: Int): Boolean {
        if (row !in 0 until rows || col !in 0 until cols) return true
        return tiles[row][col] == Tile.WALL
    }

    // ---------- Drawing ----------

    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A2310")
        style = Paint.Style.FILL
    }
    private val wallTopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5C3A1E")
        style = Paint.Style.FILL
    }
    private val waterDeepPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val waveBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 160, 220, 240)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val waveBandPaintFaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 200, 240, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val causticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val wavePath = Path()
    private val causticPoints: Array<FloatArray> = run {
        val rng = Random(0xC0FFEEL)
        Array(36) {
            floatArrayOf(rng.nextFloat(), rng.nextFloat(), rng.nextFloat() * (Math.PI * 2).toFloat())
        }
    }
    private var waterShaderTop: Float = Float.NaN
    private var waterShaderBottom: Float = Float.NaN
    private val pelletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE9B0")
        style = Paint.Style.FILL
    }
    private val penDoorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC0CB")
        style = Paint.Style.FILL
    }
    private val gatewayLockedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        style = Paint.Style.FILL
    }
    private val gatewayUnlockedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22FFAA")
        style = Paint.Style.FILL
    }

    private val tmpRect = RectF()

    fun draw(canvas: Canvas, cellSize: Float, originX: Float, originY: Float, animTime: Float) {
        val mazeWidth = cols * cellSize
        val mazeHeight = rows * cellSize
        val mazeRight = originX + mazeWidth
        val mazeBottom = originY + mazeHeight

        // Vertical depth gradient — lighter near surface, deeper toward bottom
        if (waterShaderTop != originY || waterShaderBottom != mazeBottom) {
            waterDeepPaint.shader = LinearGradient(
                0f, originY, 0f, mazeBottom,
                intArrayOf(
                    Color.parseColor("#0F6588"),
                    Color.parseColor("#08456A"),
                    Color.parseColor("#04263A"),
                    Color.parseColor("#021725"),
                ),
                floatArrayOf(0f, 0.45f, 0.85f, 1f),
                Shader.TileMode.CLAMP,
            )
            waterShaderTop = originY
            waterShaderBottom = mazeBottom
        }
        canvas.drawRect(originX, originY, mazeRight, mazeBottom, waterDeepPaint)

        // Animated wave bands — three interleaved layers at different speeds
        val bandSpacing = cellSize * 1.2f
        val segStep = cellSize * 0.35f
        var bandIndex = 0
        var bandY = originY + cellSize * 0.4f
        while (bandY < mazeBottom) {
            val phase = animTime * (0.6f + (bandIndex % 3) * 0.18f) + bandIndex * 0.65f
            val amp = cellSize * (0.10f + 0.05f * (bandIndex % 2))
            wavePath.reset()
            var px = originX
            wavePath.moveTo(px, bandY + sin(phase) * amp)
            while (px < mazeRight) {
                px += segStep
                val y2 = bandY + sin(phase + (px - originX) / cellSize * 0.55f) * amp
                wavePath.lineTo(px, y2)
            }
            canvas.drawPath(wavePath, if (bandIndex % 2 == 0) waveBandPaint else waveBandPaintFaint)
            bandY += bandSpacing
            bandIndex++
        }

        // Caustic highlights — soft pulsing dots that drift
        for (pt in causticPoints) {
            val fx = pt[0]; val fy = pt[1]; val phase = pt[2]
            val cxc = originX + fx * mazeWidth + sin(animTime * 0.5f + phase) * cellSize * 0.6f
            val cyc = originY + fy * mazeHeight + cos(animTime * 0.4f + phase * 1.3f) * cellSize * 0.4f
            val pulse = 0.5f + 0.5f * sin(animTime * 1.8f + phase)
            causticPaint.color = Color.argb((30 + 70 * pulse).toInt(), 180, 230, 255)
            canvas.drawCircle(cxc, cyc, cellSize * (0.18f + 0.16f * pulse), causticPaint)
        }

        // Tiles
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = originX + c * cellSize
                val top = originY + r * cellSize
                val cx = left + cellSize / 2f
                val cy = top + cellSize / 2f
                when (tiles[r][c]) {
                    Tile.WALL -> {
                        // Bank: inset each side that faces a non-wall tile so corridors look wider,
                        // while sides touching neighboring walls stay flush so banks merge seamlessly.
                        val inset = cellSize * 0.30f
                        val l = if (isWallForRender(c - 1, r)) left else left + inset
                        val rt = if (isWallForRender(c + 1, r)) left + cellSize else left + cellSize - inset
                        val t = if (isWallForRender(c, r - 1)) top else top + inset
                        val b = if (isWallForRender(c, r + 1)) top + cellSize else top + cellSize - inset
                        tmpRect.set(l, t, rt, b)
                        val radius = cellSize * 0.22f
                        canvas.drawRoundRect(tmpRect, radius, radius, wallPaint)
                        // Lighter top half for a sun-lit edge
                        val highlightBottom = t + (b - t) * 0.55f
                        tmpRect.set(l + 1f, t + 1f, rt - 1f, highlightBottom)
                        canvas.drawRoundRect(tmpRect, radius, radius, wallTopPaint)
                    }
                    Tile.PELLET -> {
                        canvas.drawCircle(cx, cy, cellSize * 0.12f, pelletPaint)
                    }
                    Tile.POWER_PELLET -> {
                        val pulse = 0.40f + 0.04f * sin(animTime * 4f)
                        val fruit = powerPelletFruit[c to r] ?: FruitRenderer.FruitType.APPLE
                        FruitRenderer.draw(canvas, fruit, cx, cy, cellSize * pulse)
                    }
                    Tile.PEN_DOOR -> {
                        // Render the gate only at door cells whose neighbor above is an open
                        // corridor — that's the *actual* entryway. Door cells flanked above by
                        // walls are part of the pen perimeter logically but have no doorway
                        // to gate visually.
                        if (tileAt(c, r - 1) != Tile.WALL) {
                            // The walls flanking the door inset their door-facing edges by
                            // 0.30·cellSize (general wall-renderer behavior toward non-wall
                            // neighbors), so the visual entrance is 1.6·cellSize wide. Extend
                            // the gate by the same inset on each side so it bridges to the
                            // walls' actual edges instead of floating with water gaps beside it.
                            val inset = cellSize * 0.30f
                            tmpRect.set(
                                left - inset,
                                top - cellSize * 0.05f,
                                left + cellSize + inset,
                                top + cellSize * 0.05f,
                            )
                            canvas.drawRect(tmpRect, penDoorPaint)
                        }
                    }
                    Tile.BOTTOM_GATEWAY -> {
                        if (gatewayUnlocked) {
                            // Animated glowing exit
                            val glow = 0.8f + 0.2f * sin(animTime * 5f)
                            val p = Paint(gatewayUnlockedPaint).apply {
                                alpha = (glow * 255).toInt()
                            }
                            canvas.drawRect(left, top, left + cellSize, top + cellSize, p)
                        } else {
                            canvas.drawRect(left, top, left + cellSize, top + cellSize, gatewayLockedPaint)
                        }
                    }
                    Tile.PATH, Tile.PEN_INTERIOR, Tile.TUNNEL, Tile.MONKEY_SPAWN,
                    Tile.CROCODILE_SPAWN -> {
                        // intentionally no decoration — the crocodile spawn is
                        // just a marker; the entity itself is drawn separately.
                    }
                    Tile.CURRENT_UP, Tile.CURRENT_DOWN, Tile.CURRENT_LEFT, Tile.CURRENT_RIGHT -> {
                        drawCurrentArrow(canvas, cx, cy, cellSize, tiles[r][c], animTime)
                    }
                    Tile.TIDE -> {
                        if (tideHigh) {
                            // Walkable but visually distinguishable — pale tide
                            // foam ring so the player knows it's a tide cell.
                            canvas.drawCircle(cx, cy, cellSize * 0.18f, tideOpenPaint)
                        } else {
                            // Wall phase — exposed rock. Inset rounded square
                            // similar to a regular wall, but a sandier colour.
                            val inset = cellSize * 0.18f
                            tmpRect.set(left + inset, top + inset,
                                left + cellSize - inset, top + cellSize - inset)
                            canvas.drawRoundRect(tmpRect, cellSize * 0.20f, cellSize * 0.20f, tideRockPaint)
                        }
                    }
                    Tile.LILY_PAD -> {
                        // Lily pad — green disc with a slight inner highlight
                        // and a wedge notch (the leaf "slit"). The notch
                        // rotates with animTime so adjacent pads don't all
                        // notch in the same direction.
                        canvas.drawCircle(cx, cy, cellSize * 0.42f, lilyPadDark)
                        canvas.drawCircle(cx, cy, cellSize * 0.36f, lilyPadLight)
                        val phase = ((c * 31 + r * 17) % 8) * (Math.PI.toFloat() / 4f)
                        val notchAngle = phase
                        val nx = cx + kotlin.math.cos(notchAngle) * cellSize * 0.32f
                        val ny = cy + kotlin.math.sin(notchAngle) * cellSize * 0.32f
                        wavePath.reset()
                        wavePath.moveTo(cx, cy)
                        wavePath.lineTo(
                            cx + kotlin.math.cos(notchAngle - 0.25f) * cellSize * 0.42f,
                            cy + kotlin.math.sin(notchAngle - 0.25f) * cellSize * 0.42f,
                        )
                        wavePath.lineTo(nx, ny)
                        wavePath.lineTo(
                            cx + kotlin.math.cos(notchAngle + 0.25f) * cellSize * 0.42f,
                            cy + kotlin.math.sin(notchAngle + 0.25f) * cellSize * 0.42f,
                        )
                        wavePath.close()
                        canvas.drawPath(wavePath, lilyPadDark)
                    }
                }
            }
        }
    }

    private fun drawCurrentArrow(
        canvas: Canvas, cx: Float, cy: Float, cellSize: Float, tile: Tile, animTime: Float,
    ) {
        // Pulsing chevron arrow that "flows" in the current direction. Two
        // staggered chevrons drift in the flow direction over time so the
        // tile reads as a moving current rather than a static decoration.
        val dir = tile.currentDirection ?: return
        // Per-tile animation offset; 0..1 phase repeats every ~1s.
        val phase = (animTime * 0.7f) - (animTime * 0.7f).toInt()
        val s = cellSize * 0.35f
        for (i in 0..1) {
            val localPhase = (phase + i * 0.5f) % 1f
            val drift = (localPhase - 0.5f) * cellSize * 0.5f
            val alpha = (sin(localPhase * Math.PI.toFloat()) * 200f).toInt().coerceIn(0, 200)
            currentArrowPaint.alpha = alpha
            val ax = cx + dir.dx * drift
            val ay = cy + dir.dy * drift
            wavePath.reset()
            when (dir) {
                Direction.RIGHT -> {
                    wavePath.moveTo(ax - s * 0.4f, ay - s * 0.5f)
                    wavePath.lineTo(ax + s * 0.4f, ay)
                    wavePath.lineTo(ax - s * 0.4f, ay + s * 0.5f)
                }
                Direction.LEFT -> {
                    wavePath.moveTo(ax + s * 0.4f, ay - s * 0.5f)
                    wavePath.lineTo(ax - s * 0.4f, ay)
                    wavePath.lineTo(ax + s * 0.4f, ay + s * 0.5f)
                }
                Direction.UP -> {
                    wavePath.moveTo(ax - s * 0.5f, ay + s * 0.4f)
                    wavePath.lineTo(ax, ay - s * 0.4f)
                    wavePath.lineTo(ax + s * 0.5f, ay + s * 0.4f)
                }
                Direction.DOWN -> {
                    wavePath.moveTo(ax - s * 0.5f, ay - s * 0.4f)
                    wavePath.lineTo(ax, ay + s * 0.4f)
                    wavePath.lineTo(ax + s * 0.5f, ay - s * 0.4f)
                }
                Direction.NONE -> {}
            }
            canvas.drawPath(wavePath, currentArrowPaint)
        }
    }

    private val currentArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80E8FF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tideOpenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80E0F0FF")
        style = Paint.Style.FILL
    }
    private val tideRockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A6248")
        style = Paint.Style.FILL
    }
    private val lilyPadDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A6B3A")
        style = Paint.Style.FILL
    }
    private val lilyPadLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FA463")
        style = Paint.Style.FILL
    }
}
