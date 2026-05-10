package com.monkeyswim.game

object Levels {
    // 15 columns x 22 rows. Taller than wide to better fill phone screens —
    // cellSize is bound by viewWidth/cols on most devices, so adding rows
    // converts otherwise-wasted vertical space into more maze without shrinking
    // sprites. Pen sits roughly mid-maze with a single 1-tile gate at col 7.
    // The bottom area runs corridor-separator-spawnband so the two horizontal
    // passages above the gateway are visually distinct rather than reading as
    // one 2-tile-wide block.
    //
    // W = wall (canal bank)
    // . = pellet (banana coin)
    // o = power pellet (golden banana)
    //   = empty water (no pellet)
    // - = piranha pen door
    // = = piranha pen interior
    // T = tunnel mouth (wraps to opposite side)
    // X = bottom gateway (locked until last pellet eaten)
    private val LEVEL_1: List<String> = listOf(
        "WWWWWWTTTWWWWWW", //  0  ← top border with 3-tile-wide vertical-tunnel mouth (cols 6-8)
        "W.............W", //  1  ← top outer corridor
        "W.WWW.WWW.WWW.W", //  2  ← three wall blocks separated by clear T-junctions
        "WoW.........WoW", //  3  ← top-corner power pellets
        "W.W.WWW.WWW.W.W", //  4
        "W.....W.W.....W", //  5
        "W.WWW.....WWW.W", //  6  ← (7,6) corridor must stay open for pen exit
        "W.....W W.....W", //  7  ← (6,7)/(8,7) walls anchor gate; (7,7) PATH avoids dead-end pellet
        "W..WWWW-WWWW..W", //  8  ← pen door (single tile at col 7); spike & bump removed
        "W....W===W....W", //  9  ← pen interior; col 13 opened so (13,10) isn't a dead end
        "W....W===W....X", // 10  ← pen interior + level-portal cell on right wall
        "W..WWWWWWWWW.WX", // 11  ← pen base + level-portal cell (middle of 3-tile portal)
        "W.............X", // 12  ← below-pen corridor + level-portal cell on right wall
        "W.WWWW.W.WWWW.W", // 13  ← walls at col 5,7,9 break 2x2 with row 12
        "W.............W", // 14  ← lower outer corridor
        "W.WWW.WWW.WWW.W", // 15  ← mirror of row 2
        "WoW.........WoW", // 16  ← bottom-corner power pellets
        "W.W.WWW.WWW.W.W", // 17  ← mirror of row 4
        "W...... ......W", // 18  ← upper bottom passage; (7,18) PATH is monkey spawn
        "W.WWW.WWW.WWW.W", // 19  ← wall separator: 4 vertical connectors at cols 1,5,9,13
        "W.............W", // 20  ← lower bottom passage (full pellets — no longer a gateway approach)
        "WWWWWWTTTWWWWWW", // 21  ← bottom border with 3-tile-wide vertical-tunnel mouth (cols 6-8)
    )

    init {
        require(LEVEL_1.all { it.length == 15 }) {
            val bad = LEVEL_1.withIndex().filter { it.value.length != 15 }
                .joinToString { "${it.index}=${it.value.length}" }
            "All maze rows must be 15 chars wide; offending rows: $bad"
        }
    }

    fun layoutForLevel(level: Int): List<String> {
        // For now every level reuses Level 1's layout. Difficulty ramps via piranha speed.
        return LEVEL_1
    }

    fun piranhaSpeedScale(level: Int): Float {
        // Level 1 baseline 1.0; each level adds 8% piranha speed up to a 1.6x cap.
        return (1.0f + 0.08f * (level - 1)).coerceAtMost(1.6f)
    }

    // Spawn coords (col, row).
    val MONKEY_SPAWN: Pair<Int, Int> = 7 to 18
    val PIRANHA_SPAWNS: List<Pair<Int, Int>> = listOf(
        6 to 9,
        8 to 9,
        6 to 10,
        8 to 10,
    )
}
