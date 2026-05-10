package com.monkeyswim.game

/**
 * Level catalog. Each level is a 22×15 layout that *must* contain:
 *   • exactly one 'M' (monkey spawn)
 *   • one '-' (pen door) and a contiguous '===' pen interior block
 *   • 'T' tunnel-mouth cells
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
 *   = = piranha pen interior (also acts as a piranha spawn cell)
 *   T = tunnel mouth (wraps to opposite side along the same column)
 *   X = level portal (locked until last pellet eaten)
 *
 * All 10 levels share the same pen, top/bottom tunnels, right-wall portal,
 * and monkey spawn position — these are the structural elements the piranha
 * AI and pen-exit logic depend on. What varies per level: the corridor and
 * wall patterns in rows 1-7 (top half) and 12-16 (mid-low band), plus the
 * positions of the 4 power pellets. Difficulty also ramps via piranha speed
 * (Levels.piranhaSpeedScale).
 */
object Levels {

    // Level 1 — the hand-laid baseline: 3-block top/bottom row, corner power
    // pellets, central narrow gap above the pen exit.
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

    // Level 2 — Inner power pellets: PPs pulled off the corners onto row 5 /
    // row 14, sitting one tile inside the upper-row pillar columns so the
    // player has to detour through the mid-band corridor to grab them.
    private val LEVEL_2: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W..o..W.W..o..W",
        "W.WWW.....WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.............X",
        "W.WWWW.W.WWWW.W",
        "W..o..WWW..o..W",
        "W.WWW.WWW.WWW.W",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 3 — Picket fence: row 2 / row 17 swap the three-block pattern for a
    // continuous picket of 1-cell walls, opening up more cross-traffic.
    private val LEVEL_3: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.W.W.W.W.W.W.W",
        "WoW.........WoW",
        "W.WWW.WWW.WWW.W",
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
        "W.W.W.W.W.W.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 4 — Tall pillars: the four short wall fingers in row 4/17 are
    // extended down into row 5/16, becoming taller pillars that funnel the
    // monkey through narrower vertical lanes.
    private val LEVEL_4: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.....WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.............X",
        "W.WWWW.W.WWWW.W",
        "W.W...WWW...W.W",
        "W.W.WWW.WWW.W.W",
        "WoW.........WoW",
        "W.WWW.WWW.WWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 5 — Picket-on-picket: row 4 / row 15 use the modified pattern that
    // adds wall pillars at cols 2 & 12, and row 5 / row 14 are full pickets
    // (alternating 1-cell walls every other column). Lots of vertical 1-tile
    // pickets gives the upper / lower halves a denser, more structured feel.
    private val LEVEL_5: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.W.WWW.W.W.W",
        "W.W.W.W.W.W.W.W",
        "W.WWW.....WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.............X",
        "W.WWWW.W.WWWW.W",
        "W.W.W.WWW.W.W.W",
        "W.W.W.WWW.W.W.W",
        "WoW.........WoW",
        "W.WWW.WWW.WWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 6 — Bridge ceiling: a long horizontal wall in row 6 / row 13 splits
    // the upper / lower halves into clear "rooms" that the player can only cross
    // at the edges (cols 1, 5, 9, 13).
    private val LEVEL_6: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W.........W.W",
        "W.WWWWW.WWWWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.............X",
        "W.WWWWW.WWWWW.W",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "WoW.........WoW",
        "W.WWW.WWW.WWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 7 — Twin chevrons: row 5 / row 14 carry the chevron pattern
    // (4-wall-pillar baffle). The picket row 15 below the lower chevron exists
    // so the chevron's odd-column path cells can connect downward — without it,
    // (5, 14) and (9, 14) would be 1-tile pellets isolated by walls on every
    // side.
    private val LEVEL_7: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W...W.W.W.W...W",
        "W.WWW.....WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.............X",
        "W.WWWW.W.WWWW.W",
        "W...W.W.W.W...W",
        "W.W.W.W.W.W.W.W",
        "WoW.........WoW",
        "W.WWW.WWW.WWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 8 — Stout pillars: the four short finger walls in row 4 / row 17
    // are replaced with a centered three-pillar pattern (cols 3-4, 7, 10-11),
    // shifting the maze's vertical-corridor grid.
    private val LEVEL_8: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.WWW.W.W.WWW.W",
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
        "W.WWW.W.W.WWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 9 — Stepped pillars: short L-shaped wall steps at rows 4-5 / 14-15
    // give the upper + lower halves a "stepped pyramid" feel.
    private val LEVEL_9: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.WWW.W.W.WWW.W",
        "W...W.....W...W",
        "W.W.WWW.WWW.W.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.............X",
        "W.WWWW.W.WWWW.W",
        "W...W.....W...W",
        "W.WWW.W.W.WWW.W",
        "WoW.........WoW",
        "W.WWW.WWW.WWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 10 — Densest: combines Level 6's bridge ceiling with Level 4's tall
    // pillars and Level 3's picket-fence rows for the most claustrophobic of
    // the ten layouts. Power pellets stay in the corners so they're worth a
    // detour through the tighter corridors.
    private val LEVEL_10: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.W.W.W.W.W.W.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWWWW.WWWWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.............X",
        "W.WWWWW.WWWWW.W",
        "W.W...W.W...W.W",
        "W.W.WWW.WWW.W.W",
        "WoW.........WoW",
        "W.W.W.W.W.W.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
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
