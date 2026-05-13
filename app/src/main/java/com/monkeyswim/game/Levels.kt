package com.monkeyswim.game

/**
 * Level catalog. Each level is a 22×15 layout that *must* contain:
 *   • exactly one 'M' (monkey spawn)
 *   • one '-' (pen door) and a contiguous '===' pen interior block
 *   • 'T' tunnel-mouth cells (top + bottom rows; cols MUST match top↔bottom
 *     since `tunnelWrap` in Maze.kt teleports by column without verifying
 *     the destination is also a T)
 *   • 'X' level-portal cells
 *   • four 'o' power pellets
 *   • only 1-tile-wide corridors (no 2x2 path/pellet areas)
 *
 * Tile chars (see Tile.fromChar):
 *   W = wall (canal bank)
 *   . = pellet (banana coin)
 *   o = power pellet (golden banana)
 *   (space) = empty water (no pellet)
 *   M = monkey spawn (PATH; no pellet)
 *   - = piranha pen door
 *   = = piranha pen interior (also defines piranha spawn cells via bounding-box corners)
 *   T = tunnel mouth (wraps to opposite side along the same column)
 *   X = level portal (locked until last pellet eaten)
 *
 * Level 1 is the hand-laid baseline (pen + portal + tunnels all centered).
 * Levels 2-10 each carry their own pen position, portal wall, and tunnel
 * column set so the gameplay feel changes per level — piranhas emerge from
 * a different direction, the exit is in a different place, and the wrap
 * tunnels are not always centered. The monkey spawn stays at (col 7, row 18)
 * across all levels so the player always starts in the same orientation;
 * what varies is how far the pen is from the spawn and which way the chase
 * develops. Piranha AI + pen-exit logic are fully driven from the layout
 * chars (`maze.penExitTile`, `maze.piranhaSpawnTiles`), so moving the pen
 * needs no AI changes. Difficulty also ramps via piranha speed
 * (Levels.piranhaSpeedScale).
 */
object Levels {

    // Level 1 — the hand-laid baseline: pen center, portal on right wall,
    // tunnels centered, corner power pellets.
    private val LEVEL_1: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.....W.W.....W",
        "W.WWW.....WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.............X",
        "W.WWWW.W.WWWW.W",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 2 — Pen TOP-CENTER (rows 4-7), portal LEFT WALL, tunnels SPLIT
    // (cols 1-3 + 11-13). Piranhas now spawn near the top and have to chase
    // the monkey downward; the exit is on the opposite wall from L1.
    private val LEVEL_2: List<String> = listOf(
        "WTTTWWWWWWWTTTW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.WWWWW.WW.W",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "XoW.........WoW",
        "X.W.WWWWWWW.W.W",
        "X.............W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WTTTWWWWWWWTTTW",
    )

    // Level 3 — Pen BOTTOM-CENTER (rows 13-16), portal RIGHT WALL, tunnels
    // WIDE-CENTER (cols 5-9). With the pen just two rows above the monkey
    // spawn, early-game pressure is high — piranhas reach the player fast.
    private val LEVEL_3: List<String> = listOf(
        "WWWWWTTTTTWWWWW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "WoW.........WoW",
        "W.WWW.W.W.WWW.W",
        "W.....W.W.....W",
        "W.WWW.W.W.WWW.W",
        "W.............W",
        "W.WWW.W W.WWW.X",
        "WoW.WWW WWW.WoX",
        "W.....W W.....X",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.WWWWW.WW.W",
        "W.WWW.WWW.WWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWTTTTTWWWWW",
    )

    // Level 4 — Pen LEFT-CENTER (rows 8-11, cols 1-5), portal RIGHT WALL,
    // tunnels RIGHT-ONLY (cols 10-12). Pen abuts the left bank, so piranhas
    // emerge facing right and the chase plays out horizontally.
    private val LEVEL_4: List<String> = listOf(
        "WWWWWWWWWWTTTWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.W.W.WWW.WWW.W",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WX",
        "WWWWWW.WW.WW.WX",
        "W.............X",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWWWTTTWW",
    )

    // Level 5 — Pen RIGHT-CENTER (rows 8-11, cols 9-13), portal LEFT WALL,
    // tunnels LEFT-ONLY (cols 2-4). Mirror of L4.
    private val LEVEL_5: List<String> = listOf(
        "WWTTTWWWWWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.WWW.W.W.W",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "XW.WW.WW.W===WW",
        "XW.WW.WW.WWWWWW",
        "X.............W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWTTTWWWWWWWWWW",
    )

    // Level 6 — Pen TOP-LEFT (rows 4-7, cols 1-5), portal RIGHT WALL, tunnels
    // RIGHT-ONLY (cols 10-12). Pen and portal are on opposite corners; long
    // diagonal chase to the exit.
    private val LEVEL_6: List<String> = listOf(
        "WWWWWWWWWWTTTWW",
        "W.............W",
        "W.W W.WWW.WWW.W",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WWWWWW.WW.WW.WW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "WoW.........WoX",
        "W.W.WWWWWWW.W.X",
        "W.............X",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWWWTTTWW",
    )

    // Level 7 — Pen TOP-RIGHT (rows 4-7, cols 9-13), portal LEFT WALL,
    // tunnels LEFT-ONLY (cols 2-4). Mirror of L6.
    private val LEVEL_7: List<String> = listOf(
        "WWTTTWWWWWWWWWW",
        "W.............W",
        "W.WWW.WWW.W W.W",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "WW.WW.WW.W===WW",
        "WW.WW.WW.WWWWWW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "XoW.........WoW",
        "X.W.WWWWWWW.W.W",
        "X.............W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWTTTWWWWWWWWWW",
    )

    // Level 8 — Pen BOTTOM-LEFT (rows 13-16, cols 1-5), portal RIGHT WALL,
    // tunnels VERY WIDE (cols 4-10). Pen and monkey share the lower half of
    // the maze, so the player is herded toward the upper area early.
    private val LEVEL_8: List<String> = listOf(
        "WWWWTTTTTTTWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.W.W.WWW.W",
        "W.....W.W.....W",
        "W.WWW.....WWW.W",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "WoW.........WoW",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WX",
        "WWWWWW.WW.WW.WX",
        "W.............X",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWTTTTTTTWWWW",
    )

    // Level 9 — Pen BOTTOM-RIGHT (rows 13-16, cols 9-13), portal LEFT WALL,
    // tunnels SPLIT (cols 1-3 + 11-13). Mirror of L8 with the portal on the
    // opposite wall. Rows 8, 11, 17 are deliberately broken up so adjacent
    // rows never both turn into wide horizontal corridors (no 2x2 path
    // blocks). Row 11 cols 11-12 are kept open so piranhas leaving the
    // vestibule at (11,12) can swim right past the PP at (13,11) and escape
    // upward — without the gap, every neighbor of (11,11) is a wall.
    private val LEVEL_9: List<String> = listOf(
        "WTTTWWWWWWWTTTW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.WWW.WWW.W",
        "W.....W.W.....W",
        "W.WWW.W.W.WWW.W",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "WoW.WWW.WWW..oW",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "XW.WW.WW.W===WW",
        "XW.WW.WW.WWWWWW",
        "X..WW.WW.WWWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WTTTWWWWWWWTTTW",
    )

    // Level 10 — Densest combo: pen back to CENTER, but portals on BOTH walls
    // (left + right, rows 10-12) and THREE tunnel groups (cols 2-4 + 6-8 +
    // 11-13). Tighter corridor patterns top and bottom. Maximum chaos.
    private val LEVEL_10: List<String> = listOf(
        "WWTTTWTTTWWTTTW",
        "W.............W",
        "W.W.W.W.W.W.W.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWWWW.WWWWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "X.WW.W===W.WW.X",
        "X.WW.WWWWW.WW.X",
        "X.............X",
        "W.WWWWW.WWWWW.W",
        "W.W...W.W...W.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.W.W.W.W.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWTTTWTTTWWTTTW",
    )

    private val LEVELS: List<List<String>> = listOf(
        LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4, LEVEL_5,
        LEVEL_6, LEVEL_7, LEVEL_8, LEVEL_9, LEVEL_10,
    )

    val LEVEL_COUNT: Int get() = LEVELS.size

    init {
        LEVELS.forEachIndexed { i, layout ->
            require(layout.size == 22) { "Level ${i + 1} must be 22 rows; got ${layout.size}" }
            val badRows = layout.withIndex().filter { it.value.length != 15 }
            require(badRows.isEmpty()) {
                "Level ${i + 1} rows must be 15 chars wide; offending: ${badRows.joinToString { "${it.index}=${it.value.length}" }}"
            }
            val mCount = layout.sumOf { row -> row.count { it == 'M' } }
            require(mCount == 1) {
                "Level ${i + 1} must have exactly one 'M' (monkey spawn); got $mCount"
            }
        }
    }

    fun layoutForLevel(level: Int): List<String> {
        // Levels beyond LEVEL_COUNT cycle back to Level 1 so the game never
        // runs out of layouts — difficulty keeps climbing via piranha speed.
        val idx = ((level - 1) % LEVELS.size + LEVELS.size) % LEVELS.size
        return LEVELS[idx]
    }

    fun piranhaSpeedScale(level: Int): Float {
        // Level 1 baseline 1.0; each level adds 8% piranha speed up to a 1.6x cap.
        return (1.0f + 0.08f * (level - 1)).coerceAtMost(1.6f)
    }
}
