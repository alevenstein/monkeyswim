package com.monkeyswim.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
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

    // Bounding box (inclusive) of the BOTTOM_GATEWAY tiles, in tile coords.
    // Used by the unified gate renderer to draw all portal cells as one slab+
    // vortex rather than per-cell. Null only if a level has no gateway.
    val gatewayBounds: Rect?

    // Tiles a CHASE/FRIGHTENED piranha is allowed to step INTO from a live
    // tile. Excludes walls, the pen, the locked portal, AND every tile in a
    // dead-end pocket (iteratively prune anything with <2 surviving
    // neighbors). Without the dead-end prune, levels with a 1-wide pen-exit
    // funnel (e.g. L3: (7,9)→…→(7,12)→blocked door) trap loose piranhas: the
    // greedy distance heuristic picks "down toward monkey" at every fork and
    // the piranha oscillates between the funnel and adjacent 1-cell pockets.
    // Tide HIGH is assumed (cells gated only by tide are kept).
    private val liveChaseTiles: Set<Pair<Int, Int>>

    // For each pruned tile, the direction stepping toward the nearest live
    // tile (one BFS step toward the live skeleton). Piranhas standing on a
    // pruned tile in CHASE/FRIGHTENED follow this instead of the greedy
    // target — necessary because the pen-exit cell itself is usually pruned
    // (1-wide above the door), so the moment a piranha transitions from
    // LEAVING_PEN to CHASE it's standing on a pruned tile with no live
    // neighbor to greedy-step toward, and without an escape direction would
    // bounce straight back into the pen via the safety-net mode flip.
    private val chaseEscapeDir: Map<Pair<Int, Int>, Direction>

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
        gatewayBounds = if (gw.isNotEmpty()) Rect(
            gw.minOf { it.first }, gw.minOf { it.second },
            gw.maxOf { it.first }, gw.maxOf { it.second },
        ) else null
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

        liveChaseTiles = computeLiveChaseTiles()
        chaseEscapeDir = computeChaseEscapeDirections(liveChaseTiles)
    }

    /** BFS outward from the live skeleton into pruned territory, recording
     *  for each pruned tile the one-step direction toward its nearest live
     *  neighbor. Used by `Piranha.pickDirection` to force escape behavior
     *  when a CHASE/FRIGHTENED piranha is standing on a pruned tile (e.g.
     *  immediately after `LEAVING_PEN` deposits it on the pen-exit cell). */
    private fun computeChaseEscapeDirections(
        live: Set<Pair<Int, Int>>,
    ): Map<Pair<Int, Int>, Direction> {
        val flow = HashMap<Pair<Int, Int>, Direction>()
        val visited = HashSet<Pair<Int, Int>>(live)
        val queue = ArrayDeque<Pair<Int, Int>>().apply { addAll(live) }
        val dirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (dir in dirs) {
                val nb = cell.first + dir.dx to cell.second + dir.dy
                if (nb in visited) continue
                if (nb.first !in 0 until cols || nb.second !in 0 until rows) continue
                // Only chase-candidate tiles can be escape sources — pen and
                // walls are never reachable by a CHASE piranha anyway.
                if (!isChaseCandidate(nb.first, nb.second)) continue
                // Step from nb toward cell goes in direction `dir.opposite()`.
                flow[nb] = dir.opposite()
                visited.add(nb)
                queue.addLast(nb)
            }
        }
        return flow
    }

    /** Returns the precomputed escape direction for a pruned tile, or null
     *  if (col, row) is on the live skeleton (or off-grid / pen / wall). */
    fun chaseEscapeAt(col: Int, row: Int): Direction? = chaseEscapeDir[col to row]

    /** Build the set of tiles that CHASE/FRIGHTENED piranhas can step into.
     *  Starts from every chase-walkable tile (excludes walls, pen, portal),
     *  then iteratively prunes any tile with <2 surviving neighbors so
     *  dead-end pockets fall out. The result is the maze's "live skeleton";
     *  every tile in it has at least two ways out, so a piranha that lands
     *  on it can always continue without backtracking into a corner. */
    private fun computeLiveChaseTiles(): Set<Pair<Int, Int>> {
        val candidates = HashSet<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (isChaseCandidate(c, r)) candidates.add(c to r)
            }
        }
        val dirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)
        val degree = HashMap<Pair<Int, Int>, Int>(candidates.size)
        for (cell in candidates) {
            var d = 0
            for (dir in dirs) {
                if ((cell.first + dir.dx to cell.second + dir.dy) in candidates) d++
            }
            degree[cell] = d
        }
        val queue = ArrayDeque<Pair<Int, Int>>()
        for ((cell, d) in degree) {
            if (d < 2) queue.add(cell)
        }
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell !in candidates) continue
            candidates.remove(cell)
            for (dir in dirs) {
                val n = cell.first + dir.dx to cell.second + dir.dy
                if (n in candidates) {
                    val nd = (degree[n] ?: 0) - 1
                    degree[n] = nd
                    if (nd < 2) queue.add(n)
                }
            }
        }
        return candidates
    }

    /** Initial candidacy for the chase-live set: every tile a CHASE/FRIGHTENED
     *  piranha could in principle occupy. Excludes walls, the pen (re-entry
     *  forbidden), and the locked portal. Tide is assumed HIGH (cells gated
     *  only by tide are kept). */
    private fun isChaseCandidate(col: Int, row: Int): Boolean = when (tiles[row][col]) {
        Tile.PATH, Tile.PELLET, Tile.POWER_PELLET, Tile.MONKEY_SPAWN,
        Tile.TUNNEL, Tile.CURRENT_UP, Tile.CURRENT_DOWN,
        Tile.CURRENT_LEFT, Tile.CURRENT_RIGHT,
        Tile.TIDE, Tile.LILY_PAD, Tile.CROCODILE_SPAWN -> true
        else -> false
    }

    /** True if a CHASE/FRIGHTENED piranha may step into (col, row). Tunnel
     *  wrap is allowed (off-grid on a tunnel column wraps to a live tile by
     *  construction since every level's tunnel mouth is a 3-cell corridor). */
    fun isLiveChaseTile(col: Int, row: Int): Boolean {
        if (col in tunnelCols && (row < 0 || row >= rows)) return true
        return (col to row) in liveChaseTiles
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
    // --- Gateway paints (used by drawGateway / drawDoorSlab / drawPortalVortex) ---
    private val gateFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A2310") // matches wallPaint so the frame reads as carved bank
        style = Paint.Style.FILL
    }
    private val gateSlabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5C432A") // weathered bronze plank
        style = Paint.Style.FILL
    }
    private val gateSlabHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A5C3C")
        style = Paint.Style.FILL
    }
    private val gateSlabShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A2510")
        style = Paint.Style.FILL
    }
    private val gateSlabSeam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E1C0A")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val gateRivetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D8B070")
        style = Paint.Style.FILL
    }
    private val portalGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val portalSpillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val portalSpokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 220, 255, 240)
        style = Paint.Style.FILL
    }
    private val portalRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0EAFFF0")
        style = Paint.Style.STROKE
    }
    private val portalSparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val portalSparkles: Array<FloatArray> = run {
        val rng = Random(0xBADD00DL)
        // Each entry: [unit-x in -1..1, unit-y in -1..1, phase offset]
        Array(14) {
            floatArrayOf(
                rng.nextFloat() * 2f - 1f,
                rng.nextFloat() * 2f - 1f,
                rng.nextFloat() * (Math.PI * 2).toFloat(),
            )
        }
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
                        val pulse = 0.47f + 0.04f * sin(animTime * 4f)
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
                        // Drawn as one unified gate after the tile loop so the
                        // door slabs + portal vortex span the entire portal
                        // strip instead of rendering per-cell. See drawGateway.
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

        // Unified gateway render: stone frame, sliding door slabs, and the
        // swirling portal vortex that fades in as the door opens. Captures the
        // unlock time on first observation so the slide animation plays
        // exactly once per level.
        drawGateway(canvas, cellSize, originX, originY, animTime)
    }

    // animTime at which `gatewayUnlocked` first became true this level. Used
    // to drive the door-opening slide. NaN until the gate unlocks. Resets
    // naturally per level because GameState constructs a fresh Maze.
    private var unlockAnimStart: Float = Float.NaN

    private fun drawGateway(
        canvas: Canvas, cellSize: Float, originX: Float, originY: Float, animTime: Float,
    ) {
        val b = gatewayBounds ?: return
        if (gatewayUnlocked && unlockAnimStart.isNaN()) unlockAnimStart = animTime

        val left = originX + b.left * cellSize
        val top = originY + b.top * cellSize
        val right = originX + (b.right + 1) * cellSize
        val bottom = originY + (b.bottom + 1) * cellSize
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f

        // Eased open progress 0..1. ease-out so the doors snap apart quickly
        // then settle.
        val raw = if (unlockAnimStart.isNaN()) 0f
            else ((animTime - unlockAnimStart) / GATE_OPEN_DURATION).coerceIn(0f, 1f)
        val open = 1f - (1f - raw) * (1f - raw)

        // Glow that spills inward from the portal into the maze interior.
        // Draw FIRST so door slabs cover it when locked. Direction depends on
        // which wall the portal sits on.
        if (open > 0f) {
            drawPortalGlowSpill(canvas, b, cellSize, originX, originY, animTime, open)
        }

        // Stone frame around the opening (top + bottom edges of the strip).
        val frameThickness = (cellSize * 0.12f).coerceAtLeast(2f)
        canvas.drawRect(left, top, right, top + frameThickness, gateFramePaint)
        canvas.drawRect(left, bottom - frameThickness, right, bottom, gateFramePaint)

        // Portal vortex behind the doors (alpha scales with open progress so a
        // sealed door has no light leaking through).
        canvas.save()
        canvas.clipRect(left, top + frameThickness, right, bottom - frameThickness)
        if (open > 0f) {
            drawPortalVortex(canvas, left, top + frameThickness, right, bottom - frameThickness,
                cx, cy, cellSize, animTime, open)
        }

        // Door slabs: meet at vertical center when locked; slide outward past
        // the frame as `open` ramps to 1. The clipRect above hides the parts
        // that slide beyond the frame.
        if (open < 1f) {
            val innerTop = top + frameThickness
            val innerBot = bottom - frameThickness
            val innerHalf = (innerBot - innerTop) / 2f
            val slideOffset = open * (innerHalf + frameThickness)
            val slabInset = frameThickness * 0.4f

            // Top slab (originally [innerTop, cy]) slides UP
            val topSlabTop = innerTop - slideOffset
            val topSlabBot = cy - slideOffset
            drawDoorSlab(canvas, left + slabInset, topSlabTop, right - slabInset, topSlabBot,
                cellSize, topHalf = true)

            // Bottom slab (originally [cy, innerBot]) slides DOWN
            val botSlabTop = cy + slideOffset
            val botSlabBot = innerBot + slideOffset
            drawDoorSlab(canvas, left + slabInset, botSlabTop, right - slabInset, botSlabBot,
                cellSize, topHalf = false)
        }
        canvas.restore()
    }

    private fun drawDoorSlab(
        canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float,
        cellSize: Float, topHalf: Boolean,
    ) {
        if (bottom <= top) return
        // Body
        tmpRect.set(left, top, right, bottom)
        canvas.drawRect(tmpRect, gateSlabPaint)
        // Top-of-slab bevel highlight
        tmpRect.set(left, top, right, top + (bottom - top) * 0.18f)
        canvas.drawRect(tmpRect, gateSlabHighlight)
        // Bottom-of-slab shadow
        tmpRect.set(left, bottom - (bottom - top) * 0.12f, right, bottom)
        canvas.drawRect(tmpRect, gateSlabShadow)
        // Horizontal banding (plank lines) for stone-block feel
        val bandSpacing = cellSize * 0.45f
        var y = top + bandSpacing
        while (y < bottom - 2f) {
            canvas.drawLine(left + cellSize * 0.10f, y, right - cellSize * 0.10f, y, gateSlabSeam)
            y += bandSpacing
        }
        // Rivets at the inner edge (where the two slabs meet) — large dots.
        val rivetRow = if (topHalf) bottom - cellSize * 0.18f else top + cellSize * 0.18f
        val rivetR = cellSize * 0.07f
        canvas.drawCircle(left + cellSize * 0.22f, rivetRow, rivetR, gateRivetPaint)
        canvas.drawCircle(right - cellSize * 0.22f, rivetRow, rivetR, gateRivetPaint)
        canvas.drawCircle((left + right) / 2f, rivetRow, rivetR, gateRivetPaint)
    }

    private fun drawPortalVortex(
        canvas: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        cx: Float, cy: Float, cellSize: Float, animTime: Float, alpha: Float,
    ) {
        val width = right - left
        val height = bottom - top
        val maxRadius = sqrt(width * width + height * height) * 0.55f

        val a255 = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        val layer = canvas.saveLayerAlpha(left, top, right, bottom, a255)

        // Radial glow base
        val pulse = 0.88f + 0.12f * sin(animTime * 4f)
        portalGlowPaint.shader = RadialGradient(
            cx, cy,
            maxRadius * pulse,
            intArrayOf(
                Color.parseColor("#FFFFFFFF"),
                Color.parseColor("#CCB4FFE0"),
                Color.parseColor("#7022FFAA"),
                Color.parseColor("#00041A20"),
            ),
            floatArrayOf(0f, 0.18f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(left, top, right, bottom, portalGlowPaint)

        // Rotating spokes — bright streaks of light radiating outward, swept
        // by animTime so the portal "spins". Each spoke is a tall, thin
        // rectangle drawn after rotation.
        val spokes = 6
        val spinDeg = animTime * 70f
        for (i in 0 until spokes) {
            val angle = spinDeg + i * (360f / spokes)
            canvas.save()
            canvas.rotate(angle, cx, cy)
            val len = maxRadius * 1.1f
            val w = cellSize * 0.08f
            canvas.drawRect(cx - w, cy - len, cx + w, cy + len, portalSpokePaint)
            canvas.restore()
        }

        // Concentric rings — pulse outward
        val ringPhase = (animTime * 0.6f) - (animTime * 0.6f).toInt()
        for (i in 0 until 3) {
            val rp = (ringPhase + i / 3f) % 1f
            val ringRadius = maxRadius * rp
            val ringAlpha = (255 * (1f - rp)).toInt().coerceIn(0, 255)
            portalRingPaint.alpha = ringAlpha
            portalRingPaint.strokeWidth = cellSize * (0.08f - 0.06f * rp).coerceAtLeast(1.5f)
            canvas.drawCircle(cx, cy, ringRadius, portalRingPaint)
        }

        // Sparkles
        for (s in portalSparkles) {
            val sx = cx + s[0] * width * 0.42f
            val sy = cy + s[1] * height * 0.42f
            val sp = (0.5f + 0.5f * sin(animTime * 3f + s[2])).coerceIn(0f, 1f)
            portalSparklePaint.alpha = (255 * sp).toInt()
            canvas.drawCircle(sx, sy, cellSize * 0.05f * (0.5f + sp), portalSparklePaint)
        }

        canvas.restoreToCount(layer)
    }

    /** Soft halo of light that spills out of the open gate into the adjacent
     *  maze interior, so the player sees the glow before reaching the portal. */
    private fun drawPortalGlowSpill(
        canvas: Canvas, b: Rect, cellSize: Float, originX: Float, originY: Float,
        animTime: Float, open: Float,
    ) {
        // Detect which wall the portal sits on. Spill extends ~1.5 cells into
        // the playable area; offscreen sides contribute nothing.
        val onLeftWall = b.left == 0
        val onRightWall = b.right == cols - 1
        val onTopWall = b.top == 0
        val onBottomWall = b.bottom == rows - 1

        val pad = cellSize * 1.5f
        val leftSpill = if (onRightWall) pad else 0f
        val rightSpill = if (onLeftWall) pad else 0f
        val topSpill = if (onBottomWall) pad else 0f
        val bottomSpill = if (onTopWall) pad else 0f
        val left = originX + b.left * cellSize - leftSpill
        val top = originY + b.top * cellSize - topSpill
        val right = originX + (b.right + 1) * cellSize + rightSpill
        val bottom = originY + (b.bottom + 1) * cellSize + bottomSpill
        val cx = (originX + b.left * cellSize + originX + (b.right + 1) * cellSize) / 2f
        val cy = (originY + b.top * cellSize + originY + (b.bottom + 1) * cellSize) / 2f

        val pulse = 0.85f + 0.15f * sin(animTime * 4f)
        val radius = sqrt(((right - left) * (right - left) + (bottom - top) * (bottom - top))) * 0.6f * pulse
        portalSpillPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(
                Color.argb((140 * open).toInt(), 180, 255, 220),
                Color.argb((60 * open).toInt(), 80, 220, 200),
                Color.argb(0, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(left, top, right, bottom, portalSpillPaint)
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

    companion object {
        // Seconds for the door slabs to slide fully open after the last pellet
        // is eaten. Short enough to feel snappy; long enough to read as motion.
        private const val GATE_OPEN_DURATION: Float = 0.55f
    }
}
